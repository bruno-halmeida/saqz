package br.com.saqz.androidapp

import androidx.compose.ui.test.*
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEB-117 / FLG-007 (roteiro 11, F5): versão mínima acima da do build → folha "Atualize o Saqz" com o botão da loja, que
 * o voltar e o toque fora não fecham (vale já na tela de entrada); recomendada → "Tem versão nova do Saqz", que fecha
 * com "Agora não" e não volta para a mesma versão; sem versão configurada → nada. As versões são do servidor inteiro:
 * o teste as devolve a vazio no fim, também em falha.
 */
@RunWith(AndroidJUnit4::class)
internal class AppUpdateE2eTest : InstalledE2e("app-update") {
    @Test
    fun requiredBlocksRecommendedClosesOnceAndNoneShowsNothing() {
        val (major, minor, patch) = BuildConfig.VERSION_NAME.split('.').map { it.takeWhile(Char::isDigit).toInt() }
        try {
            waitTag("login-submit")
            setVersions(minimum = "$major.$minor.${patch + 1}", recommended = null)
            returnToApp()
            waitTag("app-update")
            waitText("Atualize o Saqz")
            waitText("Tem versão nova do app. Para continuar usando, é só atualizar.")
            waitTag("app-update-store")
            waitText("Atualizar no Google Play")
            ui.onNodeWithTag("app-update-not-now").assertDoesNotExist()
            shot("obrigatoria-na-entrada")
            back()
            shell("input tap 540 300")
            ui.waitForIdle()
            Thread.sleep(1_000)
            waitTag("app-update")
            waitTag("app-update-store")
            shot("obrigatoria-nao-fecha")

            // Recomendada própria desta execução: "Agora não" vale por versão e a memória é do aparelho.
            val recommended = "$major.$minor.${patch + 100 + (System.currentTimeMillis() / 60_000 % 800)}"
            setVersions(minimum = null, recommended = recommended)
            returnToApp()
            waitTag("app-update-not-now")
            waitText("Tem versão nova do Saqz")
            waitText("Atualize para ter as novidades e as correções.")
            shot("recomendada")
            tap("app-update-not-now")
            waitGone("app-update-not-now")
            returnToApp()
            assertStaysClosed()
            shot("recomendada-nao-volta")

            setVersions(minimum = null, recommended = null)
            returnToApp()
            assertStaysClosed()
            waitTag("login-submit")
            login("owner")
            assertStaysClosed()
            shot("sem-versao-abre-normal")
        } finally {
            setVersions(minimum = null, recommended = null)
        }
    }

    private fun setVersions(minimum: String?, recommended: String?) {
        val current = api("owner", "/admin/app-versions").getLong("version")
        val android = JSONObject().put("minimum", minimum ?: JSONObject.NULL).put("recommended", recommended ?: JSONObject.NULL)
        val ios = JSONObject().put("minimum", JSONObject.NULL).put("recommended", JSONObject.NULL)
        api("owner", "/admin/app-versions", "PUT", JSONObject().put("expectedVersion", current)
            .put("reason", "E2E app-update").put("android", android).put("ios", ios))
    }

    /** A checagem roda ao abrir e a cada volta ao primeiro plano. */
    private fun returnToApp() {
        ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        ui.waitForIdle()
    }

    private fun assertStaysClosed() {
        Thread.sleep(3_000)
        ui.waitForIdle()
        ui.onNodeWithTag("app-update-store").assertDoesNotExist()
        ui.onNodeWithTag("app-update-not-now").assertDoesNotExist()
    }
}
