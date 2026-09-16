# Protocolo do PIN pad Gertec PPC930

Documentação do enquadramento (framing) usado pelo PPC930 no modo "pinpad", decodificado a partir
do software oficial de teste da Gertec (`Gertec PPC900 - Software Teste Comunicação Linux.zip` →
binário `exemplo`) e confirmado no hardware real.

## Formato do frame

```
+------+---------+----------+--------+-------------+------+------------------+
| STX  |  LEN    |  CMD     | 0x1D   |   DADOS     | ETX  |    CHECKSUM      |
| 0x02 | 1 byte  | 5 ASCII  | fixo   | N bytes     | 0x03 | 1 byte (XOR)     |
+------+---------+----------+--------+-------------+------+------------------+
```

| Campo | Tamanho | Descrição |
|---|---|---|
| `STX` | 1 | `0x02`, início do frame |
| `LEN` | 1 | tamanho **total** do frame (de `STX` até o último byte do checksum) |
| `CMD` | 5 | comando em ASCII maiúsculo, ex.: `MK102` |
| separador | 1 | `0x1D` (constante antes dos dados) |
| `DADOS` | N | parâmetros do comando (texto ASCII) |
| `ETX` | 1 | `0x03`, fim do frame |
| `CHECKSUM` | 1 | **XOR de todos os bytes de `STX` até `ETX`, inclusive** |

Regra de `LEN`:

```
LEN = 1 (STX) + 1 (LEN) + 5 (CMD) + 1 (0x1D) + N (DADOS) + 1 (ETX) + 1 (CHECKSUM)
    = N + 10
```

## Comando `MK102` — escrever no display

Envia um texto para o LCD do pinpad. Exemplo, `PPC-800`:

```
02 11 4D 4B 31 30 32 1D 50 50 43 2D 38 30 30 03 6E
│  │  └──────┬──────┘ │  └──────┬──────┘ │  └─ checksum
│  │         │        │         │        └──── ETX
│  │         │        │         └───────────── "PPC-800"
│  │         │        └─────────────────────── separador 0x1D
│  │         └──────────────────────────────── comando "MK102"
│  └────────────────────────────────────────── LEN = 0x11 = 17 bytes (total)
└───────────────────────────────────────────── STX
```

Conferindo o checksum (`XOR` de `02` até `03`):

```
02^11 = 13   ^4D = 5E   ^4B = 15   ^31 = 24   ^30 = 14   ^32 = 26
^1D = 3B     ^50 = 6B   ^50 = 3B   ^43 = 78   ^2D = 55   ^38 = 6D
^30 = 5D     ^30 = 6D   ^03 = 6E  →  checksum = 0x6E ✔
```

## Respostas

| Resposta | Significado |
|---|---|
| `06` | `ACK` — comando aceito e executado |
| `15` | `NAK` — frame rejeitado (o firmware confere o checksum) |
| *(nada)* | bytes sem enquadramento são ignorados |

Comportamento confirmado no hardware (frames gerados pelo `index.html` e pelo `testar-pinpad.ps1`):

| Enviado | Recebido | Interpretação |
|---|---|---|
| `02 12 MK102 1D "TESTE OK" 03 4C` | `06` | aceito |
| mesmo frame com último byte `4C→B3` | `15` | rejeitado — **checksum é validado pelo device** |
| `"HELLO WORLD\r\n"` cru | *(vazio)* | ignorado |
| `02 13 MK102 1D "PPC930 OK" 03 67` | `06` | aceito |

## Notas de bancada

- **Baud rate**: o PPC930 em USB é um dispositivo CDC. Respondeu `ACK` em `9600`, `19200`,
  `38400` e `115200` — a velocidade de linha não altera o comportamento. As ferramentas usam `19200 8N1`.
- **Ruído de inicialização**: logo após a abertura da porta o pinpad emite alguns bytes
  (observado: 16 bytes) antes de estar pronto para o primeiro comando; a primeira resposta
  também pode atrasar ~1 s. Descarte o buffer e aguarde ~800 ms após conectar.
- **Identificação do device**: `VID 0x1753` / `PID 0xC902`, string de produto
  `PPC930 Pinpad Terminal`, fabricante `GERTEC`, driver padrão `usbser` (CDC-ACM, porta COM).

## Como este documento foi obtido

1. O pacote oficial de teste da Gertec (`.zip`) inclui um exemplo para Linux (`Ex_Comunic_PPC910_Linux/exemplo`).
2. Esse binário constrói o frame byte a byte na pilha; os imediatos `MOV BYTE PTR [EBP+disp], imm8`
   foram extraídos e remontados, revelando `02 11 MK102 1D "PPC-800" 03 6E`.
3. O frame foi então reproduzido contra o hardware real, confirmando `ACK`/`NAK` conforme a
   validade do checksum.
