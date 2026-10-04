# Changelog

## v1.2 — 2026-10-04

Correções da segunda auditoria ([`docs/plans/2026-10-04-auditoria-v1.1.md`](docs/plans/2026-10-04-auditoria-v1.1.md)).

### Segurança
- **Número do cartão não vaza mais no log.** App Android: passar o cartão logo depois de tocar em
  "Ler tarja" fazia o log mostrar as trilhas sem máscara. Página web: um cartão passado depois do
  fim da espera aparecia em hex no log do comando seguinte. Agora nenhum programa registra bytes
  brutos que possam ser do cartão.

### Correções
- Ler tarja: sem resposta ao `MS05` não é mais tratado como falha definitiva — o leitor pode já
  estar armado de uma tentativa anterior (ele ignora um segundo `MS05`). O programa avisa e segue
  esperando o cartão (web, Android, PowerShell).
- App Android: "passe o cartão" só aparece depois que o pinpad confirma o `MS05`; bytes que chegam
  junto com o ACK não se perdem.
- App Android: o teste completo manda a linha do display no `MK10` (igual à web e ao PowerShell);
  desconectar no meio do teste não sobrescreve mais o status.
- PowerShell `-Info`: série, modelo e firmware voltam a sair separados.
- PowerShell `-Tarja`: uma resposta atrasada na fila não impede mais de reconhecer o cartão.
- PowerShell: a porta COM é sempre fechada, mesmo com erro; o texto do display é limpo (sem acento,
  ASCII, 16 caracteres).
- Página web: um `0x02` solto antes do frame não atrasa mais a leitura; a escolha manual do
  transporte (serial/USB) é respeitada ao reconectar.

### Atenção
- O APK continua sendo publicado com a chave de **debug** do GitHub Actions enquanto os secrets
  de assinatura não forem cadastrados — cada versão nova exige desinstalar a anterior.

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
