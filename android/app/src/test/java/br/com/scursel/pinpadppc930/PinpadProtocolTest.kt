package br.com.scursel.pinpadppc930

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do protocolo na JVM (sem aparelho). Os frames de COMANDO abaixo são os bytes
 * reais enviados ao PPC930 e que o equipamento respondeu ACK; os frames de RESPOSTA
 * usam dados fictícios (nenhum dado de cartão real).
 */
class PinpadProtocolTest {

    private fun hex(b: ByteArray) = PinpadProtocol.hex(b)

    @Test
    fun `frame de display MK10 bate com o exemplo oficial da Gertec`() {
        // byte-exato do exemplo Ex_Comunic_PPC910_Linux: STX 11 "MK10" 2 0x1D "PPC-800" ETX CHK
        val f = PinpadProtocol.buildFrame("MK10", "2", "PPC-800")
        assertEquals("02 11 4D 4B 31 30 32 1D 50 50 43 2D 38 30 30 03 6E", hex(f))
    }

    @Test
    fun `frames de comando batem com os bytes que o pinpad aceitou`() {
        assertEquals("02 08 4D 54 30 33 03 13", hex(PinpadProtocol.buildFrame("MT03")))
        assertEquals("02 09 53 43 30 32 30 03 2A", hex(PinpadProtocol.buildFrame("SC02", "0")))
        assertEquals("02 08 4D 53 30 35 03 12", hex(PinpadProtocol.buildFrame("MS05")))
        assertEquals("02 08 4D 54 31 30 03 11", hex(PinpadProtocol.buildFrame("MT10")))
    }

    @Test
    fun `parseia resposta de identificacao MT03`() {
        val resp = PinpadProtocol.buildFrameDeResposta("MT03", "0000000000000001\u001FPPC930 \u001FV9.99 000000")
        val fr = PinpadProtocol.parseFrame(resp)
        assertNotNull(fr)
        assertEquals("MT03", fr!!.cmd)
        val campos = PinpadProtocol.camposInfo(fr.payload)
        assertEquals("0000000000000001", campos[0])
        assertEquals("PPC930", campos[1])
        assertEquals("V9.99 000000", campos[2])
    }

    @Test
    fun `parseia presenca de chip no SC03`() {
        val comCartao = PinpadProtocol.parseFrame(PinpadProtocol.buildFrameDeResposta("SC03", "01"))
        val semCartao = PinpadProtocol.parseFrame(PinpadProtocol.buildFrameDeResposta("SC03", "00"))
        assertEquals('1', PinpadProtocol.ascii(comCartao!!.payload).last())
        assertEquals('0', PinpadProtocol.ascii(semCartao!!.payload).last())
    }

    @Test
    fun `parseia trilhas do evento MS06 com dados ficticios`() {
        // PAN fictício, formato real: 1F '1' trilha1 1F '2' trilha2
        val t1 = "%B4111111111111111^NOME/SOBRENOME           ^3210201000000000000000000000?F"
        val t2 = ";4111111111111111=32102010000000000000?A"
        val payload = "\u001F1$t1\u001F2$t2"
        val fr = PinpadProtocol.parseFrame(PinpadProtocol.buildFrameDeResposta("MS06", payload))
        assertNotNull(fr)
        assertEquals("MS06", fr!!.cmd)
        val trilhas = PinpadProtocol.extrairTrilhas(fr.payload)
        assertEquals(t1, trilhas["1"])
        assertEquals(t2, trilhas["2"])
        assertEquals("-", trilhas["3"])
    }

    @Test
    fun `rejeita frame com checksum invalido`() {
        val bom = PinpadProtocol.buildFrameDeResposta("SC03", "01")
        val ruim = bom.copyOf()
        ruim[ruim.size - 1] = (ruim[ruim.size - 1].toInt() xor 0xFF).toByte()
        val fr = PinpadProtocol.parseFrame(ruim)
        assertTrue(fr == null || fr.badChecksum)
    }

    @Test
    fun `monta e le o frame de resposta com LEN correto`() {
        val f = PinpadProtocol.buildFrameDeResposta("SC03", "00")
        assertEquals(10, f.size)
        assertEquals(10, f[1].toInt() and 0xFF)   // LEN = total
        assertEquals(PinpadProtocol.STX, f[0].toInt() and 0xFF)
    }
}