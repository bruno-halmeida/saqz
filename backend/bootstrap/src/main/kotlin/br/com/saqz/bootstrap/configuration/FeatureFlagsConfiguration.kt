package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.input.http.FeaturesController
import br.com.saqz.access.adapter.output.jdbc.flags.JdbcFeatureFlagStore
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.flags.FeatureCatalog
import br.com.saqz.access.application.flags.FeatureChangeNotifier
import br.com.saqz.access.application.flags.FeatureFlagStore
import br.com.saqz.access.application.flags.FeatureFlags
import br.com.saqz.adminweb.http.AdminFeaturesController
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.Parameter
import com.google.firebase.remoteconfig.ParameterValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.time.Clock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.sql.DataSource

/** Flags (DEB-119): registro, avaliação, `GET /api/features`, adm e aviso ao vivo. Só existe com banco. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class FeatureFlagsConfiguration {
    @Bean
    fun featureCatalog(@Value("\${saqz.notifications.whatsapp.enabled:false}") whatsApp: Boolean) = FeatureCatalog(whatsApp)

    @Bean
    fun featureFlagStore(dataSource: DataSource): FeatureFlagStore = JdbcFeatureFlagStore(dataSource)

    @Bean(destroyMethod = "close")
    fun featureChangeNotifier(firebaseApp: FirebaseApp, environment: Environment): FeatureChangeNotifierBean {
        // Perfil local/test usa o emulador de Auth, que não tem Remote Config: sem credencial, só o log.
        val profiles = environment.activeProfiles.toSet()
        val emulator = profiles.isNotEmpty() && profiles.all { it == "local" || it == "test" }
        return FeatureChangeNotifierBean(if (emulator) null else RemoteConfigFeatureNotifier(firebaseApp))
    }

    /** Grava a regra inicial das flags novas do registro na subida (ex.: o WhatsApp a partir da variável). */
    @Bean
    fun featureFlags(catalog: FeatureCatalog, store: FeatureFlagStore, notifier: FeatureChangeNotifierBean, clock: ObjectProvider<Clock>) =
        FeatureFlags(catalog, store, notifier, clock.getIfAvailable { Clock.systemUTC() }).also { flags ->
            val created = flags.ensureCatalog()
            if (created.isNotEmpty()) log.info("feature_flags_created keys={}", created.joinToString(","))
        }

    @Bean
    fun featuresController(features: FeatureFlags) = FeaturesController(features)

    @Bean
    fun adminFeaturesController(features: FeatureFlags, admins: PlatformAdminLookup) = AdminFeaturesController(features, admins)

    private companion object {
        val log = LoggerFactory.getLogger(FeatureFlagsConfiguration::class.java)
    }
}

/**
 * Publica fora da requisição do adm, numa fila de uma thread só (os avisos saem na ordem das mudanças).
 * Falha ao publicar não desfaz a mudança da flag: o app também busca ao abrir e ao voltar.
 */
class FeatureChangeNotifierBean(private val remote: RemoteConfigFeatureNotifier?) : FeatureChangeNotifier, AutoCloseable {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "feature-remote-config").apply { isDaemon = true } }

    override fun published(signal: String) {
        if (remote == null) {
            log.info("feature_remote_config_skipped signal={} cause=no_credentials", signal)
            return
        }
        executor.execute {
            try {
                remote.publish(signal)
            } catch (failure: Exception) {
                log.warn("feature_remote_config_failed signal={} cause={}", signal, failure.javaClass.simpleName)
            }
        }
    }

    override fun close() = executor.shutdown()

    private companion object {
        val log = LoggerFactory.getLogger(FeatureChangeNotifierBean::class.java)
    }
}

/**
 * Troca o parâmetro `versao_das_flags` no Remote Config do projeto do ambiente (`saqz-dev`, `saquz-app`).
 * O Remote Config não decide nada: o app, ao ver o valor mudar, chama `GET /api/features` (FLG-003).
 */
class RemoteConfigFeatureNotifier(private val firebaseApp: FirebaseApp) {
    fun publish(signal: String) {
        val remoteConfig = FirebaseRemoteConfig.getInstance(firebaseApp)
        // O template tem ETag: se outra publicação passou na frente, lê de novo e tenta mais uma vez.
        repeat(2) { attempt ->
            val template = remoteConfig.template
            val value = "${System.currentTimeMillis()}-$signal"
            template.setParameters(template.parameters + (PARAMETER to Parameter()
                .setDefaultValue(ParameterValue.of(value))
                .setDescription("Muda a cada publicação de flags no adm; o app busca /api/features quando muda.")))
            try {
                remoteConfig.publishTemplate(template)
                log.info("feature_remote_config_published signal={} value={}", signal, value)
                return
            } catch (failure: FirebaseRemoteConfigException) {
                if (attempt == 1) throw failure
            }
        }
    }

    companion object {
        const val PARAMETER = "versao_das_flags"
        private val log = LoggerFactory.getLogger(RemoteConfigFeatureNotifier::class.java)
    }
}
