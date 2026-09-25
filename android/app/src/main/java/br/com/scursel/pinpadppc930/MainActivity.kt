package br.com.scursel.pinpadppc930

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var usb: PinpadUsb
    private lateinit var status: TextView
    private lateinit var chipTxt: TextView
    private lateinit var infoTxt: TextView
    private lateinit var logView: TextView
    private lateinit var chkMonitor: CheckBox
    private val hora = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /** um consumidor de bytes por vez (monitor de chip x tarja x teste completo) */
    private val io = PortaoIo()
    @Volatile private var monitorando = false
    @Volatile private var cancelar = false
    private var threadMonitor: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usb = PinpadUsb(this)
        setContentView(montarUi())
        log("Teste PIN pad Gertec PPC930 (app nativo, USB host API)")
        log("Ligue o pinpad via OTG e toque em Conectar.")
    }

    override fun onDestroy() {
        monitorando = false
        cancelar = true
        super.onDestroy()
        usb.fechar()
    }

    // ---------- UI ----------
    private fun botao(texto: String, bloco: () -> Unit): Button {
        val b = Button(this)
        b.text = texto
        b.isAllCaps = false
        b.setOnClickListener { bloco() }
        return b
    }

    private fun campo(rotulo: String): Pair<LinearLayout, TextView> {
        val l = LinearLayout(this); l.orientation = LinearLayout.HORIZONTAL
        val r = TextView(this); r.text = rotulo; r.width = dp(64); r.setTextColor(Color.GRAY)
        val v = TextView(this); v.text = "-"; v.typeface = Typeface.MONOSPACE; v.setTextColor(Color.WHITE)
        l.addView(r); l.addView(v)
        return Pair(l, v)
    }

    private fun montarUi(): ViewGroup {
        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setPadding(dp(12), dp(12), dp(12), dp(12))

        val titulo = TextView(this)
        titulo.text = "Teste PIN pad Gertec PPC930"
        titulo.textSize = 19f
        raiz.addView(titulo)

        status = TextView(this)
        status.text = "desconectado"
        status.setTextColor(Color.LTGRAY)
        raiz.addView(status)

        val linha1 = LinearLayout(this)
        linha1.addView(botao("1 · Conectar") { conectar() }, peso())
        linha1.addView(botao("Desconectar") { desconectar() }, peso())
        raiz.addView(linha1)

        val linha2 = LinearLayout(this)
        linha2.addView(botao("💳 Ler tarja (MS05)") { lerTarja() }, peso())
        linha2.addView(botao("🔌 Checar chip (SC02)") { checarChip() }, peso())
        raiz.addView(linha2)

        val linha3 = LinearLayout(this)
        linha3.addView(botao("Info (MT03/PP03)") { infoPinpad() }, peso())
        chkMonitor = CheckBox(this); chkMonitor.text = "monitorar chip 1×/s"; chkMonitor.setTextColor(Color.LTGRAY)
        chkMonitor.setOnCheckedChangeListener { _, v -> monitorando = v; if (v) iniciarMonitor() }
        linha3.addView(chkMonitor, peso())
        raiz.addView(linha3)

        val linha4 = LinearLayout(this)
        val texto = EditText(this); texto.setText("PINPAD OK"); texto.setHint("texto do display")
        texto.filters = arrayOf(android.text.InputFilter.LengthFilter(16))
        linha4.addView(texto, peso(2f))
        linha4.addView(botao("Display") { enviarDisplay(texto.text.toString()) }, peso())
        raiz.addView(linha4)

        raiz.addView(botao("Teste completo (MT10/MT03/MK10/SC02)") { testeCompleto() })

        val (lT, t) = campo("Trilha 1"); raiz.addView(lT)
        val (lT2, t2) = campo("Trilha 2"); raiz.addView(lT2)
        val (lT3, t3) = campo("Trilha 3"); raiz.addView(lT3)
        trilhasTri = arrayOf(t, t2, t3)

        val (lC, c) = campo("Chip"); chipTxt = c; raiz.addView(lC)
        val (lI, i) = campo("Pinpad"); infoTxt = i; raiz.addView(lI)

        logView = TextView(this)
        logView.typeface = Typeface.MONOSPACE
        logView.textSize = 11f
        logView.setTextColor(Color.LTGRAY)
        logView.movementMethod = ScrollingMovementMethod()
        val sc = ScrollView(this)
        sc.addView(logView)
        sc.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240))
        raiz.addView(sc)

        val rolavel = ScrollView(this)
        rolavel.addView(raiz)
        return rolavel
    }

    private lateinit var trilhasTri: Array<TextView>
    private fun peso(p: Float = 1f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, p)
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun log(msg: String) {
        runOnUiThread {
            logView.append("[${hora.format(Date())}] $msg\n")
            val pai = logView.parent as? ScrollView
            pai?.post { pai.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }
    private fun mostrarStatus(s: String, ok: Boolean? = null) = runOnUiThread {
        status.text = s
        status.setTextColor(when (ok) { true -> Color.rgb(34,197,94); false -> Color.rgb(239,68,68); null -> Color.LTGRAY })
    }

    /**
     * Roda em thread de fundo com o portao de I/O fechado — nenhuma outra operacao
     * (nem o monitor de chip) consome bytes enquanto isto roda.
     * O portao e adquirido aqui na UI e liberado na thread de trabalho; por isso PortaoIo
     * usa Semaphore e nao ReentrantLock.
     */
    private fun tarefa(bloco: () -> Unit) {
        if (!io.tentarEntrar()) { log("ocupado - aguarde a operação atual"); return }
        Thread {
            try { bloco() } catch (e: Exception) { log("erro: ${e.message}") }
            finally { io.sair() }
        }.also { it.isDaemon = true; it.start() }
    }

    // ---------- operações ----------
    private fun conectar() = tarefa {
        monitorando = false
        cancelar = false
        runOnUiThread { if (::chkMonitor.isInitialized) chkMonitor.isChecked = false }
        if (usb.conectado) usb.fechar()
        mostrarStatus("abrindo USB…")
        val ok = usb.abrir { log(it) }
        if (!ok) { mostrarStatus("falha ao conectar", false); return@tarefa }
        Thread.sleep(400)
        usb.limpar()
        // warm-up: o primeiro comando depois de abrir costuma ser descartado
        usb.tx(PinpadProtocol.buildFrame("MT10"))
        usb.ler(700); usb.limpar()
        log("warm-up feito")
        mostrarStatus("conectado · ${usb.descrever()}", true)
    }

    /**
     * Nao passa por tarefa(): precisa funcionar justamente quando o portao esta ocupado
     * (ex.: uma leitura de tarja esperando o cartao por ate 270 s). Fecha a USB, o que
     * faz a espera longa terminar, e o flag `cancelar` interrompe os lacos de leitura.
     */
    private fun desconectar() {
        monitorando = false
        cancelar = true
        runOnUiThread { if (::chkMonitor.isInitialized) chkMonitor.isChecked = false }
        Thread {
            try { threadMonitor?.join(2000) } catch (_: InterruptedException) {}
            try { usb.fechar() } catch (e: Exception) { log("erro ao fechar: ${e.message}") }
            mostrarStatus("desconectado")
            log("desconectado")
        }.also { it.isDaemon = true; it.start() }
    }

    private fun comando(cmd: String, param: String? = null, dados: String? = null,
                        timeoutMs: Int = 1500, espera: String? = null): Pair<Int?, PinpadProtocol.Frame?> {
        usb.limpar()
        val f = PinpadProtocol.buildFrame(cmd, param, dados)
        log("→ $cmd${param ?: ""}${if (dados != null) " \"$dados\"" else ""}  ${PinpadProtocol.hex(f)}")
        if (!usb.tx(f)) { log("falha ao escrever na USB"); return Pair(null, null) }
        val buf = ArrayList<Byte>()
        var ack: Int? = null
        var decorrido = 0
        while (decorrido < timeoutMs && !cancelar) {
            val chunk = usb.ler(150)
            if (chunk.isNotEmpty()) { chunk.forEach { buf.add(it) }; decorrido = 0 } else decorrido += 150
            if (ack == null && buf.isNotEmpty()) ack = buf[0].toInt() and 0xFF
            val achado = acharFrame(buf)
            if (achado != null) {
                val fr = achado.frame
                if (espera != null && fr.cmd != espera) {
                    log("   (ignorando resposta atrasada ${fr.cmd})")
                    buf.subList(0, achado.offset + achado.tamanho).clear()
                    continue
                }
                log("← ${if (ack == PinpadProtocol.ACK) "ACK " else ""}${fr.cmd} " +
                    (if (fr.payload.isNotEmpty()) "[${PinpadProtocol.hex(fr.payload)}] " + PinpadProtocol.ascii(fr.payload) else "") +
                    (if (fr.badChecksum) " (checksum inválido!)" else ""))
                return Pair(ack, fr)
            }
            if (ack != null && ack != PinpadProtocol.ACK && ack != PinpadProtocol.EOT) break
        }
        log(when (ack) {
            PinpadProtocol.ACK -> "← ACK (06) sem dados"
            PinpadProtocol.NAK -> "← NAK (15) - comando rejeitado"
            PinpadProtocol.EOT -> "← EOT (04)"
            else -> "← sem resposta"
        })
        return Pair(ack, null)
    }

    /** Um frame completo encontrado no buffer, com o tamanho que ele ocupa. */
    private class Achado(val frame: PinpadProtocol.Frame, val offset: Int, val tamanho: Int)

    /**
     * Procura um frame completo a partir de um STX no buffer.
     *
     * Devolve tambem quantos bytes o frame ocupa para que o chamador descarte SO ele.
     * Antes o codigo fazia buf.clear(), o que jogava fora bytes que chegaram junto —
     * era assim que um evento MS06 se perdia no meio de outra leitura.
     */
    private fun acharFrame(buf: List<Byte>): Achado? {
        for (i in 0 until buf.size - 1) {
            if (buf[i].toInt() and 0xFF != PinpadProtocol.STX) continue
            val len = buf[i + 1].toInt() and 0xFF
            if (len < 8) continue                 // 0x02 solto no lixo: segue procurando
            if (buf.size < i + len) return null   // frame ainda incompleto: espera mais bytes
            val f = PinpadProtocol.parseFrame(buf.toByteArray(), i) ?: continue
            return Achado(f, i, len)
        }
        return null
    }

    private fun lerTarja() = tarefa {
        if (!usb.conectado) { log("conecte primeiro"); return@tarefa }
        mostrarStatus("PASSE O CARTÃO NA TRILHA AGORA (leitor armado · 90 s)", null)
        trilhasTri.forEach { it.text = "-" }
        val (ack, _) = comando("MS05", timeoutMs = 1500)
        if (ack == null) { mostrarStatus("o pinpad não respondeu ao MS05 — leitor não armado", false); return@tarefa }
        if (ack == PinpadProtocol.NAK) { mostrarStatus("leitor recusou armar (NAK)", false); return@tarefa }
        if (ack != PinpadProtocol.ACK) {
            mostrarStatus("o pinpad não confirmou o MS05 (byte ${"%02X".format(ack)}) — leitor não armado", false)
            return@tarefa
        }
        log("leitor armado - aguardando o cartão passar")
        var fr = esperarEvento("MS06", 90_000)
        if (cancelar) return@tarefa                       // desconectou durante a espera
        if (fr == null) {
            log("90 s sem cartão - continuo escutando (o leitor segue armado)")
            mostrarStatus("leitor ainda armado - pode passar o cartão", null)
            fr = esperarEvento("MS06", 180_000)
            if (cancelar) return@tarefa
        }
        if (fr != null && fr.payload.size > 6) {
            val t = PinpadProtocol.extrairTrilhas(fr.payload)
            runOnUiThread {
                trilhasTri[0].text = PinpadProtocol.mascararPan(t["1"] ?: "-")
                trilhasTri[1].text = PinpadProtocol.mascararPan(t["2"] ?: "-")
                trilhasTri[2].text = PinpadProtocol.mascararPan(t["3"] ?: "-")
            }
            log("EVENTO MS06 (cartão lido)")
            mostrarStatus("TARJA LIDA com sucesso", true)
        } else {
            log("nenhum cartão passou durante a escuta")
            mostrarStatus("nenhum cartão lido - toque em Ler tarja e passe o cartão na janela", false)
        }
    }

    /**
     * Espera um frame espontâneo (o pinpad envia sozinho quando lê o cartão).
     * Descarta apenas os frames que não interessam, preservando o resto do buffer —
     * antes um buf.clear() podia jogar fora bytes que chegaram na mesma leitura.
     */
    private fun esperarEvento(cmd: String, timeoutMs: Int): PinpadProtocol.Frame? {
        val buf = ArrayList<Byte>()
        var decorrido = 0
        while (decorrido < timeoutMs && !cancelar) {
            val chunk = usb.ler(250)
            if (chunk.isNotEmpty()) { chunk.forEach { buf.add(it) }; decorrido = 0 } else decorrido += 250
            val achado = acharFrame(buf)
            if (achado != null) {
                if (achado.frame.cmd == cmd) return achado.frame
                buf.subList(0, achado.offset + achado.tamanho).clear()
            }
        }
        return null
    }

    private fun checarChip() = tarefa {
        if (!usb.conectado) { log("conecte primeiro"); return@tarefa }
        val (ack, fr) = comando("SC02", "0", timeoutMs = 1500, espera = "SC03")
        val presente = if (fr != null && fr.cmd == "SC03") {
            val s = PinpadProtocol.ascii(fr.payload)
            s.isNotEmpty() && s.last() == '1'
        } else null
        runOnUiThread {
            chipTxt.text = when (presente) { true -> "PRESENTE"; false -> "ausente"; null -> "?" }
        }
        mostrarStatus(when (presente) {
            true -> "CHIP DETECTADO (cartão inserido)"
            false -> "chip: nenhum cartão inserido"
            null -> if (ack == PinpadProtocol.NAK) "leitor de chip recusou (NAK)" else "sem resposta do leitor de chip"
        }, presente)
    }

    /**
     * Monitor de chip (1×/s).
     *
     * NÃO usa tarefa(): o portão é adquirido a cada iteração, e não uma vez para o loop
     * inteiro. Antes o monitor segurava o portão enquanto a caixa estivesse marcada, e
     * TODO outro botão (tarja, chip, info, display, teste completo, desconectar) era
     * recusado com "ocupado - aguarde a operação atual" — só funcionava desmarcando a caixa.
     *
     * Adquirindo por iteração, uma operação longa (tarja, teste completo) assume a porta
     * entre uma consulta e outra, e o monitor volta sozinho depois.
     */
    private fun monitorarChip() {
        while (monitorando) {
            if (!usb.conectado) { Thread.sleep(500); continue }
            if (!io.tentarEntrar()) { Thread.sleep(300); continue }
            try {
                usb.tx(PinpadProtocol.buildFrame("SC02", "0"))
                val fr = esperarEvento("SC03", 1200)
                val presente = fr?.let { PinpadProtocol.ascii(it.payload).lastOrNull() == '1' }
                runOnUiThread { chipTxt.text = when (presente) { true -> "PRESENTE"; false -> "ausente"; null -> "?" } }
            } catch (e: Exception) {
                log("monitor de chip: ${e.message}")
            } finally {
                io.sair()
            }
            Thread.sleep(300)
        }
    }

    /** Uma única thread de monitor: marcar/desmarcar rápido não pode criar duas. */
    private fun iniciarMonitor() {
        if (threadMonitor?.isAlive == true) return
        threadMonitor = Thread { monitorarChip() }.also { it.isDaemon = true; it.start() }
    }

    private fun infoPinpad() = tarefa {
        if (!usb.conectado) { log("conecte primeiro"); return@tarefa }
        val (_, f1) = comando("MT03", timeoutMs = 2000, espera = "MT03")
        if (f1 != null && f1.cmd == "MT03") {
            val c = PinpadProtocol.camposInfo(f1.payload)
            log("info: série=${c.getOrNull(0)} modelo=${c.getOrNull(1)} firmware=${c.getOrNull(2)}")
            runOnUiThread { infoTxt.text = "${c.getOrNull(0) ?: "?"} · ${c.getOrNull(1) ?: "?"} · ${c.getOrNull(2) ?: "?"}" }
        }
        val (_, f2) = comando("PP03", timeoutMs = 2000, espera = "PP04")
        if (f2 != null) {
            val rtc = PinpadProtocol.ascii(f2.payload).removePrefix("PP04")
            runOnUiThread { infoTxt.append(" · $rtc") }
        }
    }

    private fun enviarDisplay(txt: String) = tarefa {
        if (!usb.conectado) { log("conecte primeiro"); return@tarefa }
        val semAcento = java.text.Normalizer.normalize(txt, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}"), "")
        val limpo = semAcento.filter { it.code in 0x20..0x7E }.take(16)   // linha do LCD: 16 caracteres
        val texto = if (limpo.isBlank()) "PINPAD OK" else limpo
        val (ack, _) = comando("MK10", "2", texto, timeoutMs = 1500)
        mostrarStatus(if (ack == PinpadProtocol.ACK) "display OK - o LCD deve mostrar \"$texto\"" else "sem ACK no display", ack == PinpadProtocol.ACK)
    }

    private fun testeCompleto() = tarefa {
        if (!usb.conectado) { log("conecte primeiro"); return@tarefa }
        // MS05 NÃO entra aqui: ele arma o leitor de tarja e o pinpad ignora os comandos seguintes
        val casos = listOf(
            Triple("MT10", null, "alive"),
            Triple("MT03", null, "nº de série"),
            Triple("MK10", null, "display"),
            Triple("SC02", "0", "leitor de chip")
        )
        var ok = 0
        for ((cmd, param, nome) in casos) {
            val f = PinpadProtocol.buildFrame(cmd, param, if (cmd == "MK10") "TESTE OK" else null)
            usb.limpar()
            log("→ $nome  ${PinpadProtocol.hex(f)}")
            if (!usb.tx(f)) { log("   FALHOU - erro de escrita"); continue }
            var ack: Int? = null
            val buf = ArrayList<Byte>(); var d = 0
            while (d < 2000 && !cancelar) {
                val c = usb.ler(150)
                if (c.isNotEmpty()) { c.forEach { buf.add(it) }; d = 0 } else d += 150
                if (ack == null && buf.isNotEmpty()) ack = buf[0].toInt() and 0xFF
                if (PinpadProtocol.parseFrame(buf.toByteArray()) != null) break
                if (ack != null && ack != PinpadProtocol.ACK) break
            }
            val passou = ack == PinpadProtocol.ACK
            if (passou) ok++
            log("   ${if (passou) "PASSOU" else "FALHOU"} - $nome")
            Thread.sleep(150)
        }
        log("resumo: $ok/${casos.size} testes passaram")
        mostrarStatus(
            when {
                ok == casos.size -> "PINPAD OK - display, identificação e leitor de chip respondendo"
                ok == 0 -> "FALHOU - o pinpad não respondeu (a aplicação está rodando? tela em Program Manager?)"
                else -> "$ok/${casos.size} testes passaram - comportamento parcial"
            },
            when { ok == casos.size -> true; ok == 0 -> false; else -> null })
    }
}
