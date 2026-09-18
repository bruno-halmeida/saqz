package br.com.saqz.access.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegisterTermsLinksTest {
    @Test
    fun `terms sentence links both public documents`() {
        val sentence = "Ao criar sua conta, você concorda com os Termos de uso e a Política de privacidade."

        val links = termsText(sentence, Color.Black).getLinkAnnotations(0, sentence.length)

        assertEquals(
            listOf("https://saqz.app/termos/", "https://saqz.app/privacidade/"),
            links.map { (it.item as LinkAnnotation.Url).url },
        )
        assertEquals("Termos de uso", sentence.substring(links[0].start, links[0].end))
        assertEquals("Política de privacidade", sentence.substring(links[1].start, links[1].end))
    }

    @Test
    fun `sentence without the locators stays plain`() {
        val sentence = "Sem documentos por aqui."

        assertTrue(termsText(sentence, Color.Black).getLinkAnnotations(0, sentence.length).isEmpty())
    }
}
