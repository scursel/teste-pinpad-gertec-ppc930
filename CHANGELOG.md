# Changelog

## v1.1 — 2026-09-25

Correções da auditoria do repositório (plano em [`docs/plans/2026-09-25-correcoes-auditoria.md`](docs/plans/2026-09-25-correcoes-auditoria.md)).

### Segurança
- **PAN mascarado** nas trilhas da tarja, nos três programas (web, Android, PowerShell): toda
  sequência de 13+ dígitos aparece como `6 primeiros + *** + 4 últimos` (ex.: `411111******1111`).
- O payload bruto (hex) do evento `MS06` não é mais exibido nem registrado no log
  (no PowerShell, só com `-Completo`).
- App Android: só abre dispositivos Gertec (VID `1753`) — antes, sem pinpad, reivindicava à força
  o primeiro USB que encontrasse.
- Assinatura release configurável por variáveis de ambiente / secrets do GitHub (a chave nunca vai
  para o repositório; `*.jks` e `*.keystore` estão no `.gitignore`).

### Correções
- Ler tarja: **sem resposta ao `MS05`** agora é falha ("leitor não armado"). Antes o app dizia
  "armado" e esperava o cartão por até 270 s à toa.
- Página web, modo WebUSB: `bulkTransferIn` não aceita timeout, então uma leitura sem dados travava
  para sempre (inclusive antes de cada comando). Agora a leitura corre contra um temporizador sem
  perder os bytes que chegam depois.
- App Android: `parseFrame` não trava mais com um `0x02` solto seguido de tamanho pequeno.
- App Android: tocar em Conectar duas vezes não cria mais uma segunda thread de leitura; o receiver de
  permissão USB é removido; a caixa "monitorar chip" é desmarcada ao reconectar; botões avisam
  "conecte primeiro".
- Display: texto sem acento, só ASCII e limitado a 16 caracteres (web e Android); frames maiores que
  255 bytes são recusados.
- PowerShell: a máscara usava um recurso que só existe no PowerShell 6.1+; no Windows PowerShell 5.1
  (o do README) a saída saía estragada. Agora funciona nos dois.

### Infraestrutura
- GitHub Actions (`.github/workflows/android.yml`): testes JVM + build do APK a cada push; uma tag
  `v*` publica o APK num Release.
- 17 testes JVM (eram 12).

### Atenção ao atualizar o APK
O APK desta versão foi assinado com uma chave diferente da v1.0. **Desinstale a versão anterior**
antes de instalar, senão o Android recusa ("conflito de pacote").

## v1.0 — 2026-09-18
- Primeira versão: app web (Web Serial/WebUSB), app Android nativo, script PowerShell e
  documentação do protocolo.
