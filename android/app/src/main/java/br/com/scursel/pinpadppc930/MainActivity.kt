package br.com.scursel.pinpadppc930

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
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
    private lateinit var cartao: LinearLayout
    private lateinit var cartaoTitulo: TextView
    private lateinit var cartaoDetalhe: TextView
    private val linhasTeste = HashMap<Teste, TextView>()
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
    /**
     * Estado de um resultado. A cor do cartao grande e dos itens da lista "Resultados" vem daqui:
     * verde = passou, vermelho = falhou, amarelo = parcial, azul = em andamento / acao sua.
     */
    private enum class Estado(val fundo: Int, val texto: Int, val icone: String) {
        NEUTRO(Color.rgb(55, 65, 81), Color.LTGRAY, "○"),
        AGUARDANDO(Color.rgb(29, 78, 216), Color.rgb(96, 165, 250), "⏳"),
        OK(Color.rgb(21, 128, 61), Color.rgb(74, 222, 128), "✅"),
        ATENCAO(Color.rgb(180, 83, 9), Color.rgb(251, 191, 36), "⚠️"),
        FALHA(Color.rgb(185, 28, 28), Color.rgb(248, 113, 113), "❌")
    }

    /** Itens da lista "Resultados" (um por parte do pinpad). */
    private enum class Teste(val nome: String) {
        CONEXAO("Conexão USB"),
        TARJA("Tarja magnética"),
        CHIP("Leitor de chip"),
        DISPLAY("Display"),
        IDENT("Identificação")
    }

    private fun botao(texto: String, bloco: () -> Unit): Button {
        val b = Button(this)
        b.text = texto
        b.isAllCaps = false
        b.textSize = 15f
        b.setOnClickListener { bloco() }
        return b
    }

    private fun fundo(cor: Int, raio: Int = 14): GradientDrawable =
        GradientDrawable().apply { setColor(cor); cornerRadius = dp(raio).toFloat() }

    private fun secao(texto: String): TextView {
        val t = TextView(this)
        t.text = texto
        t.textSize = 13f
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.setTextColor(Color.rgb(156, 163, 175))
        t.setPadding(dp(2), dp(18), 0, dp(6))
        return t
    }

    private fun campo(rotulo: String): Pair<LinearLayout, TextView> {
        val l = LinearLayout(this); l.orientation = LinearLayout.HORIZONTAL
        l.setPadding(0, dp(2), 0, dp(2))
        val r = TextView(this); r.text = rotulo; r.width = dp(72); r.setTextColor(Color.GRAY)
        val v = TextView(this); v.text = "-"; v.typeface = Typeface.MONOSPACE; v.setTextColor(Color.WHITE)
        l.addView(r); l.addView(v)
        return Pair(l, v)
    }

    private fun linhaTeste(t: Teste): LinearLayout {
        val l = LinearLayout(this); l.orientation = LinearLayout.HORIZONTAL
        l.setPadding(dp(12), dp(10), dp(12), dp(10))
        val nome = TextView(this); nome.text = t.nome; nome.textSize = 15f; nome.setTextColor(Color.WHITE)
        val res = TextView(this); res.text = "${Estado.NEUTRO.icone} não testado"; res.textSize = 15f
        res.setTextColor(Estado.NEUTRO.texto); res.gravity = Gravity.END
        l.addView(nome, peso()); l.addView(res, peso(1.3f))
        linhasTeste[t] = res
        return l
    }

    private fun montarUi(): ViewGroup {
        val raiz = LinearLayout(this)
        raiz.orientation = LinearLayout.VERTICAL
        raiz.setPadding(dp(14), dp(14), dp(14), dp(24))

        val titulo = TextView(this)
        titulo.text = "Teste PIN pad Gertec PPC930"
        titulo.textSize = 20f
        titulo.setTypeface(titulo.typeface, Typeface.BOLD)
        raiz.addView(titulo)

        status = TextView(this)
        status.text = "○ desconectado"
        status.setTextColor(Color.LTGRAY)
        status.setPadding(0, dp(2), 0, dp(10))
        raiz.addView(status)

        // cartao grande: o resultado da ultima acao, impossivel de nao ver
        cartao = LinearLayout(this)
        cartao.orientation = LinearLayout.VERTICAL
        cartao.setPadding(dp(16), dp(16), dp(16), dp(16))
        cartaoTitulo = TextView(this); cartaoTitulo.textSize = 24f
        cartaoTitulo.setTypeface(cartaoTitulo.typeface, Typeface.BOLD); cartaoTitulo.setTextColor(Color.WHITE)
        cartaoDetalhe = TextView(this); cartaoDetalhe.textSize = 15f; cartaoDetalhe.setTextColor(Color.WHITE)
        cartaoDetalhe.setPadding(0, dp(6), 0, 0)
        cartao.addView(cartaoTitulo); cartao.addView(cartaoDetalhe)
        raiz.addView(cartao)
        aplicarVeredito("Comece por aqui", "Ligue o pinpad no cabo OTG e toque em Conectar.", Estado.NEUTRO)

        raiz.addView(secao("1 · CONEXÃO"))
        val linha1 = LinearLayout(this)
        linha1.addView(botao("🔗 Conectar") { conectar() }, peso())
        linha1.addView(botao("Desconectar") { desconectar() }, peso())
        raiz.addView(linha1)

        raiz.addView(secao("2 · TESTES"))
        raiz.addView(botao("▶ Teste completo (display, identificação, chip)") { testeCompleto() })
        val linha2 = LinearLayout(this)
        linha2.addView(botao("💳 Ler tarja") { lerTarja() }, peso())
        linha2.addView(botao("🔌 Checar chip") { checarChip() }, peso())
        raiz.addView(linha2)
        val linha3 = LinearLayout(this)
        val texto = EditText(this); texto.setText("PINPAD OK"); texto.setHint("texto do display")
        texto.filters = arrayOf(android.text.InputFilter.LengthFilter(16))
        linha3.addView(texto, peso(1.4f))
        linha3.addView(botao("🖥 Display") { enviarDisplay(texto.text.toString()) }, peso())
        raiz.addView(linha3)
        val linha4 = LinearLayout(this)
        linha4.addView(botao("ℹ️ Info do pinpad") { infoPinpad() }, peso())
        chkMonitor = CheckBox(this); chkMonitor.text = "monitorar chip"; chkMonitor.setTextColor(Color.LTGRAY)
        chkMonitor.setOnCheckedChangeListener { _, v -> monitorando = v; if (v) iniciarMonitor() }
        linha4.addView(chkMonitor, peso())
        raiz.addView(linha4)

        raiz.addView(secao("RESULTADOS"))
        val lista = LinearLayout(this); lista.orientation = LinearLayout.VERTICAL
        lista.background = fundo(Color.rgb(24, 27, 34))
        Teste.values().forEach { lista.addView(linhaTeste(it)) }
        raiz.addView(lista)

        raiz.addView(secao("DADOS LIDOS"))
        val (lT, t) = campo("Trilha 1"); raiz.addView(lT)
        val (lT2, t2) = campo("Trilha 2"); raiz.addView(lT2)
        val (lT3, t3) = campo("Trilha 3"); raiz.addView(lT3)
        trilhasTri = arrayOf(t, t2, t3)
        val (lC, c) = campo("Chip"); chipTxt = c; raiz.addView(lC)
        val (lI, i) = campo("Pinpad"); infoTxt = i; raiz.addView(lI)

        // log tecnico: escondido por padrao, para nao confundir quem so quer saber se passou
        logView = TextView(this)
        logView.typeface = Typeface.MONOSPACE
        logView.textSize = 11f
        logView.setTextColor(Color.LTGRAY)
        logView.movementMethod = ScrollingMovementMethod()
        val sc = ScrollView(this)
        sc.addView(logView)
        sc.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260))
        sc.visibility = View.GONE
        lateinit var btnLog: Button
        btnLog = botao("Mostrar detalhes técnicos (log)") {
            val abrir = sc.visibility != View.VISIBLE
            sc.visibility = if (abrir) View.VISIBLE else View.GONE
            btnLog.text = if (abrir) "Esconder detalhes técnicos" else "Mostrar detalhes técnicos (log)"
        }
        raiz.addView(secao("PARA SUPORTE"))
        raiz.addView(btnLog)
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

    private fun aplicarVeredito(titulo: String, detalhe: String, e: Estado) {
        cartao.background = fundo(e.fundo)
        cartaoTitulo.text = "${e.icone}  $titulo"
        cartaoDetalhe.text = detalhe
        cartaoDetalhe.visibility = if (detalhe.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Cartao grande no topo: o que aconteceu e o que fazer agora. */
    private fun veredito(titulo: String, detalhe: String, e: Estado) = runOnUiThread {
        aplicarVeredito(titulo, detalhe, e)
    }

    /** Atualiza um item da lista "Resultados". */
    private fun marcar(t: Teste, e: Estado, texto: String) = runOnUiThread {
        val v = linhasTeste[t] ?: return@runOnUiThread
        v.text = "${e.icone} $texto"
        v.setTextColor(e.texto)
    }

    private fun conexao(texto: String, ok: Boolean?) = runOnUiThread {
        status.text = texto
        status.setTextColor(when (ok) { true -> Estado.OK.texto; false -> Estado.FALHA.texto; null -> Color.LTGRAY })
    }

    /**
     * Roda em thread de fundo com o portao de I/O fechado — nenhuma outra operacao
     * (nem o monitor de chip) consome bytes enquanto isto roda.
     * O portao e adquirido aqui na UI e liberado na thread de trabalho; por isso PortaoIo
     * usa Semaphore e nao ReentrantLock.
     */
    private fun tarefa(bloco: () -> Unit) {
        if (!io.tentarEntrar()) {
            log("ocupado - aguarde a operação atual")
            android.widget.Toast.makeText(this, "Aguarde: outro teste ainda está rodando", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            try { bloco() } catch (e: Exception) { log("erro: ${e.message}"); veredito("Erro", e.message ?: "erro inesperado", Estado.FALHA) }
            finally { io.sair() }
        }.also { it.isDaemon = true; it.start() }
    }

    // ---------- operações ----------
    private fun conectar() = tarefa {
        monitorando = false
        cancelar = false
        runOnUiThread { if (::chkMonitor.isInitialized) chkMonitor.isChecked = false }
        if (usb.conectado) usb.fechar()
        conexao("⏳ abrindo USB…", null)
        veredito("Conectando…", "Se o Android pedir permissão de USB, toque em OK.", Estado.AGUARDANDO)
        marcar(Teste.CONEXAO, Estado.AGUARDANDO, "conectando…")
        val ok = usb.abrir { log(it) }
        if (!ok) {
            conexao("❌ não conectado", false)
            veredito("Não conectou", "Confira o cabo OTG, se o pinpad está ligado e se a permissão de USB foi aceita. Detalhes no log.", Estado.FALHA)
            marcar(Teste.CONEXAO, Estado.FALHA, "falhou")
            return@tarefa
        }
        Thread.sleep(400)
        usb.limpar()
        // warm-up: o primeiro comando depois de abrir costuma ser descartado
        usb.tx(PinpadProtocol.buildFrame("MT10"))
        usb.ler(700); usb.limpar()
        log("warm-up feito")
        conexao("✅ conectado · ${usb.descrever()}", true)
        veredito("Conectado", "Agora toque em Teste completo ou escolha um teste.", Estado.OK)
        marcar(Teste.CONEXAO, Estado.OK, "conectado")
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
            conexao("○ desconectado", null)
            veredito("Desconectado", "Toque em Conectar para testar de novo.", Estado.NEUTRO)
            marcar(Teste.CONEXAO, Estado.NEUTRO, "desconectado")
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
                if (fr.cmd == "MS06") {
                    log("← MS06 (dados do cartão ocultos, ${fr.payload.size} bytes)")
                } else {
                    log("← ${if (ack == PinpadProtocol.ACK) "ACK " else ""}${fr.cmd} " +
                        (if (fr.payload.isNotEmpty()) "[${PinpadProtocol.hex(fr.payload)}] " + PinpadProtocol.ascii(fr.payload) else "") +
                        (if (fr.badChecksum) " (checksum inválido!)" else ""))
                }
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
        if (!usb.conectado) { log("conecte primeiro"); veredito("Conecte primeiro", "Toque em Conectar antes de testar.", Estado.ATENCAO); return@tarefa }
        trilhasTri.forEach { it.text = "-" }

        veredito("Ligando o leitor de tarja…", "Aguarde — ainda NÃO passe o cartão.", Estado.AGUARDANDO)
        marcar(Teste.TARJA, Estado.AGUARDANDO, "ligando o leitor…")

        usb.limpar()
        val f = PinpadProtocol.buildFrame("MS05")
        val hex = PinpadProtocol.hex(f)
        log("→ MS05  $hex")
        if (!usb.tx(f)) {
            log("falha ao escrever na USB")
            veredito("Tarja: FALHOU", "Não foi possível enviar o comando pela USB. Desconecte e conecte de novo.", Estado.FALHA)
            marcar(Teste.TARJA, Estado.FALHA, "erro de USB")
            return@tarefa
        }

        // Ler até 1500 ms para resposta ao MS05
        var ack: Int? = null
        var bufRestante = ByteArray(0)
        var decorrido = 0

        while (decorrido < 1500 && !cancelar) {
            val chunk = usb.ler(150)
            if (chunk.isNotEmpty()) {
                ack = chunk[0].toInt() and 0xFF
                bufRestante = if (chunk.size > 1) chunk.copyOfRange(1, chunk.size) else ByteArray(0)
                break
            } else {
                decorrido += 150
            }
        }

        // Processar resposta ao MS05
        when {
            ack == PinpadProtocol.NAK -> {
                veredito("Tarja: FALHOU", "O pinpad recusou ligar o leitor de tarja (NAK).", Estado.FALHA)
                marcar(Teste.TARJA, Estado.FALHA, "pinpad recusou")
                return@tarefa
            }
            ack != null && ack != PinpadProtocol.ACK -> {
                veredito("Tarja: FALHOU", "Resposta inesperada do pinpad (byte ${"%02X".format(ack)}). O leitor não ligou.", Estado.FALHA)
                marcar(Teste.TARJA, Estado.FALHA, "resposta inesperada")
                return@tarefa
            }
            ack == null -> {
                log("sem resposta ao MS05 - o leitor pode ja estar armado de uma tentativa anterior; sigo escutando")
                veredito("PASSE O CARTÃO AGORA", "O pinpad não confirmou, mas o leitor pode já estar ligado de uma tentativa anterior. Passe o cartão na trilha.", Estado.AGUARDANDO)
                marcar(Teste.TARJA, Estado.AGUARDANDO, "esperando o cartão…")
            }
            ack == PinpadProtocol.ACK -> {
                log("leitor armado - aguardando o cartão passar")
                veredito("PASSE O CARTÃO AGORA", "Leitor ligado. Passe o cartão na trilha, num movimento contínuo (até 90 s).", Estado.AGUARDANDO)
                marcar(Teste.TARJA, Estado.AGUARDANDO, "esperando o cartão…")
            }
        }

        var fr = esperarEvento("MS06", 90_000, bufRestante)
        if (cancelar) return@tarefa
        if (fr == null) {
            log("90 s sem cartão - continuo escutando (o leitor segue armado)")
            veredito("PASSE O CARTÃO AGORA", "Nenhum cartão ainda. O leitor continua ligado por mais 3 minutos.", Estado.AGUARDANDO)
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
            veredito("Tarja: PASSOU", "Cartão lido. As trilhas estão em Dados lidos (número mascarado).", Estado.OK)
            marcar(Teste.TARJA, Estado.OK, "cartão lido")
        } else {
            log("nenhum cartão passou durante a escuta")
            veredito("Tarja: nenhum cartão lido", "Toque em Ler tarja, espere aparecer PASSE O CARTÃO AGORA e só então passe o cartão. Se repetir, a tarja do cartão pode estar ruim — tente outro cartão.", Estado.FALHA)
            marcar(Teste.TARJA, Estado.FALHA, "nenhum cartão lido")
        }
    }

    /**
     * Espera um frame espontâneo (o pinpad envia sozinho quando lê o cartão).
     * Descarta apenas os frames que não interessam, preservando o resto do buffer —
     * antes um buf.clear() podia jogar fora bytes que chegaram na mesma leitura.
     */
    private fun esperarEvento(cmd: String, timeoutMs: Int, inicial: ByteArray = ByteArray(0)): PinpadProtocol.Frame? {
        val buf = ArrayList<Byte>()
        inicial.forEach { buf.add(it) }
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
        if (!usb.conectado) { log("conecte primeiro"); veredito("Conecte primeiro", "Toque em Conectar antes de testar.", Estado.ATENCAO); return@tarefa }
        val (ack, fr) = comando("SC02", "0", timeoutMs = 1500, espera = "SC03")
        val presente = if (fr != null && fr.cmd == "SC03") {
            val s = PinpadProtocol.ascii(fr.payload)
            s.isNotEmpty() && s.last() == '1'
        } else null
        runOnUiThread {
            chipTxt.text = when (presente) { true -> "PRESENTE"; false -> "ausente"; null -> "?" }
        }
        when (presente) {
            true -> {
                veredito("Chip: PASSOU", "Leitor de chip respondeu e há um cartão inserido.", Estado.OK)
                marcar(Teste.CHIP, Estado.OK, "cartão detectado")
            }
            false -> {
                veredito("Chip: leitor OK", "O leitor respondeu, mas não há cartão. Insira um cartão com chip e toque em Checar chip de novo.", Estado.OK)
                marcar(Teste.CHIP, Estado.OK, "responde (sem cartão)")
            }
            null -> {
                val motivo = if (ack == PinpadProtocol.NAK) "O pinpad recusou o comando do chip (NAK)." else "O leitor de chip não respondeu."
                veredito("Chip: FALHOU", motivo, Estado.FALHA)
                marcar(Teste.CHIP, Estado.FALHA, "sem resposta")
            }
        }
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
        if (!usb.conectado) { log("conecte primeiro"); veredito("Conecte primeiro", "Toque em Conectar antes de testar.", Estado.ATENCAO); return@tarefa }
        val (_, f1) = comando("MT03", timeoutMs = 2000, espera = "MT03")
        if (f1 != null && f1.cmd == "MT03") {
            val c = PinpadProtocol.camposInfo(f1.payload)
            log("info: série=${c.getOrNull(0)} modelo=${c.getOrNull(1)} firmware=${c.getOrNull(2)}")
            runOnUiThread { infoTxt.text = "${c.getOrNull(0) ?: "?"} · ${c.getOrNull(1) ?: "?"} · ${c.getOrNull(2) ?: "?"}" }
            veredito("Identificação: PASSOU", "Modelo ${c.getOrNull(1) ?: "?"} · firmware ${c.getOrNull(2) ?: "?"}", Estado.OK)
            marcar(Teste.IDENT, Estado.OK, "série lida")
        } else {
            veredito("Identificação: FALHOU", "O pinpad não informou série/modelo.", Estado.FALHA)
            marcar(Teste.IDENT, Estado.FALHA, "sem resposta")
        }
        val (_, f2) = comando("PP03", timeoutMs = 2000, espera = "PP04")
        if (f2 != null) {
            val rtc = PinpadProtocol.ascii(f2.payload).removePrefix("PP04")
            runOnUiThread { infoTxt.append(" · $rtc") }
        }
    }

    private fun enviarDisplay(txt: String) = tarefa {
        if (!usb.conectado) { log("conecte primeiro"); veredito("Conecte primeiro", "Toque em Conectar antes de testar.", Estado.ATENCAO); return@tarefa }
        val semAcento = java.text.Normalizer.normalize(txt, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}"), "")
        val limpo = semAcento.filter { it.code in 0x20..0x7E }.take(16)   // linha do LCD: 16 caracteres
        val texto = if (limpo.isBlank()) "PINPAD OK" else limpo
        val (ack, _) = comando("MK10", "2", texto, timeoutMs = 1500)
        if (ack == PinpadProtocol.ACK) {
            veredito("Display: comando aceito", "Confira na tela do pinpad: deve aparecer \"$texto\".", Estado.OK)
            marcar(Teste.DISPLAY, Estado.OK, "aceito")
        } else {
            veredito("Display: FALHOU", "O pinpad não confirmou o texto do display.", Estado.FALHA)
            marcar(Teste.DISPLAY, Estado.FALHA, "sem resposta")
        }
    }

    private fun testeCompleto() = tarefa {
        if (!usb.conectado) { log("conecte primeiro"); veredito("Conecte primeiro", "Toque em Conectar antes de testar.", Estado.ATENCAO); return@tarefa }
        // MS05 NÃO entra aqui: ele arma o leitor de tarja e o pinpad ignora os comandos seguintes
        val casos = listOf(
            Triple("MT10", null, "alive"),
            Triple("MT03", null, "nº de série"),
            Triple("MK10", "2", "display"),
            Triple("SC02", "0", "leitor de chip")
        )
        val itemDe = mapOf("MT10" to Teste.CONEXAO, "MT03" to Teste.IDENT, "MK10" to Teste.DISPLAY, "SC02" to Teste.CHIP)
        val falharam = ArrayList<String>()
        veredito("Teste completo em andamento…", "Aguarde alguns segundos.", Estado.AGUARDANDO)
        var ok = 0
        for ((cmd, param, nome) in casos) {
            if (cancelar) return@tarefa
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
            if (passou) ok++ else falharam.add(nome)
            itemDe[cmd]?.let { marcar(it, if (passou) Estado.OK else Estado.FALHA, if (passou) "respondeu" else "não respondeu") }
            log("   ${if (passou) "PASSOU" else "FALHOU"} - $nome")
            Thread.sleep(150)
        }
        if (cancelar) return@tarefa
        log("resumo: $ok/${casos.size} testes passaram")
        when {
            ok == casos.size -> veredito("PASSOU — ${ok} de ${casos.size}",
                "Display, identificação e leitor de chip respondendo. Confira se apareceu TESTE OK na tela do pinpad. A tarja tem teste separado (Ler tarja).", Estado.OK)
            ok == 0 -> veredito("FALHOU — 0 de ${casos.size}",
                "O pinpad não respondeu. Ele está na tela normal (não em Program Manager)? Desconecte e conecte de novo.", Estado.FALHA)
            else -> veredito("PARCIAL — ${ok} de ${casos.size}", "Não responderam: ${falharam.joinToString(", ")}.", Estado.ATENCAO)
        }
    }
}
