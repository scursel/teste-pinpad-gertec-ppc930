# App nativo Android — Teste PIN pad Gertec PPC930

APK que fala com o pinpad usando a **USB host API do Android**. Existe porque o **WebUSB não
consegue** assumir este aparelho em muitos celulares:

| | WebUSB (navegador) | App nativo (este) |
|---|---|---|
| Reivindicar a interface | `claimInterface(num)` — **falha** se o kernel já vinculou o driver (`cdc_acm`) | `claimInterface(iface, **force = true**)` — desvincula o driver do kernel |
| Resultado no PPC930 | `Unable to claim interface` em vários aparelhos | funciona |

O protocolo é o mesmo do app web — veja [`../PROTOCOLO.md`](../PROTOCOLO.md).

## Como usar

1. Instale o APK (`dist/teste-pinpad-ppc930.apk` no repositório, ou compile você mesmo — abaixo).
2. Ligue o pinpad no celular com **cabo OTG**.
3. Abra o app → **1 · Conectar** → aceite o diálogo de permissão USB do Android.
4. Use os botões:

| Botão | O que faz |
|---|---|
| **💳 Ler tarja (MS05)** | arma o leitor e espera o cartão passar (90 s + 3 min de escuta) — **passe o cartão depois de tocar no botão** |
| **🔌 Checar chip (SC02)** | mostra `PRESENTE` / `ausente` |
| **monitorar chip 1×/s** | repete a consulta para você ver mudar ao inserir/remover o cartão |
| **Info (MT03/PP03)** | nº de série, modelo, firmware e RTC |
| **Display** | escreve o texto no LCD do pinpad |
| **Teste completo** | `MT10`, `MT03`, `MK10`, `SC02` — confere ACK de cada um |

> O teste completo **não** usa `MS05`: ele arma o leitor de tarja e, enquanto armado, o pinpad
> ignora todos os comandos seguintes.

## Compilando

Requisitos: JDK 17+ e Android SDK (platform 34 + build-tools 34.0.0).

```bash
cd android
echo "sdk.dir=CAMINHO_DO_SEU_ANDROID_SDK" > local.properties
gradle assembleDebug            # gera app/build/outputs/apk/debug/app-debug.apk
gradle testDebugUnitTest        # roda os testes do protocolo na JVM
```

Os testes (`app/src/test/.../PinpadProtocolTest.kt`) conferem o enquadramento contra **bytes reais**
do equipamento — inclusive o frame do exemplo oficial da Gertec (`MK10` + `PPC-800`) e os bytes que o
PPC930 respondeu `ACK` (`MT03`, `SC02`+`0`, `MS05`, `MT10`). Nenhum dado de cartão real é usado.

## Estrutura

```
app/src/main/java/br/com/scursel/pinpadppc930/
    PinpadProtocol.kt   enquadramento (STX/LEN/CMD/ETX/XOR), parser e trilhas  (Kotlin puro, testável)
    PinpadUsb.kt        USB host API: permissão, claimInterface(force), endpoints bulk, leitura em thread
    MainActivity.kt     interface e fluxos (tarja, chip, info, display, teste completo)
app/src/test/...        testes do protocolo na JVM
```