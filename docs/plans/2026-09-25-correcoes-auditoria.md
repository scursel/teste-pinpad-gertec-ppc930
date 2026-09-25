# Plano: correções da auditoria (2026-09-25)

Objetivo: corrigir os achados da auditoria sem mudar o comportamento que já funciona no hardware.

## Regra comum de mascaramento (PAN)

Toda sequência de **13 ou mais dígitos** nas trilhas é mostrada como
`6 primeiros + '*' repetido + 4 últimos` (ex.: `411111******1111`). Vale para os três programas.
Nenhum programa mostra o payload bruto (hex) do evento `MS06`.

## Tarefas

| # | Arquivo(s) | O quê | Executor |
|---|---|---|---|
| T1 | `index.html` | mascarar trilhas; não mostrar hex do MS06; MS05 sem resposta = falha; timeout real no WebUSB (`bulkTransferIn` não aceita timeout); limitar texto do display (32 chars, só ASCII) | modelo intermediário |
| T2 | `testar-pinpad.ps1` | não imprimir hex do MS06 (só com `-Completo`); MS05 sem resposta = falha; `Mascarar` compatível com Windows PowerShell 5.1 | modelo barato |
| T3 | `android/.../*.kt` + testes | `mascararPan()` + testes; `parseFrame` rejeita LEN < 8; MS05 sem resposta = falha; caixa do monitor sincronizada; limite do display; `abrir()` fecha conexão anterior, remove o receiver e só aceita VID Gertec | modelo intermediário |
| T4 | `.github/workflows/android.yml`, `android/app/build.gradle.kts`, `android/gradlew` | CI rodando os testes JVM; assinatura release lida de variáveis de ambiente; bit executável do gradlew | coordenador |
| T5 | todos | revisão do diff, verificação, commit e push | coordenador |

## Verificação

- JS: funções puras testadas com `node`.
- Kotlin: `./gradlew testDebugUnitTest` no GitHub Actions (o container não tem Android SDK).
- PowerShell: sem `pwsh` no container — revisão manual.

## Fora do escopo

- Gerar/commitar a chave de assinatura release e reconstruir `dist/*.apk` (precisa da chave do dono e do Android SDK).
