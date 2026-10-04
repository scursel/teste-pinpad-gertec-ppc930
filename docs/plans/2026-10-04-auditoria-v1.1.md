# Auditoria v1.1 (2026-10-04)

Escopo: `android/` (Kotlin), `index.html`, `testar-pinpad.ps1`, CI e `dist/*.apk`, no commit `edb485a`.
Nada foi alterado no código; este arquivo só registra os achados.

Verificado: CI `android` verde no `main` (testes JVM + build). Não foi possível rodar no hardware nem
rodar o PowerShell (o container não tem `pwsh` nem Android SDK) — os achados vêm da leitura do código.

## Achados

| # | Gravidade | Onde | Problema | Cenário |
|---|---|---|---|---|
| A1 | **Alta** (PAN vaza) | `MainActivity.kt:259-261` + `comando()` `:211-222` | O app mostra "PASSE O CARTÃO AGORA" **antes** de enviar o `MS05`, e o `MS05` passa por `comando()` sem `espera`. Se o cartão passar nos ~1,5 s em que `comando()` ainda escuta, ele pega o `MS06` como "resposta" e **escreve no log o hex e o ASCII das trilhas, sem máscara**. Depois `lerTarja` descarta esse frame e fica esperando outro `MS06` por até 270 s. | Toca em "Ler tarja" e passa o cartão logo em seguida. |
| A2 | **Alta** (PAN vaza) | `index.html` `drenar()` e `comando()` ("sem resposta [hex]") | Depois de 270 s sem cartão o leitor **continua armado** (o próprio app avisa). Se o cartão passar depois disso, o `MS06` fica na fila e o próximo comando (chip, info, monitor) chama `drenar()`, que registra no log `descartando N byte(s) pendentes: <hex>` — **trilhas inteiras, sem máscara**. | Ler tarja → espera acabar → passa o cartão → clica em qualquer outro botão. |
| A3 | Média | `lerTarja` nos três programas | Correção da v1.1 ("sem resposta ao `MS05` = falha") dá **falso negativo** quando o leitor já está armado de uma tentativa anterior: o pinpad ignora o segundo `MS05` (PROTOCOLO.md, linha 76), o app diz "leitor não armado" e para de escutar; o cartão passado em seguida é perdido (e cai no A2 na página web). | Ler tarja → deixa expirar → Ler tarja de novo. |
| A4 | Média | `testar-pinpad.ps1:98` | `-Info`: a função `Ascii` troca `0x1F` por `.` **antes** do `-split [char]0x1F`, então o split nunca acontece: `serie` recebe a string inteira e `modelo`/`firmware` saem vazios. A página web já corrige isso (comentário "separa os campos ANTES de converter"). | `.\testar-pinpad.ps1 -Port COM7 -Info` |
| A5 | Média | `.github/workflows/android.yml:306-310`, `dist/teste-pinpad-ppc930.apk` | Sem os secrets de assinatura, o Release publica o **APK de debug** — o de `dist/` é `CN=Android Debug`, criado em 2026-09-25 no runner. A chave debug do runner é **nova a cada execução**, então **toda** versão futura exigirá desinstalar a anterior (não só a v1.1, como diz o CHANGELOG), e o APK é `debuggable`. | Próximo Release sem secrets. |
| A6 | Baixa | `MainActivity.kt:400` | Teste completo do Android manda `MK10` **sem** a linha (`"2"`); a web (`index.html`) e o PowerShell mandam `MK10` + `"2"`. Dados insuficientes para saber se o PPC930 aceita sem o parâmetro; o protocolo documentado sempre tem a linha. | — |
| A7 | Baixa | `testar-pinpad.ps1:131-134` | No `-Tarja`, `Parse-Frame` sempre devolve o **primeiro** frame do buffer e o buffer nunca é aparado. Se chegar antes um frame que não é `MS06` (resposta atrasada, checksum ruim), o `MS06` seguinte nunca é reconhecido. | Resposta atrasada na fila antes do cartão. |
| A8 | Baixa | `testar-pinpad.ps1` (geral) | A porta não é fechada em `try/finally`: uma exceção (ex.: `WriteTimeout` de 250 ms) deixa a COM presa até fechar a janela do PowerShell. `$Texto` também não tem limite de tamanho/ASCII (>250 chars quebra no cast `[byte]`). | Rodar de novo na mesma janela após um erro. |
| A9 | Baixa | `index.html` `conectar()` | `window.__transporteOk` ignora a escolha feita depois no seletor de transporte (se o serial funcionou uma vez, escolher "USB" não tem efeito até recarregar a página). | — |
| A10 | Baixa | `index.html` `parseFrame`/`esperarFrame`, `MainActivity.acharFrame` | Um `0x02` solto seguido de um tamanho grande faz o código esperar bytes que nunca vêm, e o frame real logo depois não é visto até o timeout. Web ainda não tem o filtro `len < 8` que o Android ganhou na v1.1. | Lixo na linha antes do frame (raro). |
| A11 | Baixa | `MainActivity.kt` `testeCompleto` | Ao desconectar no meio do teste, o laço continua, registra FALHOU nos itens restantes e sobrescreve o status "desconectado" com "FALHOU". | — |

## O que a v1.1 corrigiu e foi confirmado na leitura

- Máscara de PAN aplicada nas trilhas exibidas (web, Android, PowerShell) — mas veja A1/A2 (log).
- `parseFrame` do Android rejeita `LEN < 8`; `abrir()` só aceita VID Gertec e fecha a conexão anterior.
- Timeout real no WebUSB (`usbPend` + corrida com temporizador).
- Portão de I/O (`PortaoIo`/`exclusivo`) evita o monitor de chip roubar bytes.
- Assinatura release lida de variáveis de ambiente; `*.jks` no `.gitignore`.

## Ordem sugerida de correção

1. A1 e A2 (vazamento de PAN no log): nunca registrar hex/ASCII de frame `MS06`; no Android mostrar
   "passe o cartão" só depois do ACK e ler o ACK do `MS05` sem consumir frames.
2. A3: sem resposta ao `MS05` → avisar "o leitor pode já estar armado" e seguir escutando o `MS06`.
3. A4 (uma linha: split antes de converter).
4. A5: gerar a chave release uma vez e cadastrar os 4 secrets (passo do dono do repositório).
5. Demais itens.
