package br.com.scursel.pinpadppc930

import java.util.concurrent.Semaphore

/**
 * Portao de acesso exclusivo aos bytes da porta.
 *
 * O pinpad tem UMA fila de bytes: se duas operacoes leem ao mesmo tempo, uma rouba a
 * resposta da outra. Casos reais que ja aconteceram:
 *
 *  - o monitor de chip (1x/s) consome o evento espontaneo MS06 no meio de uma leitura de tarja;
 *  - o monitor de chip responde no lugar do "teste completo", que entao falha parcialmente.
 *
 * Quem le bytes (ou escreve e espera resposta) entra pelo portao; quem nao consegue entra
 * desiste em vez de esperar, para nao travar a interface.
 *
 * Usa [Semaphore] e nao ReentrantLock de proposito: o portao e adquirido na thread da UI e
 * liberado na thread de trabalho, e ReentrantLock exigiria que a MESMA thread liberasse
 * (IllegalMonitorStateException). Semaphore nao tem dono.
 *
 * Sem dependencia de Android -> testavel na JVM (ver PortaoIoTest).
 */
class PortaoIo {

    private val sem = Semaphore(1)

    /** true enquanto alguem esta com o portao. */
    val ocupado: Boolean get() = sem.availablePermits() == 0

    /** Tenta entrar sem esperar. false = outra operacao esta usando a porta. */
    fun tentarEntrar(): Boolean = sem.tryAcquire()

    /** Libera o portao. Sempre em `finally`. */
    fun sair() {
        sem.release()
    }

    /**
     * Roda [bloco] com o portao aberto. Retorna null (sem executar nada) se estava ocupado.
     * O portao e liberado mesmo se [bloco] lancar.
     */
    fun <T> exclusivo(bloco: () -> T): T? {
        if (!tentarEntrar()) return null
        try {
            return bloco()
        } finally {
            sair()
        }
    }
}
