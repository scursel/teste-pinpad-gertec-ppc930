package br.com.scursel.pinpadppc930

/**
 * Protocolo do PIN pad Gertec PPC930 (linha "PPC").
 *
 *   frame:  STX | LEN(total) | CMD(4) | [param] | [0x1D + dados] | ETX | XOR(STX..ETX)
 *   resposta: ACK(06) [| frame]   /   NAK(15)
 *
 * Sem dependencia de Android -> testavel na JVM (ver PinpadProtocolTest).
 */
object PinpadProtocol {

    const val STX = 0x02
    const val ETX = 0x03
    const val SEP = 0x1D
    const val FSEP = 0x1F
    const val ACK = 0x06
    const val NAK = 0x15
    const val EOT = 0x04

    fun xor(bytes: ByteArray, from: Int, until: Int): Byte {
        var x = 0
        for (i in from until until) x = x xor bytes[i].toInt()
        return x.toByte()
    }

    /** Monta um frame de comando. `param` = 1 char opcional (ex.: linha do display). */
    fun buildFrame(cmd: String, param: String? = null, data: String? = null): ByteArray {
        require(cmd.length == 4) { "comando deve ter 4 caracteres" }
        val body = ArrayList<Byte>()
        body.addAll(cmd.toByteArray(Charsets.US_ASCII).toList())
        if (param != null) body.addAll(param.toByteArray(Charsets.US_ASCII).toList())
        if (data != null) {
            body.add(SEP.toByte())
            body.addAll(data.toByteArray(Charsets.US_ASCII).toList())
        }
        val frame = ArrayList<Byte>()
        frame.add(STX.toByte())
        frame.add(0)                         // LEN provisorio
        frame.addAll(body)
        frame.add(ETX.toByte())
        require(frame.size + 1 <= 255) { "frame grande demais" }
        frame[1] = (frame.size + 1).toByte() // LEN = total (inclui o checksum)
        val arr = frame.toByteArray()
        return arr + xor(arr, 0, arr.size)
    }

    /**
     * Monta um frame com o mesmo enquadramento do pinpad (CMD + payload direto, sem 0x1D).
     * Serve para testes e para simular respostas do equipamento.
     */
    fun buildFrameDeResposta(cmd: String, payload: String): ByteArray {
        require(cmd.length == 4) { "comando deve ter 4 caracteres" }
        val frame = ArrayList<Byte>()
        frame.add(STX.toByte())
        frame.add(0)
        frame.addAll(cmd.toByteArray(Charsets.US_ASCII).toList())
        frame.addAll(payload.toByteArray(Charsets.ISO_8859_1).toList())
        frame.add(ETX.toByte())
        frame[1] = (frame.size + 1).toByte()
        val arr = frame.toByteArray()
        return arr + xor(arr, 0, arr.size)
    }

    data class Frame(val cmd: String, val payload: ByteArray, val badChecksum: Boolean = false)

    /** Procura um frame completo a partir do STX dentro do buffer. */
    fun parseFrame(bytes: ByteArray, offset: Int = 0): Frame? {
        for (i in offset until bytes.size) {
            if (bytes[i].toInt() and 0xFF != STX) continue
            if (i + 2 > bytes.size) return null
            val len = bytes[i + 1].toInt() and 0xFF
            if (len < 8) continue              // 0x02 solto no lixo: nao e um frame valido
            if (i + len > bytes.size) return null
            val f = bytes.copyOfRange(i, i + len)
            val calc = xor(f, 0, f.size - 1)
            if (calc != f[f.size - 1]) return Frame("", ByteArray(0), badChecksum = true)
            val cmd = String(f, 2, 4, Charsets.US_ASCII)
            val payload = if (len > 8) f.copyOfRange(6, len - 2) else ByteArray(0)
            return Frame(cmd, payload)
        }
        return null
    }

    /** Extrai as trilhas do payload de um MS06: campos separados por 0x1F, cada um 'N' + dados. */
    fun extrairTrilhas(payload: ByteArray): Map<String, String> {
        val out = hashMapOf("1" to "-", "2" to "-", "3" to "-")
        var cur = ArrayList<Byte>()
        fun fechar() {
            if (cur.isEmpty()) return
            val n = (cur[0].toInt() and 0xFF).toChar().toString()
            if (out.containsKey(n)) out[n] = ascii(cur.subList(1, cur.size).toByteArray())
            cur = ArrayList()
        }
        for (b in payload) {
            if (b.toInt() and 0xFF == FSEP) fechar() else cur.add(b)
        }
        fechar()
        return out
    }

    /** Campos de um MT03: serial, modelo, firmware (separados por 0x1F). */
    fun camposInfo(payload: ByteArray): List<String> =
        splitFsep(payload).map { it.trim() }.filter { it.isNotEmpty() }

    fun splitFsep(payload: ByteArray): List<String> {
        val partes = ArrayList<String>()
        var cur = ArrayList<Byte>()
        for (b in payload) {
            if (b.toInt() and 0xFF == FSEP) { partes.add(ascii(cur.toByteArray())); cur = ArrayList() }
            else cur.add(b)
        }
        if (cur.isNotEmpty()) partes.add(ascii(cur.toByteArray()))
        return partes
    }

    fun ascii(bytes: ByteArray): String =
        buildString { for (b in bytes) { val v = b.toInt() and 0xFF; if (v in 32..126) append(v.toChar()) } }

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { String.format("%02X", it) }

    /** Mascara PANs em texto: mantem os 6 primeiros e os 4 ultimos digitos de cada run de 13+ digitos. */
    fun mascararPan(s: String): String =
        Regex("\\d{13,}").replace(s) { m ->
            val d = m.value
            d.substring(0, 6) + "*".repeat(d.length - 10) + d.substring(d.length - 4)
        }
}
