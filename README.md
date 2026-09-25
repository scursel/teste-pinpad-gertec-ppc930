# Teste PIN pad Gertec PPC930

Ferramenta de bancada para responder uma pergunta simples: **este PIN pad está funcionando?**

Abre direto no navegador, sem instalar nada:

| Onde você roda | Como fala com o pinpad | Requisito |
|---|---|---|
| **PC** (Windows / Linux / macOS) | **Web Serial** — abre a porta COM/serial | Chrome ou Edge |
| **Celular Android** | **App nativo (APK)** — USB host API | cabo OTG |

**Aplicativo online:** <https://scursel.github.io/teste-pinpad-gertec-ppc930/>

📱 **App Android (APK):** **[baixar a última versão](https://github.com/scursel/teste-pinpad-gertec-ppc930/releases/latest/download/teste-pinpad-ppc930.apk)**
· [todas as versões](https://github.com/scursel/teste-pinpad-gertec-ppc930/releases)

> ⚠️ **No celular o navegador não funciona.** O Android reserva a interface USB deste pinpad para o
> driver nativo do kernel (`cdc_acm`) e o WebUSB não tem como desvincular — a conexão falha com
> `Unable to claim interface`. A própria página detecta o celular, avisa e oferece o APK. Use o app
> nativo (veja [android/](android/README.md)); o navegador é o caminho no PC.

O app testa **display, tarja magnética, leitor de chip, identificação e integridade do protocolo** —
tudo com comandos documentados em [PROTOCOLO.md](PROTOCOLO.md) e validados em hardware real
(PPC930, firmware `V2.23`).

---

## Uso rápido

### No celular (Android) — app nativo

1. Baixe o APK: **[última versão](https://github.com/scursel/teste-pinpad-gertec-ppc930/releases/latest/download/teste-pinpad-ppc930.apk)**
   (ou `dist/teste-pinpad-ppc930.apk`, ou o link no aviso da página).
2. Ligue o pinpad no celular com um **adaptador OTG** (USB-C → USB-A/B).
3. Instale o APK — autorize "fontes desconhecidas" quando o Android pedir.
   *Atualizando da v1.0?* Desinstale a versão antiga antes (a assinatura mudou).
4. Abra o app → **1 · Conectar** → aceite o diálogo de permissão USB do Android.
5. Use os botões de teste (veja abaixo).

> O comportamento do WebUSB varia por fabricante, então vale tentar outro aparelho — mas o caminho
> recomendado no celular é o app nativo. Veja [android/README.md](android/README.md).

### No PC (Chrome/Edge)

1. Ligue o pinpad na USB (aparece como porta COM, ex.: `COM7`).
2. Abra o arquivo `index.html` (ou a URL do GitHub Pages acima).
3. **1 · Conectar PIN pad** → escolha a porta COM do pinpad na lista do Chrome
   *(deixe o transporte em "Automático" — no PC ele usa serial automaticamente).*

> Se você usa o app oficial da Gertec (`Teste PPC 800/900`) ou qualquer monitor serial,
> **feche-o antes**: ele segura a porta COM e o navegador não consegue abrir.

---

## Botões e o que cada um prova

| Botão | O que faz | Como saber que passou |
|---|---|---|
| **💳 Ler tarja (MS05)** | Arma o leitor de tarja e espera o cartão passar | As **Trilhas 1/2/3** aparecem preenchidas e o veredito fica verde |
| **🔌 Checar chip (SC02)** | Consulta a leitora de chip | Etiqueta **`chip: PRESENTE`** / `ausente` |
| **monitorar chip** | Repete a consulta 1×/s (útil para inserir/remover o cartão e ver mudar) | A etiqueta muda em ~1 s |
| **Info (MT03 / PP03)** | Lê nº de série, modelo, firmware e RTC | Campos preenchidos |
| **2 · Enviar ao display** | Escreve o texto no LCD (`MK10`) — até 16 caracteres, acentos são removidos | O LCD do pinpad mostra o texto |
| **3 · Teste completo** | Roda `MT10`, `MT03`, `MK10`, `SC02` e confere ACK | `PINPAD OK` se todos respondem |

### ⚠️ A ordem importa na tarja

O leitor de tarja funciona por **evento**: o pinpad só lê o cartão se estiver **armado** no momento
da passada.

1. Clique em **💳 Ler tarja** → o app responde **"PASSE O CARTÃO NA TRILHA AGORA"** (armado por 90 s;
   depois continua escutando por mais 3 min).
2. **Só então** passe o cartão, devagar, na trilha.
3. As trilhas aparecem na hora (o pinpad envia o frame sozinho, sem o app pedir de novo).

Se você passar o cartão *antes* de armar, o pinpad apita mas não entrega os dados — e o app
corretamente não mostra trilha nenhuma.

Se o pinpad **não responder** ao `MS05`, o app avisa "leitor não armado" na hora, em vez de
ficar esperando o cartão.

### 🔒 Dados do cartão

As trilhas aparecem com o **número do cartão (PAN) mascarado** — `6 primeiros + *** + 4 últimos`,
ex.: `411111******1111` — no app web, no app Android e no script PowerShell. O payload bruto do
evento `MS06` não é exibido nem gravado no log. No script PowerShell, `-Completo` mostra tudo
(use só com cartão de teste).

---

## O que o teste completo verifica

| # | O que envia | Resposta esperada | O que prova |
|---|---|---|---|
| 1 | `MT10` | `ACK` (`06`) | firmware vivo e respondendo |
| 2 | `MT03` | `MT03` + série/modelo/firmware | aplicação carregada e identificável |
| 3 | `MK10` + texto | `ACK` | canal de escrita no display |
| 4 | `SC02` + `0` | `SC03` | leitora de chip respondendo |

Se os quatro passam, o pinpad está com firmware, USB/cabo e periféricos básicos OK.
Se **nada** responde, veja a seção de estados abaixo.

> `MS05` (tarja) **não** entra no teste completo de propósito: ele arma o leitor e, enquanto
> armado, o pinpad ignora todos os comandos seguintes. A tarja tem botão próprio (💳 Ler tarja).

---

## Estados do pinpad (diagnóstico rápido)

| Sintoma | O que é | O que fazer |
|---|---|---|
| Responde `ACK`/dados | aplicação rodando | ✅ normal |
| Enumera na USB mas **não responde nada** e a tela mostra **`Program Manager`** | boot/download mode — a aplicação não está carregada | selecione a aplicação no teclado; se não subir, precisa do Download Tool da Gertec |
| Tela mostra **`APP Blocked`** | bloqueio de segurança (tamper/GEDI): as chaves foram invalidadas | não tem recuperação por software — assistência técnica Gertec |
| Nada responde e a tela está apagada | sem alimentação / cabo | troque cabo/porta; OTG de celular pode precisar de hub com fonte |

---

## Protocolo

O formato de frame e **todos os comandos confirmados** (com exemplos de bytes) estão em
[PROTOCOLO.md](PROTOCOLO.md):

```
STX | LEN(total) | CMD(4) | [param] | [0x1D + dados] | ETX | XOR(STX..ETX)
```

Destaques: `MT03` (série/modelo/firmware), `PP03` (RTC), `MK10` (display), `MS05`→**evento `MS06`**
(trilhas da tarja), `SC02`→`SC03` (presença de chip).

---

## Alternativa por linha de comando (Windows)

O repositório inclui `testar-pinpad.ps1`:

```powershell
# teste básico (display + ACK)
powershell -ExecutionPolicy Bypass -File .\testar-pinpad.ps1 -Port COM7

# leitura de tarja (arma e espera o cartão passar por 60 s)
powershell -ExecutionPolicy Bypass -File .\testar-pinpad.ps1 -Port COM7 -Tarja

# leitor de chip
powershell -ExecutionPolicy Bypass -File .\testar-pinpad.ps1 -Port COM7 -Chip
```

> Use **uma ponta por vez** na porta: se o app no navegador estiver conectado, o script falha com
> "acesso negado" (e vice-versa).

---

## Solução de problemas

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `Access denied` ao conectar | No **Windows**, o device está preso ao driver da porta COM (`usbser`); o WebUSB exige driver WinUSB | Use o transporte **serial** (é o padrão no PC). WebUSB ali só serve no Android/Linux |
| `Unable to claim interface` **no celular** | O Android reservou o pinpad (classe CDC) para o driver nativo do kernel e não libera para o navegador | Sem contorno pelo navegador nesse aparelho: caminho nativo é APK (USB host API) ou SDK da Gertec. Vale testar outro celular — o comportamento varia por fabricante |
| `...a porta está em uso` / `Failed to open serial port` | Outro programa segurando a COM (app Gertec, monitor serial, ou o próprio script PowerShell) | Feche o outro programa e conecte de novo |
| Tarja não preenche as trilhas | O cartão passou **antes** de armar, ou a tarja está suja/riscada | Clique em **Ler tarja** e passe o cartão **durante** a janela |
| Chip não acusa presença | Cartão mal inserido / leitor com sujeira | Insira até o fim; limpe o contato dourado |
| No celular o device não aparece | Cabo OTG sem dados, ou falta de energia | Use outro adaptador OTG; se o LCD piscar/apagar, use **hub OTG com fonte** |
| Lista de portas vazia no PC | Pinpad não enumerado | Gerenciador de Dispositivos → *Portas (COM e LPT)*; troque o cabo/porta USB |

---

## Verificação (hardware real)

```
conectado: serial · USB 1753:C902 · 19200 8N1
→ MT03  ← ACK MT03  série 7200032201007922 · PPC930 · V2.23 210223
→ PP03  ← ACK PP04  026/09/17 11:28:12
→ SC02  ← ACK SC03 "01"      (cartão de chip inserido)
→ MS05  ← ACK               (leitor de tarja armado)
   ... cartão passa ...
   ← EVENTO MS06  1F 31 %B************9853^NOME/SOBRENOME^... 1F 32 ;************9853=...
   TARJA LIDA com sucesso
```

Dados de cartão nos exemplos são sempre **fictícios/mascarados**.

O caminho WebUSB foi validado até a seleção/permissão do device
(`GERTEC · PPC930 Pinpad Terminal · VID 1753 PID C902`); a abertura da interface USB depende do
Android (no Windows é bloqueada pelo driver da porta COM).

---

## Estrutura

```
index.html                    aplicativo web (arquivo único, sem dependências, funciona offline)
testar-pinpad.ps1             testador por linha de comando (Windows)
android/                      app nativo Android (USB host API) — ver android/README.md
dist/teste-pinpad-ppc930.apk  APK pronto para instalar no celular (cópia do último Release)
PROTOCOLO.md                  especificação do protocolo (comandos confirmados + bytes)
CHANGELOG.md                  o que mudou em cada versão
docs/plans/                   planos de trabalho (ex.: correções da auditoria)
.github/workflows/android.yml CI: testes JVM + build do APK; tag v* publica o Release
```

## Versões e publicação do APK

O histórico está em [CHANGELOG.md](CHANGELOG.md). Para publicar uma versão nova:

1. Suba `versionCode`/`versionName` em `android/app/build.gradle.kts` e anote em `CHANGELOG.md`.
2. Crie e envie a tag: `git tag v1.2 && git push origin v1.2` — ou, sem terminal, em
   **Actions → android → Run workflow**, informando a versão (ex.: `v1.2`).
3. O GitHub Actions roda os testes, compila o APK e cria o Release com o arquivo
   `teste-pinpad-ppc930.apk` — o link "última versão" deste README passa a apontar para ele.

Assinatura: veja [android/README.md](android/README.md#assinatura-release).

## App nativo para Android

Em vários celulares o **navegador não consegue** assumir o pinpad: o Android vincula o driver
nativo do kernel à interface CDC e o WebUSB não tem como desvincular (`Unable to claim interface`).
Um app nativo resolve, porque a USB host API do Android tem `claimInterface(iface, force = true)`.

Por isso existe o app em [`android/`](android/README.md) — mesmo protocolo, mesmos testes, e o
APK pronto em `dist/teste-pinpad-ppc930.apk` (instale com cabo OTG + permissão de fontes
desconhecidas).

| Onde | Caminho | Status |
|---|---|---|
| PC (Windows/Linux/macOS) | Web Serial no navegador | ✅ funciona |
| Android — navegador | WebUSB | ⚠️ depende do aparelho (kernel pode segurar a interface) |
| Android — app nativo | USB host API + `claimInterface(force)` | ✅ caminho recomendado |


## Licença

[MIT](LICENSE) © 2026 Gabriel Scursel
