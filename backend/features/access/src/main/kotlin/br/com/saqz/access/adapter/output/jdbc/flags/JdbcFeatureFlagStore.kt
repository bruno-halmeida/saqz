package br.com.saqz.access.adapter.output.jdbc.flags

import br.com.saqz.access.application.flags.*
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/** JSON das regras: o mesmo formato no banco, no histórico e no corpo do adm. */
object FeatureJson {
    private val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun <T> read(json: String, type: Class<T>): T = mapper.readValue(json, type)

    fun encode(value: Any?): String = mapper.writeValueAsString(value)

    /** Números inteiros dos parâmetros voltam como [Long], como o [FeatureRules] espera. */
    fun rule(json: String): FeatureRule = normalize(mapper.readValue(json, FeatureRule::class.java))
    fun rule(tree: Any): FeatureRule = normalize(mapper.convertValue(tree, FeatureRule::class.java))
    fun tree(json: String): Any = mapper.readValue(json, Any::class.java)

    fun normalize(rule: FeatureRule) = rule.copy(
        values = rule.values.mapValues { normalize(it.value) },
        variants = rule.variants.map { variant -> variant.copy(values = variant.values.mapValues { normalize(it.value) }) },
    )

    private fun normalize(value: Any): Any = when (value) {
        is Int -> value.toLong()
        is Short -> value.toLong()
        is java.math.BigInteger -> value.toLong()
        else -> value
    }
}

/**
 * Toda escrita (flag, grupo ou versões) pega a mesma trava de transação antes de conferir a versão
 * esperada: a validação das listas cruzadas (flag × grupo) não corre contra outra escrita, e o histórico
 * sai na mesma transação da mudança.
 */
class JdbcFeatureFlagStore(dataSource: DataSource) : FeatureFlagStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val tx = TransactionTemplate(DataSourceTransactionManager(dataSource))

    override fun ensure(definitions: List<FeatureFlagDefinition>, now: Instant): List<String> = tx.execute {
        lock()
        definitions.filter { definition ->
            val created = jdbc.sql("""
                INSERT INTO feature_flags(flag_key, version, rule, full_rollout_since, updated_at)
                VALUES (:key, 1, CAST(:rule AS jsonb), :since, :at) ON CONFLICT (flag_key) DO NOTHING
            """).param("key", definition.key).param("rule", FeatureJson.encode(definition.initialRule))
                .param("since", if (definition.initialRule.fullRollout()) Timestamp.from(now) else null, Types.TIMESTAMP)
                .param("at", Timestamp.from(now)).update() == 1
            if (created) history(UUID.randomUUID(), FeatureHistoryType.FLAG, definition.key, 1, null, definition.initialReason,
                now, null, definition.initialRule)
            created
        }.map { it.key }
    }!!

    override fun flags(): Map<String, StoredFeatureFlag> = jdbc.sql("SELECT * FROM feature_flags").query { rs, _ -> flag(rs) }
        .list().associateBy { it.key }

    override fun userIdBySubject(subject: String): UUID? =
        jdbc.sql("SELECT id FROM access_users WHERE firebase_subject = :subject AND deleted_at IS NULL")
            .param("subject", subject).query(UUID::class.java).optional().orElse(null)

    override fun profile(userId: UUID): AudienceProfile? = jdbc.sql("""
        SELECT p.plan::text AS plan, p.source,
            (SELECT CASE WHEN s.canceled_at IS NOT NULL OR s.status = 'CANCELED' THEN 'CANCELED'
                         WHEN s.status = 'PAST_DUE' THEN 'PAST_DUE' ELSE 'ACTIVE' END
               FROM subscriptions s
              WHERE s.owner_user_id = u.id AND (s.status = 'ACTIVE' OR s.first_confirmed_at IS NOT NULL)) AS site_status,
            (SELECT CASE WHEN a.in_billing_retry OR (a.expires_at <= now() AND a.grace_period_expires_at > now()) THEN 'PAST_DUE'
                         WHEN a.expires_at <= now() OR a.auto_renew = false THEN 'CANCELED' ELSE 'ACTIVE' END
               FROM app_store_subscriptions a
              WHERE a.owner_user_id = u.id AND a.revoked_at IS NULL
              ORDER BY a.expires_at DESC LIMIT 1) AS apple_status,
            (SELECT CASE WHEN g.state IN ('IN_GRACE_PERIOD', 'ON_HOLD') THEN 'PAST_DUE'
                         WHEN g.state = 'ACTIVE' AND g.expires_at > now() THEN 'ACTIVE' ELSE 'CANCELED' END
               FROM google_play_subscriptions g
              WHERE g.owner_user_id = u.id AND g.superseded_at IS NULL
              ORDER BY g.expires_at DESC LIMIT 1) AS google_status,
            CASE WHEN EXISTS (SELECT 1 FROM access_groups g WHERE g.owner_user_id = u.id AND g.deleted_at IS NULL) THEN 'OWNER'
                 WHEN EXISTS (SELECT 1 FROM group_memberships m JOIN access_groups g ON g.id = m.group_id AND g.deleted_at IS NULL
                               WHERE m.user_id = u.id AND m.role = 'ADMIN') THEN 'ADMIN'
                 WHEN EXISTS (SELECT 1 FROM group_memberships m JOIN access_groups g ON g.id = m.group_id AND g.deleted_at IS NULL
                               WHERE m.user_id = u.id) THEN 'ATHLETE'
                 ELSE 'NONE' END AS role,
            ARRAY(SELECT group_name FROM feature_user_group_members WHERE user_id = u.id) AS feature_groups
        FROM access_users u
        LEFT JOIN owner_effective_plans p ON p.owner_user_id = u.id
        WHERE u.id = :id AND u.deleted_at IS NULL
    """).param("id", userId).query { rs, _ ->
        val source = rs.getString("source")
        val plan = when {
            source == null -> AudiencePlan.NONE
            source == "TRIAL" -> AudiencePlan.TRIAL
            else -> AudiencePlan.valueOf(rs.getString("plan"))
        }
        // A situação é a da fonte que dá o plano; sem plano, a da última assinatura que existiu.
        val site = rs.getString("site_status")
        val apple = rs.getString("apple_status")
        val google = rs.getString("google_status")
        val status = when (source) {
            "SITE" -> site
            "APP_STORE" -> apple
            "GOOGLE_PLAY" -> google
            "COURTESY", "TRIAL" -> null
            else -> site ?: apple ?: google
        }
        @Suppress("UNCHECKED_CAST")
        val groups = (rs.getArray("feature_groups").array as Array<String>).toSet()
        AudienceProfile(userId, plan, status?.let(AudienceSubscriptionStatus::valueOf), AudienceRole.valueOf(rs.getString("role")), groups)
    }.optional().orElse(null)

    override fun usersExist(ids: Collection<UUID>): Boolean {
        if (ids.isEmpty()) return true
        val distinct = ids.toSet()
        return jdbc.sql("SELECT count(*) FROM access_users WHERE id IN (:ids) AND deleted_at IS NULL")
            .param("ids", distinct).query(Long::class.java).single() == distinct.size.toLong()
    }

    override fun groupMembers(names: Collection<String>): Map<String, Set<UUID>> {
        if (names.isEmpty()) return emptyMap()
        val result = names.toSet().associateWith { mutableSetOf<UUID>() }.toMutableMap()
        val existing = jdbc.sql("SELECT name FROM feature_user_groups WHERE name IN (:names)").param("names", names.toSet())
            .query(String::class.java).set()
        jdbc.sql("SELECT group_name, user_id FROM feature_user_group_members WHERE group_name IN (:names)").param("names", names.toSet())
            .query { rs, _ -> result.getValue(rs.getString(1)).add(rs.getObject(2, UUID::class.java)) }.list()
        return result.filterKeys { it in existing }
    }

    override fun changeFlag(change: FlagChange): StoredFeatureFlag = write {
        val before = jdbc.sql("SELECT * FROM feature_flags WHERE flag_key = :key FOR UPDATE").param("key", change.key)
            .query { rs, _ -> flag(rs) }.optional().orElseThrow { FeatureFailure(FeatureError.NOT_FOUND) }
        if (before.version != change.expectedVersion) throw FeatureFailure(FeatureError.CONFLICT, "A flag mudou; recarregue")
        change.validate()
        // A contagem dos alertas começa quando a flag passa a valer 100% e zera quando deixa de valer.
        val since = when {
            !change.rule.fullRollout() -> null
            before.rule.fullRollout() -> before.fullRolloutSince ?: change.now
            else -> change.now
        }
        jdbc.sql("""
            UPDATE feature_flags SET version = version + 1, rule = CAST(:rule AS jsonb), full_rollout_since = :since, updated_at = :at
            WHERE flag_key = :key
        """).param("rule", FeatureJson.encode(change.rule)).param("since", since?.let(Timestamp::from), Types.TIMESTAMP)
            .param("at", Timestamp.from(change.now)).param("key", change.key).update()
        val after = StoredFeatureFlag(change.key, before.version + 1, change.rule, since, change.now)
        history(change.id, FeatureHistoryType.FLAG, change.key, after.version, change.actor, change.reason, change.now, before.rule, change.rule)
        after
    }

    override fun groups(): List<FeatureGroup> {
        val members = jdbc.sql("""
            SELECT m.group_name, u.id, u.display_name, u.email, u.phone
            FROM feature_user_group_members m JOIN access_users u ON u.id = m.user_id
            ORDER BY m.group_name, u.display_name NULLS LAST, u.email
        """).query { rs, _ -> rs.getString(1) to member(rs) }.list().groupBy({ it.first }, { it.second })
        return jdbc.sql("SELECT * FROM feature_user_groups ORDER BY name").query { rs, _ ->
            val name = rs.getString("name")
            FeatureGroup(name, rs.getLong("version"), members[name].orEmpty(), rs.getTimestamp("updated_at").toInstant())
        }.list()
    }

    override fun changeGroup(change: GroupChange): FeatureGroup = write {
        val current = jdbc.sql("SELECT version FROM feature_user_groups WHERE name = :name FOR UPDATE").param("name", change.name)
            .query(Long::class.java).optional().orElse(0L)
        if (current != change.expectedVersion) throw FeatureFailure(FeatureError.CONFLICT, "O grupo mudou; recarregue")
        val newMembers = change.members.toSet()
        change.validate(newMembers)
        val before = if (current == 0L) null else groupState(change.name)
        if (current == 0L) {
            jdbc.sql("INSERT INTO feature_user_groups(name, version, updated_at) VALUES (:name, 1, :at)")
                .param("name", change.name).param("at", Timestamp.from(change.now)).update()
        } else {
            jdbc.sql("UPDATE feature_user_groups SET version = version + 1, updated_at = :at WHERE name = :name")
                .param("name", change.name).param("at", Timestamp.from(change.now)).update()
        }
        jdbc.sql("DELETE FROM feature_user_group_members WHERE group_name = :name").param("name", change.name).update()
        newMembers.forEach {
            jdbc.sql("INSERT INTO feature_user_group_members(group_name, user_id) VALUES (:name, :user)")
                .param("name", change.name).param("user", it).update()
        }
        history(change.id, FeatureHistoryType.GROUP, change.name, current + 1, change.actor, change.reason, change.now, before,
            groupState(change.name))
        groups().single { it.name == change.name }
    }

    override fun searchUsers(query: String): List<FeatureGroupMember> {
        val digits = query.filter(Char::isDigit).takeIf { it.length >= 4 }
        return jdbc.sql("""
            SELECT id, display_name, email, phone FROM access_users
            WHERE deleted_at IS NULL
              AND (email ILIKE '%' || :query || '%'
                   OR (:digits::text IS NOT NULL AND regexp_replace(coalesce(phone, ''), '[^0-9]', '', 'g') LIKE '%' || :digits || '%'))
            ORDER BY email LIMIT 20
        """).param("query", query.replace("%", "").replace("_", "\\_")).param("digits", digits)
            .query { rs, _ -> member(rs) }.list()
    }

    override fun appVersions(): AppVersions = jdbc.sql("SELECT * FROM feature_app_versions").query { rs, _ ->
        AppVersions(rs.getLong("version"),
            PlatformAppVersions(rs.getString("android_minimum"), rs.getString("android_recommended")),
            PlatformAppVersions(rs.getString("ios_minimum"), rs.getString("ios_recommended")))
    }.single()

    override fun changeAppVersions(change: AppVersionsChange): AppVersions = write {
        jdbc.sql("SELECT singleton FROM feature_app_versions FOR UPDATE").query(Boolean::class.java).single()
        val before = appVersions()
        if (before.version != change.expectedVersion) throw FeatureFailure(FeatureError.CONFLICT, "As versões mudaram; recarregue")
        jdbc.sql("""
            UPDATE feature_app_versions SET version = version + 1, android_minimum = :am, android_recommended = :ar,
                ios_minimum = :im, ios_recommended = :ir, updated_at = :at
        """).param("am", change.android.minimum).param("ar", change.android.recommended)
            .param("im", change.ios.minimum).param("ir", change.ios.recommended).param("at", Timestamp.from(change.now)).update()
        val after = AppVersions(before.version + 1, change.android, change.ios)
        history(change.id, FeatureHistoryType.APP_VERSIONS, "app", after.version, change.actor, change.reason, change.now,
            versionsState(before), versionsState(after))
        after
    }

    override fun history(type: FeatureHistoryType?, key: String?, page: Int, size: Int): FeatureHistoryPage {
        val where = "WHERE (:type::text IS NULL OR h.entity_type = :type) AND (:key::text IS NULL OR h.entity_key = :key)"
        val total = jdbc.sql("SELECT count(*) FROM feature_history h $where").param("type", type?.name).param("key", key)
            .query(Long::class.java).single()
        val items = jdbc.sql("""
            SELECT h.*, u.display_name AS actor_name, u.email AS actor_email FROM feature_history h
            LEFT JOIN access_users u ON u.id = h.actor_user_id
            $where ORDER BY h.created_at DESC, h.version DESC, h.id LIMIT :size OFFSET :offset
        """).param("type", type?.name).param("key", key).param("size", size).param("offset", (page - 1).toLong() * size)
            .query { rs, _ -> entry(rs) }.list()
        return FeatureHistoryPage(page, size, total, items)
    }

    override fun historyEntry(id: UUID): FeatureHistoryEntry? = jdbc.sql("""
        SELECT h.*, u.display_name AS actor_name, u.email AS actor_email FROM feature_history h
        LEFT JOIN access_users u ON u.id = h.actor_user_id WHERE h.id = :id
    """).param("id", id).query { rs, _ -> entry(rs) }.optional().orElse(null)

    private fun <T : Any> write(block: () -> T): T = try {
        tx.execute { lock(); block() }!!
    } catch (_: org.springframework.dao.DuplicateKeyException) {
        // Mesmo requestId (id do histórico) usado de novo, ou duas criações do mesmo grupo.
        throw FeatureFailure(FeatureError.CONFLICT, "Pedido repetido; recarregue")
    }

    private fun lock() {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext('feature_flags'))").query { _, _ -> 0 }.list()
    }

    private fun history(id: UUID, type: FeatureHistoryType, key: String, version: Long, actor: UUID?, reason: String,
        at: Instant, before: Any?, after: Any) {
        jdbc.sql("""
            INSERT INTO feature_history(id, entity_type, entity_key, version, actor_user_id, reason, created_at, before_state, after_state)
            VALUES (:id, :type, :key, :version, :actor, :reason, :at, CAST(:before AS jsonb), CAST(:after AS jsonb))
        """).param("id", id).param("type", type.name).param("key", key).param("version", version)
            .param("actor", actor, Types.OTHER).param("reason", reason).param("at", Timestamp.from(at))
            .param("before", before?.let(FeatureJson::encode)).param("after", FeatureJson.encode(after)).update()
    }

    private fun groupState(name: String): Map<String, Any> = mapOf("name" to name, "members" to
        jdbc.sql("SELECT user_id FROM feature_user_group_members WHERE group_name = :name ORDER BY user_id")
            .param("name", name).query(UUID::class.java).list())

    private fun versionsState(versions: AppVersions) = mapOf("android" to versions.android, "ios" to versions.ios)

    private fun flag(rs: ResultSet) = StoredFeatureFlag(rs.getString("flag_key"), rs.getLong("version"),
        FeatureJson.rule(rs.getString("rule")), rs.getTimestamp("full_rollout_since")?.toInstant(), rs.getTimestamp("updated_at").toInstant())

    private fun member(rs: ResultSet) = FeatureGroupMember(rs.getObject("id", UUID::class.java), rs.getString("display_name"),
        rs.getString("email"), rs.getString("phone"))

    private fun entry(rs: ResultSet) = FeatureHistoryEntry(
        rs.getObject("id", UUID::class.java), FeatureHistoryType.valueOf(rs.getString("entity_type")), rs.getString("entity_key"),
        rs.getLong("version"), rs.getObject("actor_user_id", UUID::class.java),
        rs.getString("actor_name") ?: rs.getString("actor_email"), rs.getString("reason"), rs.getTimestamp("created_at").toInstant(),
        rs.getString("before_state")?.let(FeatureJson::tree), FeatureJson.tree(rs.getString("after_state")),
    )
}
