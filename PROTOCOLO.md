# Protocolo do PIN pad Gertec PPC930

Documentação do protocolo usado pelas ferramentas deste repositório, decodificada a partir de
software oficial da Gertec e **confirmada no hardware real** (PPC930, firmware `V2.23`).

> Todos os exemplos usam **dados de cartão fictícios/mascarados**.

## Formato do frame

```
+------+---------+----------+---------+---------------+------+------------------+
| STX  |  LEN    |  CMD     | [param] | [0x1D + dados]| ETX  |    CHECKSUM      |
| 0x02 | 1 byte  | 4 ASCII  | ASCII   | opcional      | 0x03 | 1 byte (XOR)     |
+------+---------+----------+---------+---------------+------+------------------+
```

| Campo | Descrição |
|---|---|
| `LEN` | tamanho **total** do frame (de `STX` até o último byte do checksum) |
| `CMD` | comando de 4 caracteres (ex.: `MT03`) |
| `param` | parâmetro opcional de 1 caractere (ex.: `MK10` + `2` = linha do display) |
| dados | opcional, precedido por `0x1D` (ex.: texto do display) |
| `CHECKSUM` | **XOR de todos os bytes de `STX` até `ETX`, inclusive** |

Regra: `LEN = 2 (STX+LEN) + len(CMD) + len(param) + len(dados) + 1 (0x1D, se houver) + 2 (ETX+CHK)`.

### Respostas

O pinpad responde primeiro com um byte de controle e, quando há dados, um frame completo:

| Byte | Significado |
|---|---|
| `06` | `ACK` — comando aceito (pode vir seguido do frame de resposta) |
| `15` | `NAK` — comando rejeitado (frame/param inválido) |
| `04` | `EOT` — fim (ex.: `MS050`) |
| *(nada)* | enquanto o leitor está armado o pinpad **ignora** comandos (não responde) |

O frame de resposta tem a mesma estrutura (`STX | LEN | CMD | payload | ETX | XOR`), mas o
`CMD` costuma ser o **código de resposta** (par do comando enviado).

## Comandos confirmados

| Enviar | Resposta | Função |
|---|---|---|
| `MT10` | `ACK` | alive/ping |
| `MT03` | `MT03` + `série` `0x1F` `modelo` `0x1F` `firmware` | identificação |
| `PP03` | `PP04` + `YY/MM/DD` `HH:MM:SS` | relógio (RTC) |
| `MK10` + linha + `0x1D` + texto | `ACK` | escreve no display |
| `MS05` | `ACK` | **arma o leitor de tarja** |
| *(evento)* | `MS06` + trilhas | **emitido sozinho quando o cartão passa** |
| `SC02` + `0` | `SC03` + `0` `0`/`1` | leitor de chip: `0`=sem cartão, `1`=cartão presente |

### Identificação — `MT03`

```
→ 02 08 4D 54 30 33 03 13                      (STX LEN "MT03" ETX CHK)
← 06 02 2D 4D 54 30 33 37 32 30 30 30 33 32 32 30 31 30 30 37 39 32 32 1F 50 50 43 39 33 30 20 1F 56 32 2E 32 33 20 32 31 30 32 32 33 03 0D
  ACK | "MT03" | 7200032201007922 | 0x1F | "PPC930 " | 0x1F | "V2.23 210223"
```

Campos separados por `0x1F`: **número de série**, modelo, versão de firmware.

### Display — `MK10`

```
→ 02 11 4D 4B 31 30 32 1D 50 50 43 2D 38 30 30 03 6E
  STX LEN "MK10" "2" 0x1D "PPC-800" ETX CHK      → o "2" é a linha; o texto vem após 0x1D
← 06
```

### Tarja magnética — `MS05` / `MS06`

O leitor funciona por **evento**, não por polling:

1. Envie `MS05` (sem parâmetro) → o pinpad responde `ACK` e **arma** o leitor.
2. Enquanto armado, o pinpad **ignora outros comandos** (inclusive outro `MS05`).
3. Quando o cartão passa, o pinpad **emite espontaneamente** um frame `MS06` — **sem o `ACK` na frente**:

```
← 02 80 4D 53 30 36 1F 31 25 42 ... 1F 32 3B 35 ... 03 9A
  STX LEN "MS06" | 0x1F | '1' <TRILHA 1> | 0x1F | '2' <TRILHA 2> | ETX | CHK
```

Exemplo com dados fictícios:

```
1F 31 25 42 2A2A2A2A2A2A2A2A2A2A39383533 5E 4E4F4D452F534F4252454E4F4D45 5E ...
        └ '1' └ '%B' + PAN mascarado + '^' + NOME/SOBRENOME + '^' + validade/serviço
1F 32 3B 35 ...                                                       └ '2' + ';' + trilha 2
```

- Trilha 1 começa com `%` (formato `%B<PAN>^<NOME>^<validade><serviço><dados>?`)
- Trilha 2 começa com `;` (formato `;<PAN>=<validade><serviço><dados>?`)
- Cartão com chip também pode trazer trilha 3 (`T3`) — no PPC930 testado só T1/T2 apareceram.

### Leitor de chip — `SC02` / `SC03`

```
→ 02 09 53 43 30 32 30 03 2A          (STX LEN "SC02" "0" ETX CHK)
← 06 02 0A 53 43 30 33 30 31 03 19    ACK | "SC03" | "01"   → 0 = parâmetro ecoado, 1 = CARTÃO PRESENTE
← 06 02 0A 53 43 30 33 30 30 03 18    ACK | "SC03" | "00"   → nenhum cartão inserido
```

O último dígito é o status de presença. Parâmetros `0`–`5` respondem; `6`–`9` retornam `NAK`.
A leitura do ATR/conteúdo do chip (APDU) não foi identificada neste firmware — a tabela da DLL
oficial da Gertec cita `PPC_CheckSMC` (este comando) e `PPC_ResetSMC` (`SC07`/`SC08`).

## Notas de bancada (todas observadas no hardware)

- **Warm-up**: o primeiro comando após abrir a porta costuma ser descartado/perdido. Envie um
  comando inofensivo (`MT10`) e aguarde ~800 ms antes da sequência real.
- **Resposta atrasada**: respostas podem chegar depois do timeout do comando anterior. Descarte
  o buffer antes de cada comando e valide o `CMD` esperado na resposta (senão o `PP04` de um
  `PP03` é lido como resposta do comando seguinte).
- **Leitor armado ignora comandos**: depois de `MS05`, o pinpad não responde nada até o cartão
  passar (ou o estado expirar). Não faça polling de `MS05` e **não inclua `MS05` em sequências
  automáticas** — ele deixa o equipamento surdo para os comandos seguintes. Por isso o
  "Teste completo" do app não usa `MS05` (a tarja tem botão próprio).
- **Buffer é one-shot**: a leitura da tarja fica disponível uma vez; depois precisa passar o
  cartão de novo.
- **Baud rate**: o USB do PPC930 é CDC e respondeu `ACK` em `9600`, `19200`, `38400` e `115200`.
  As ferramentas usam `19200 8N1`.

## Como isto foi obtido

1. O frame base veio do binário de exemplo oficial (`Ex_Comunic_PPC910_Linux/exemplo`), com os
   imediatos `MOV BYTE PTR [EBP+disp], imm8` remontados → `02 11 "MK102" 1D "PPC-800" 03 6E`.
2. A tabela de comandos (`MK10`, `MT03`, `MS01`, `MS05`/`MS06`, `SC02`/`SC03`, `PP03`/`PP04`, …)
   foi extraída do `sagat.dll` (biblioteca oficial de teste que acompanha o pacote SAGAT da Gertec).
3. Cada comando foi validado contra o PPC930 real, com os bytes de resposta conferidos.
4. O fluxo de evento da tarja (`MS05` arma → `MS06` espontâneo) foi confirmado capturando a porta
   durante a passada do cartão.
