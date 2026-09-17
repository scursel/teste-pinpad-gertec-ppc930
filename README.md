# Teste PIN pad Gertec PPC930

Ferramenta de bancada para responder uma pergunta simples: **este PIN pad está funcionando?**

Abre direto no navegador, sem instalar nada:

| Onde você roda | Como fala com o pinpad | Requisito |
|---|---|---|
| **PC** (Windows / Linux / macOS) | **Web Serial** — abre a porta COM/serial | Chrome ou Edge |
| **Celular Android** | **WebUSB** — fala direto com o USB do device | Chrome + cabo OTG |

**Aplicativo online:** <https://scursel.github.io/teste-pinpad-gertec-ppc930/>

O app testa **display, tarja magnética, leitor de chip, identificação e integridade do protocolo** —
tudo com comandos documentados em [PROTOCOLO.md](PROTOCOLO.md) e validados em hardware real
(PPC930, firmware `V2.23`).

---

## Uso rápido

### No celular (Android + Chrome)

1. Ligue o pinpad no celular com um **adaptador OTG** (USB-C → USB-A/B).
2. Abra `https://scursel.github.io/teste-pinpad-gertec-ppc930/` no **Chrome**.
3. Toque em **1 · Conectar PIN pad** → escolha **`PPC930 Pinpad Terminal`**.
4. Use os botões de teste (veja abaixo).

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
| **2 · Enviar ao display** | Escreve o texto no LCD (`MK10`) | O LCD do pinpad mostra o texto |
| **3 · Teste completo** | Roda `MT10`, `MT03`, `MK10`, `SC02`, `MS05` e confere ACK | `PINPAD OK` se todos respondem |

### ⚠️ A ordem importa na tarja

O leitor de tarja funciona por **evento**: o pinpad só lê o cartão se estiver **armado** no momento
da passada.

1. Clique em **💳 Ler tarja** → o app responde **"PASSE O CARTÃO NA TRILHA AGORA"** (armado por 90 s;
   depois continua escutando por mais 3 min).
2. **Só então** passe o cartão, devagar, na trilha.
3. As trilhas aparecem na hora (o pinpad envia o frame sozinho, sem o app pedir de novo).

Se você passar o cartão *antes* de armar, o pinpad apita mas não entrega os dados — e o app
corretamente não mostra trilha nenhuma.

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
index.html         aplicativo (arquivo único, sem dependências, funciona offline)
testar-pinpad.ps1  testador por linha de comando (Windows)
PROTOCOLO.md       especificação do protocolo (comandos confirmados + bytes)
```

## Licença

[MIT](LICENSE) © 2026 Gabriel Scursel
