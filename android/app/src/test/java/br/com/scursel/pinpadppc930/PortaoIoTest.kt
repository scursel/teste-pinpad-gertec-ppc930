package br.com.scursel.pinpadppc930

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do portao de I/O (JVM, sem aparelho).
 *
 * Cobrem a regra que faltava: enquanto uma operacao esta lendo a porta, nenhuma outra
 * pode entrar — era isso que fazia o monitor de chip engolir o evento MS06 da tarja.
 */
class PortaoIoTest {

    @Test
    fun `so uma operacao entra por vez`() {
        val p = PortaoIo()
        assertFalse(p.ocupado)
        assertTrue(p.tentarEntrar())
        assertTrue(p.ocupado)
        assertFalse("a segunda operacao nao pode entrar", p.tentarEntrar())
        p.sair()
        assertFalse(p.ocupado)
        assertTrue("depois de sair, a porta reabre", p.tentarEntrar())
    }

    @Test
    fun `exclusivo devolve null e nao executa quando ocupado`() {
        val p = PortaoIo()
        assertTrue(p.tentarEntrar())
        var executou = false
        val r = p.exclusivo { executou = true; 42 }
        assertNull(r)
        assertFalse("o bloco nao pode rodar com a porta ocupada", executou)
        p.sair()
    }

    @Test
    fun `exclusivo devolve o valor e libera o portao`() {
        val p = PortaoIo()
        assertEquals(7, p.exclusivo { 7 })
        assertFalse("o portao tem de voltar livre", p.ocupado)
        assertTrue(p.tentarEntrar())
    }

    @Test
    fun `exclusivo libera o portao mesmo quando o bloco lanca`() {
        val p = PortaoIo()
        try {
            p.exclusivo<Unit> { throw IllegalStateException("falha simulada") }
        } catch (_: IllegalStateException) {
        }
        assertFalse("excecao nao pode travar o portao", p.ocupado)
        assertTrue(p.tentarEntrar())
    }

    @Test
    fun `portao adquirido numa thread e liberado em outra`() {
        // e exatamente o uso do app: tarefa() adquire na UI e libera na thread de trabalho
        val p = PortaoIo()
        assertTrue(p.tentarEntrar())
        val t = Thread { p.sair() }
        t.start()
        t.join(2000)
        assertFalse("Semaphore nao tem dono: outra thread pode liberar", p.ocupado)
        assertTrue(p.tentarEntrar())
    }
}
