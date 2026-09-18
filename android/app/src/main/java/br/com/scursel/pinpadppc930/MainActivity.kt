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
    private lateinit var trilhas: TextView
    private lateinit var chipTxt: TextView
    private lateinit var infoTxt: TextView
    private lateinit var logView: TextView
    private val hora = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var monitorando = false
    private var ocupado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usb = PinpadUsb(this)
        setContentView(montarUi())
        log("Teste PIN pad Gertec PPC930 (app nativo, USB host API)")
        log("Ligue o pinpad via OTG e toque em Conectar.")
    }

    override fun onDestroy() { super.onDestroy(); usb.fechar() }

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
        val chk = CheckBox(this); chk.text = "monitorar chip 1×/s"; chk.setTextColor(Color.LTGRAY)
        chk.setOnCheckedChangeListener { _, v -> monitorando = v; if (v) iniciarMonitor() }
        linha3.addView(chk, peso())
        raiz.addView(linha3)

        val linha4 = LinearLayout(this)
        val texto = EditText(this); texto.setText("PINPAD OK"); texto.setHint("texto do display")
        linha4.addView(texto, peso(2f))
        linha4.addView(botao("Display") { enviarDisplay(texto.text.toString()) }, peso())
        raiz.addView(linha4)

        raiz.addView(botao("Teste completo (MT10/MT03/MK10/SC02)") { testeCompleto() })

        val (lT, t) = campo("Trilha 1"); trilhas = t; raiz.addView(lT)
        val (lT2, t2) = campo("Trilha 2"); raiz.addView(lT2)
        val (lT3, t3) = campo("Trilha 3"); raiz.addView(lT3)
        t2.tag = "t2"; t3.tag = "t3"
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

    /** roda em thread de fundo e protege contra concorrência */
    private fun tarefa(bloco: () -> Unit) {
        if (ocupado) { log("ocupado - aguarde a operação atual"); return }
        ocupado = true
        Thread {
            try { bloco() } catch (e: Exception) { log("erro: ${e.message}") }
            finally { ocupado = false }
        }.also { it.isDaemon = true; it.start() }
    }

    // ---------- operações ----------
    private fun conectar() = tarefa {
        monitorando = false
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

    private fun desconectar() = tarefa {
        monitorando = false
        usb.fechar()
        mostrarStatus("desconectado")
        log("desconectado")
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
        while (decorrido < timeoutMs) {
            val chunk = usb.ler(150)
            if (chunk.isNotEmpty()) { chunk.forEach { buf.add(it) }; decorrido = 0 } else decorrido += 150
            if (ack == null && buf.isNotEmpty()) ack = buf[0].toInt() and 0xFF
            val fr = PinpadProtocol.parseFrame(buf.toByteArray())
            if (fr != null) {
                if (espera != null && fr.cmd != espera) {
                    log("   (ignorando resposta atrasada ${fr.cmd})")
                    buf.clear(); continue
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

    private fun lerTarja() = tarefa {
        if (!usb.conectado) { log("conecte primeiro"); return@tarefa }
        mostrarStatus("PASSE O CARTÃO NA TRILHA AGORA (leitor armado · 90 s)", null)
        trilhasTri.forEach { it.text = "-" }
        val (ack, _) = comando("MS05", timeoutMs = 1500)
        if (ack == PinpadProtocol.NAK) { mostrarStatus("leitor recusou armar (NAK)", false); return@tarefa }
        log("leitor armado - aguardando o cartão passar")
        var fr = esperarEvento("MS06", 90_000)
        if (fr == null) {
            log("90 s sem cartão - continuo escutando (o leitor segue armado)")
            mostrarStatus("leitor ainda armado - pode passar o cartão", null)
            fr = esperarEvento("MS06", 180_000)
        }
        if (fr != null && fr.payload.size > 6) {
            val t = PinpadProtocol.extrairTrilhas(fr.payload)
            runOnUiThread {
                trilhasTri[0].text = t["1"]; trilhasTri[1].text = t["2"]; trilhasTri[2].text = t["3"]
            }
            log("EVENTO MS06 (cartão lido)")
            mostrarStatus("TARJA LIDA com sucesso", true)
        } else {
            log("nenhum cartão passou durante a escuta")
            mostrarStatus("nenhum cartão lido - toque em Ler tarja e passe o cartão na janela", false)
        }
    }

    /** espera um frame espontâneo (o pinpad envia sozinho quando lê o cartão) */
    private fun esperarEvento(cmd: String, timeoutMs: Int): PinpadProtocol.Frame? {
        val buf = ArrayList<Byte>()
        var decorrido = 0
        while (decorrido < timeoutMs) {
            val chunk = usb.ler(250)
            if (chunk.isNotEmpty()) { chunk.forEach { buf.add(it) }; decorrido = 0 } else decorrido += 250
            val fr = PinpadProtocol.parseFrame(buf.toByteArray())
            if (fr != null) {
                if (fr.cmd == cmd) return fr
                buf.clear()
            }
        }
        return null
    }

    private fun checarChip() = tarefa {
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

    private fun monitorarChip() = tarefa {
        while (monitorando) {
            val f = PinpadProtocol.buildFrame("SC02", "0")
            usb.tx(f)
            val fr = esperarEvento("SC03", 1200)
            val presente = fr?.let { PinpadProtocol.ascii(it.payload).lastOrNull() == '1' }
            runOnUiThread { chipTxt.text = when (presente) { true -> "PRESENTE"; false -> "ausente"; null -> "?" } }
            Thread.sleep(300)
        }
    }
    private fun iniciarMonitor() {
        Thread { monitorarChip() }.also { it.isDaemon = true; it.start() }
    }

    private fun infoPinpad() = tarefa {
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
        val (ack, _) = comando("MK10", "2", txt, timeoutMs = 1500)
        mostrarStatus(if (ack == PinpadProtocol.ACK) "display OK - o LCD deve mostrar \"$txt\"" else "sem ACK no display", ack == PinpadProtocol.ACK)
    }

    private fun testeCompleto() = tarefa {
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
            while (d < 2000) {
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
