package br.com.saqz.groups.presentation.pix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PixKeysTest {
    private val uuid = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun `e-mail vai em minusculas e sem espacos e valida o formato`() {
        assertEquals("racha@saqz.test", PixKeys.normalize(PixKeyType.EMAIL, " Racha@Saqz.TEST "))
        assertTrue(PixKeys.isValid(PixKeyType.EMAIL, "racha@saqz.test"))
        listOf("racha", "racha@", "@saqz.test", "racha@saqz", "ra cha@saqz.test").forEach {
            assertFalse(PixKeys.isValid(PixKeyType.EMAIL, it), it)
        }
    }

    @Test
    fun `aleatoria aceita uuid com ou sem hifens e normaliza`() {
        assertEquals(uuid, PixKeys.normalize(PixKeyType.RANDOM, uuid))
        assertEquals(uuid, PixKeys.normalize(PixKeyType.RANDOM, uuid.replace("-", "")))
        assertEquals(uuid, PixKeys.normalize(PixKeyType.RANDOM, " ${uuid.uppercase()} "))
        assertTrue(PixKeys.isValid(PixKeyType.RANDOM, uuid))
        assertFalse(PixKeys.isValid(PixKeyType.RANDOM, uuid.dropLast(1)))
        assertFalse(PixKeys.isValid(PixKeyType.RANDOM, uuid.replace("e", "z")))
    }

    @Test
    fun `cpf guarda so os 11 digitos e confere os verificadores`() {
        assertEquals("52998224725", PixKeys.normalize(PixKeyType.CPF, "529.982.247-25"))
        assertEquals("52998224725", PixKeys.normalize(PixKeyType.CPF, "529982247250000"))
        assertTrue(PixKeys.isValid(PixKeyType.CPF, "52998224725"))
        assertFalse(PixKeys.isValid(PixKeyType.CPF, "52998224726"))
        assertFalse(PixKeys.isValid(PixKeyType.CPF, "11111111111"))
        assertFalse(PixKeys.isValid(PixKeyType.CPF, "5299822472"))
    }

    @Test
    fun `telefone guarda os 11 digitos e tira o mais 55 colado`() {
        assertEquals("11987654321", PixKeys.normalize(PixKeyType.PHONE, "(11) 98765-4321"))
        assertEquals("11987654321", PixKeys.normalize(PixKeyType.PHONE, "+55 11 98765-4321"))
        assertTrue(PixKeys.isValid(PixKeyType.PHONE, "11987654321"))
        assertFalse(PixKeys.isValid(PixKeyType.PHONE, "1198765432"))
        assertFalse(PixKeys.isValid(PixKeyType.PHONE, "11887654321"))
        assertFalse(PixKeys.isValid(PixKeyType.PHONE, "00987654321"))
    }

    @Test
    fun `deduz o tipo pelo formato e devolve nulo para texto livre`() {
        assertEquals(PixKeyType.EMAIL, PixKeys.deduce("racha@saqz.test"))
        assertEquals(PixKeyType.RANDOM, PixKeys.deduce(uuid))
        assertEquals(PixKeyType.RANDOM, PixKeys.deduce(uuid.replace("-", "")))
        assertEquals(PixKeyType.CPF, PixKeys.deduce("529.982.247-25"))
        assertEquals(PixKeyType.PHONE, PixKeys.deduce("(11) 98765-4321"))
        assertEquals(PixKeyType.PHONE, PixKeys.deduce("+5511987654321"))
        assertNull(PixKeys.deduce("chave antiga em texto livre"))
        assertNull(PixKeys.deduce("12345"))
        assertNull(PixKeys.deduce(""))
        assertNull(PixKeys.deduce(null))
    }

    @Test
    fun `formata cpf e telefone e deixa os outros como guardados`() {
        assertEquals("529.982.247-25", PixKeys.format(PixKeyType.CPF, "52998224725"))
        assertEquals("(11) 98765-4321", PixKeys.format(PixKeyType.PHONE, "11987654321"))
        assertEquals("racha@saqz.test", PixKeys.format(PixKeyType.EMAIL, "racha@saqz.test"))
        assertEquals("texto", PixKeys.format(PixKeyType.CPF, "texto"))
    }

    @Test
    fun `o tipo sai do rotulo guardado e texto livre nao e tipo`() {
        assertEquals(PixKeyType.CPF, PixKeyType.fromLabel("CPF"))
        assertEquals(PixKeyType.RANDOM, PixKeyType.fromLabel(" Aleatória "))
        assertNull(PixKeyType.fromLabel("Lucas Prado · Nubank"))
        assertNull(PixKeyType.fromLabel(null))
    }
}
