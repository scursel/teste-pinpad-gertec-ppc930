package br.com.scursel.pinpadppc930

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import java.util.concurrent.ArrayBlockingQueue

/**
 * Transporte USB (Android USB host API).
 *
 * Diferente do WebUSB, aqui podemos usar claimInterface(iface, force=true), que
 * desvincula o driver nativo do kernel (cdc_acm) — e por isso funciona onde o
 * navegador falha com "Unable to claim interface".
 */
class PinpadUsb(private val activity: Activity) {

    companion object {
        const val VID_GERTEC = 0x1753
        const val PID_PPC930 = 0xC902
        private const val ACTION_PERM = "br.com.scursel.pinpadppc930.PERM"
    }

    private val manager: UsbManager = activity.getSystemService(Context.USB_SERVICE) as UsbManager
    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var iface: UsbInterface? = null
    private var epOut: UsbEndpoint? = null
    private var epIn: UsbEndpoint? = null

    @Volatile private var lendo = false
    private var threadLeitura: Thread? = null
    private val rx = ArrayBlockingQueue<Byte>(65536)

    val conectado: Boolean get() = connection != null

    fun descrever(): String {
        val d = device ?: return "desconhecido"
        val m = d.manufacturerName ?: "?"
        val p = d.productName ?: "?"
        return "$m · $p · VID ${"%04X".format(d.vendorId)} PID ${"%04X".format(d.productId)}"
    }

    fun listarGertec(): List<UsbDevice> =
        manager.deviceList.values.filter { it.vendorId == VID_GERTEC }

    /** Abre o dispositivo: pede permissão, reivindica a interface COM FORÇA e acha os endpoints bulk. */
    fun abrir(log: (String) -> Unit): Boolean {
        val dev = listarGertec().firstOrNull()
            ?: manager.deviceList.values.firstOrNull { it.vendorId == VID_GERTEC }
            ?: manager.deviceList.values.firstOrNull()
            ?: run { log("nenhum dispositivo USB encontrado"); return false }
        device = dev
        log("dispositivo: ${dev.manufacturerName} ${dev.productName} VID=${"%04X".format(dev.vendorId)} PID=${"%04X".format(dev.productId)}")
        log("configs=${dev.configurationCount} interfaces=${dev.interfaceCount}")

        if (!manager.hasPermission(dev)) {
            log("pedindo permissão USB… (aceite no diálogo do sistema)")
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(activity, 0, Intent(ACTION_PERM).setPackage(activity.packageName), flags)
            val rec = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    log("permissão: ${if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) "concedida" else "negada"}")
                }
            }
            val reg = if (Build.VERSION.SDK_INT >= 33) Context.RECEIVER_NOT_EXPORTED else 0
            if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(rec, IntentFilter(ACTION_PERM), reg)
            else activity.registerReceiver(rec, IntentFilter(ACTION_PERM))
            manager.requestPermission(dev, pi)
            var espera = 0
            while (!manager.hasPermission(dev) && espera < 30000) { Thread.sleep(200); espera += 200 }
            if (!manager.hasPermission(dev)) { log("permissão não concedida"); return false }
            log("permissão USB concedida")
        }

        val conn = manager.openDevice(dev) ?: run { log("openDevice() retornou null"); return false }
        connection = conn

        // interface com endpoints bulk IN e OUT (no PPC930 é a interface 0, CDC)
        var escolhida: UsbInterface? = null
        var saida: UsbEndpoint? = null
        var entrada: UsbEndpoint? = null
        for (i in 0 until dev.interfaceCount) {
            val itf = dev.getInterface(i)
            var o: UsbEndpoint? = null; var n: UsbEndpoint? = null
            for (e in 0 until itf.endpointCount) {
                val ep = itf.getEndpoint(e)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (ep.direction == UsbConstants.USB_DIR_OUT) o = ep else n = ep
            }
            log("iface ${itf.id}: class=${itf.interfaceClass} sub=${itf.interfaceSubclass} eps=${itf.endpointCount} bulkOut=${o != null} bulkIn=${n != null}")
            if (o != null && n != null && escolhida == null) { escolhida = itf; saida = o; entrada = n }
        }
        escolhida ?: run { log("nenhuma interface com endpoints bulk"); fechar(); return false }

        // o ponto-chave: force = true desvincula o driver do kernel
        val ok = conn.claimInterface(escolhida, true)
        log("claimInterface(${escolhida.id}, force=true) -> $ok")
        if (!ok) { log("não foi possível reivindicar a interface"); fechar(); return false }
        iface = escolhida; epOut = saida; epIn = entrada
        log("interface ${escolhida.id} ok · ep out=${saida!!.address} in=${entrada!!.address}")

        // CDC: line coding + DTR/RTS (best-effort)
        val lineCoding = byteArrayOf(0x00, 0x4B, 0x00, 0x00, 0x00, 0x00, 0x08) // 19200 8N1
        val r1 = conn.controlTransfer(0x21, 0x20, 0, escolhida.id, lineCoding, lineCoding.size, 500)
        val r2 = conn.controlTransfer(0x21, 0x22, 3, escolhida.id, null, 0, 500)
        log("SET_LINE_CODING=$r1 SET_CONTROL_LINE_STATE=$r2 (best-effort)")

        iniciarLeitura()
        return true
    }

    private fun iniciarLeitura() {
        lendo = true
        rx.clear()
        threadLeitura = Thread {
            val buf = ByteArray(512)
            while (lendo) {
                val ep = epIn ?: break
                val n = try { connection?.bulkTransfer(ep, buf, buf.size, 300) ?: -1 } catch (e: Exception) { -1 }
                if (n > 0) for (i in 0 until n) rx.offer(buf[i])
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun fechar() {
        lendo = false
        threadLeitura?.join(800)
        threadLeitura = null
        try { iface?.let { connection?.releaseInterface(it) } } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
        connection = null; iface = null; epOut = null; epIn = null
    }

    fun tx(bytes: ByteArray): Boolean {
        val ep = epOut ?: return false
        val conn = connection ?: return false
        return conn.bulkTransfer(ep, bytes, bytes.size, 2000) >= 0
    }

    /** Aguarda bytes por até timeoutMs (acumula o que chegar). */
    fun ler(timeoutMs: Int): ByteArray {
        val out = ArrayList<Byte>()
        var decorrido = 0
        while (decorrido < timeoutMs) {
            val b = rx.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (b != null) { out.add(b); decorrido = 0 } else decorrido += 50
        }
        return out.toByteArray()
    }

    fun limpar() { rx.clear() }
}
