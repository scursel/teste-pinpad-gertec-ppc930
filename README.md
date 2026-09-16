# Teste PIN pad Gertec PPC930

Ferramenta de bancada para responder uma pergunta simples: **este PIN pad está funcionando?**

Abre direto no navegador, sem instalar nada:

| Onde você roda | Como fala com o pinpad | Requisito |
|---|---|---|
| **PC** (Windows / Linux / macOS) | **Web Serial** — abre a porta COM/serial | Chrome ou Edge |
| **Celular Android** | **WebUSB** — fala direto com o USB do device | Chrome + cabo OTG |

O app monta os comandos do protocolo Gertec, **valida o checksum das respostas (ACK/NAK)** e termina com um veredito: `PINPAD OK` ou `FALHOU`.

**Aplicativo online (celular):** <https://scursel.github.io/teste-pinpad-gertec-ppc930/>

---

## Uso rápido

### No celular (Android + Chrome)

1. Ligue o pinpad no celular com um **adaptador OTG** (USB-C → USB-A/B).
2. Abra `https://scursel.github.io/teste-pinpad-gertec-ppc930/` no **Chrome**.
3. Toque em **1 · Conectar PIN pad** → escolha **`PPC930 Pinpad Terminal`**.
4. Toque em **3 · Teste completo (ACK / NAK)**.

O LCD do pinpad deve mostrar o texto enviado no teste, e o app fecha com:

```
resumo: 4/4 testes passaram
VEREDITO: PINPAD OK - comunicação, framing e checksum validados
```

### No PC (Chrome/Edge)

1. Ligue o pinpad na USB (aparece como porta COM, ex.: `COM7`).
2. Abra o arquivo `index.html` (ou a URL do GitHub Pages acima).
3. **1 · Conectar PIN pad** → escolha a porta COM do pinpad na lista do Chrome
   *(deixe o transporte em "Automático" — no PC ele usa serial automaticamente).*
4. **2 · Enviar ao display** para escrever um texto no LCD, ou **3 · Teste completo**.

> Se você usa o app oficial da Gertec (`Teste PPC 800/900`) ou qualquer monitor serial,
> **feche-o antes**: ele segura a porta COM e o navegador não consegue abrir.

---

## O que o teste completo verifica

| # | O que envia | Resposta esperada de um pinpad bom | O que prova |
|---|---|---|---|
| 1 | Frame válido (`MK102` + checksum correto) | `ACK` (`06`) | o pinpad recebe e executa comandos |
| 2 | O mesmo frame com **checksum corrompido** | `NAK` (`15`) | o firmware **valida integridade** — não é um ACK cego |
| 3 | Texto puro, sem enquadramento | *(nenhuma resposta)* | o device ignora ruído/bytes soltos |
| 4 | Frame válido de novo | `ACK` (`06`) | comunicação estável e repetível |

Se os quatro passam, o pinpad está com hardware, firmware, USB/cabo e canal de dados OK.
Se o resultado for `2/4` ou `3/4`, veja **Solução de problemas**.

---

## Botões

| Botão | O que faz |
|---|---|
| **1 · Conectar PIN pad** | Abre o seletor (porta serial no PC, dispositivo USB no Android) |
| **2 · Enviar ao display** | Escreve o texto do campo no LCD do pinpad (comando `MK102`) |
| **Desconectar** | Libera a porta/interface |
| **3 · Teste completo (ACK / NAK)** | Roda os 4 casos acima e dá o veredito |
| Seletor de transporte | `Automático` (serial no PC, USB no celular), ou forçar `Porta serial` / `USB direto` |
| **no USB, listar todos os dispositivos** | Mostra todos os USB (use se o PPC930 não aparecer na lista) |

---

## Protocolo

O formato de frame usado (decodificado do próprio exemplo oficial da Gertec) está documentado em
[PROTOCOLO.md](PROTOCOLO.md):

```
STX | LEN(total) | CMD(5) | 0x1D | DADOS | ETX | XOR(STX..ETX)
```

Exemplo real — escrever `PPC-800` no display:

```
02 11 4D 4B 31 30 32 1D 50 50 43 2D 38 30 30 03 6E
```

---

## Alternativa por linha de comando (Windows)

Se preferir sem navegador, o repositório inclui `testar-pinpad.ps1`:

```powershell
powershell -ExecutionPolicy Bypass -File .\testar-pinpad.ps1 -Port COM7
```

Saída esperada:

```
Comando enviado : PINPAD OK
Resposta        : [06]
RESULTADO: PINPAD OK - comunicacao funcionando (ACK). Verifique o display do equipamento.
```

---

## Solução de problemas

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `Access denied` ao conectar | No **Windows**, o device está preso ao driver da porta COM (`usbser`); o WebUSB exige driver WinUSB | Use o transporte **serial** (é o padrão no PC). WebUSB ali só serve no Android/Linux |
| `...a porta está em uso` / `Failed to open serial port` | Outro programa segurando a COM (app Gertec, monitor serial, IDE) | Feche o outro programa e conecte de novo |
| Lista de portas vazia no PC | Pinpad não enumerado | Veja o Gerenciador de Dispositivos → *Portas (COM e LPT)*; troque o cabo/porta USB |
| No celular o device não aparece | Cabo OTG sem dados, ou falta de energia | Use outro adaptador OTG; se o LCD piscar/apagar, use **hub OTG com fonte** |
| `PINPAD OK` não fecha | WebView de outro app | Tem que ser o **Chrome** (ou Edge/Samsung Internet); WebView Android não tem WebUSB |
| Teste `2/4` ou `3/4` | Ruído na primeira resposta, ou pinpad reiniciando | Desconecte, conecte de novo e rode outra vez; se persistir, verifique alimentação |
| `sem resposta` em tudo | Pinpad em outro modo de operação / sem energia | Confira a alimentação e o modo configurado no equipamento |

### Detalhes que valem saber

- **Baud rate é irrelevante no USB do PPC930**: o device é CDC e respondeu `ACK` em 9600, 19200, 38400 e 115200. O app usa 19200 por padrão.
- **Primeira resposta atrasa**: logo após abrir a porta o pinpad emite alguns bytes de inicialização. O app descarta esse lixo automaticamente (warm-up de 800 ms) — sem isso o primeiro teste dá falso negativo.
- **Sem o pinpad ligado**, o app mostra o erro real da camada de transporte (é assim que se distingue problema de software de problema de hardware).

---

## Verificação

Testado contra hardware real (Gertec PPC930, `USB\VID_1753&PID_C902`, string de produto `PPC930 Pinpad Terminal`):

```
[17:44:53] conectado: serial · USB 1753:C902 · 19200 8N1
[17:45:41] → frame válido             ← ACK (06)      PASSOU
[17:45:42] → checksum corrompido      ← NAK (15)      PASSOU
[17:45:42] → texto puro (sem framing) ← sem resposta  PASSOU
[17:45:43] → frame válido novamente   ← ACK (06)      PASSOU
[17:45:43] resumo: 4/4 testes passaram
```

O caminho WebUSB foi validado até a seleção/permissão do device
(`GERTEC · PPC930 Pinpad Terminal · VID 1753 PID C902`); a abertura da interface USB
depende do Android (no Windows é bloqueada pelo driver da porta COM, como explicado acima).

---

## Estrutura

```
index.html         aplicativo (arquivo único, sem dependências, funciona offline)
testar-pinpad.ps1  testador por linha de comando (Windows)
PROTOCOLO.md       especificação do frame Gertec
```

## Licença

[MIT](LICENSE) © 2026 Gabriel Scursel
