<#
  Testador de PIN pad Gertec PPC930 por linha de comando (Windows).

  Uso:
    .\testar-pinpad.ps1 -Port COM7                  # teste básico (display + ACK)
    .\testar-pinpad.ps1 -Port COM7 -Chip            # leitor de chip (presença)
    .\testar-pinpad.ps1 -Port COM7 -Tarja           # leitor de tarja (arma e espera o cartão)
    .\testar-pinpad.ps1 -Port COM7 -Info            # série, modelo, firmware, RTC

  Protocolo: STX | LEN | CMD(4) | [param] | [0x1D + dados] | ETX | XOR(STX..ETX)
  Veja PROTOCOLO.md. Importante: use UMA ponta por vez na porta COM.
#>
param(
  [string]$Port  = "COM7",
  [int]$Baud     = 19200,
  [string]$Texto = "PINPAD OK",
  [switch]$Tarja,
  [switch]$Chip,
  [switch]$Info,
  [int]$Janela   = 60,          # segundos de espera do cartão no modo -Tarja
  [switch]$Completo             # mostra PAN completo (padrão: mascarado)
)

$STX = 0x02; $ETX = 0x03; $SEP = 0x1D; $FSEP = 0x1F; $ACK = 0x06; $NAK = 0x15; $EOT = 0x04

function New-Frame([string]$cmd, [string]$param, [string]$dados) {
  $body = [System.Collections.Generic.List[byte]]::new()
  $body.AddRange([Text.Encoding]::ASCII.GetBytes($cmd))
  if ($param) { $body.AddRange([Text.Encoding]::ASCII.GetBytes($param)) }
  if ($null -ne $dados) { $body.Add($SEP); $body.AddRange([Text.Encoding]::ASCII.GetBytes($dados)) }
  $f = [System.Collections.Generic.List[byte]]::new()
  $f.Add($STX); $f.Add(0); $f.AddRange($body); $f.Add($ETX)
  $f[1] = [byte]($f.Count + 1)
  $x = 0; foreach ($b in $f) { $x = $x -bxor $b }
  $f.Add([byte]$x)
  return $f.ToArray()
}
function Hex([byte[]]$b) { ($b | ForEach-Object { $_.ToString('X2') }) -join ' ' }
function Ascii([byte[]]$b) { -join ($b | ForEach-Object { if ($_ -ge 32 -and $_ -lt 127) { [char]$_ } else { '.' } }) }
function Mascarar([string]$s) { if ($Completo) { return $s } ; return ($s -replace '\d{6,}', { param($m) $v = $m.Value; $v.Substring(0,4) + ('*' * ($v.Length - 8)) + $v.Substring($v.Length - 4) }) }

function Read-Chunks($sp, $ms, [ref]$acc) {
  $sw = [Diagnostics.Stopwatch]::StartNew()
  while ($sw.ElapsedMilliseconds -lt $ms) {
    $n = $sp.BytesToRead
    if ($n -gt 0) { $buf = New-Object byte[] $n; [void]$sp.Read($buf, 0, $n); $acc.Value.AddRange($buf); $sw.Restart() }
    else { Start-Sleep -Milliseconds 25 }
  }
}
function Parse-Frame([byte[]]$bytes) {
  # procura um frame completo a partir do STX
  for ($i = 0; $i -lt $bytes.Length; $i++) {
    if ($bytes[$i] -ne $STX) { continue }
    if ($i + 2 -gt $bytes.Length) { break }
    $len = $bytes[$i + 1]
    if ($i + $len -gt $bytes.Length) { break }
    $f = $bytes[$i..($i + $len - 1)]
    $x = 0; for ($k = 0; $k -lt $f.Length - 1; $k++) { $x = $x -bxor $f[$k] }
    if ($x -ne $f[$f.Length - 1]) { return @{ cmd = ""; payload = @(); bad = $true } }
    $cmd = [Text.Encoding]::ASCII.GetString($f[2..5])
    $payload = if ($len -gt 8) { $f[6..($len - 3)] } else { @() }
    return @{ cmd = $cmd; payload = $payload; bad = $false }
  }
  return $null
}

# ---------- porta ----------
if ([System.IO.Ports.SerialPort]::GetPortNames() -notcontains $Port) {
  Write-Host "Porta $Port nao encontrada. Portas: $([System.IO.Ports.SerialPort]::GetPortNames() -join ', ')" -ForegroundColor Red; exit 1
}
$sp = New-Object System.IO.Ports.SerialPort($Port, $Baud, [System.IO.Ports.Parity]::None, 8, [System.IO.Ports.StopBits]::One)
$sp.ReadTimeout = 250; $sp.WriteTimeout = 250
try { $sp.Open() } catch { Write-Host "Falha ao abrir $Port : $($_.Exception.Message)" -ForegroundColor Red; exit 1 }
Start-Sleep -Milliseconds 900
$sp.DiscardInBuffer()
# warm-up (o primeiro comando depois de abrir costuma ser descartado)
$sp.Write((New-Frame "MT10" $null $null), 0, (New-Frame "MT10" $null $null).Length)
$tmp = [System.Collections.Generic.List[byte]]::new(); Read-Chunks $sp 600 ([ref]$tmp)
$sp.DiscardInBuffer()

function Enviar([string]$cmd, [string]$param, [string]$dados, [int]$ms) {
  $f = New-Frame $cmd $param $dados
  Write-Host ("-> " + $cmd + $(if ($param) { $param }) + $(if ($null -ne $dados) { ' "' + $dados + '"' }) + "  " + (Hex $f)) -ForegroundColor Cyan
  $sp.Write($f, 0, $f.Length)
  $acc = [System.Collections.Generic.List[byte]]::new()
  Read-Chunks $sp $ms ([ref]$acc)
  return , $acc.ToArray()
}

if ($Info) {
  $r = Enviar "MT03" $null $null 2000
  $fr = Parse-Frame $r
  if ($fr -and $fr.cmd -eq "MT03") {
    $campos = (Ascii $fr.payload) -split [char]$FSEP | Where-Object { $_ }
    Write-Host ("   serie=" + $campos[0] + "  modelo=" + $campos[1] + "  firmware=" + $campos[2]) -ForegroundColor Green
  } else { Write-Host "   sem resposta de identificacao" -ForegroundColor Yellow }
  $r = Enviar "PP03" $null $null 2000
  $fr = Parse-Frame $r
  if ($fr) { Write-Host ("   rtc=" + (Ascii $fr.payload).Replace("PP04","")) -ForegroundColor Green }
  $sp.Close(); exit 0
}

if ($Chip) {
  $r = Enviar "SC02" "0" $null 1500
  $fr = Parse-Frame $r
  if ($fr -and $fr.cmd -eq "SC03") {
    $s = Ascii $fr.payload
    if ($s.Substring($s.Length - 1) -eq "1") { Write-Host "RESULTADO: CHIP PRESENTE (cartao inserido)" -ForegroundColor Green }
    else { Write-Host "RESULTADO: nenhum cartao de chip inserido" -ForegroundColor Yellow }
  } elseif ($r.Length -and $r[0] -eq $NAK) { Write-Host "RESULTADO: leitor de chip recusou o comando (NAK)" -ForegroundColor Red }
  else { Write-Host "RESULTADO: sem resposta do leitor de chip" -ForegroundColor Red }
  $sp.Close(); exit 0
}

if ($Tarja) {
  $f = New-Frame "MS05" $null $null
  Write-Host ("-> MS05 (arma o leitor de tarja)  " + (Hex $f)) -ForegroundColor Cyan
  $sp.Write($f, 0, $f.Length)
  $acc = [System.Collections.Generic.List[byte]]::new(); Read-Chunks $sp 1500 ([ref]$acc)
  if ($acc.Count -and $acc[0] -eq $NAK) { Write-Host "   leitor recusou armar (NAK)" -ForegroundColor Red; $sp.Close(); exit 1 }
  Write-Host "   leitor ARMADO - passe o cartao na trilha agora" -ForegroundColor Yellow
  $sw = [Diagnostics.Stopwatch]::StartNew()
  $buf = [System.Collections.Generic.List[byte]]::new()
  $fr = $null
  while ($sw.ElapsedMilliseconds -lt ($Janela * 1000)) {
    $chunk = [System.Collections.Generic.List[byte]]::new(); Read-Chunks $sp 400 ([ref]$chunk)
    if ($chunk.Count) { $buf.AddRange($chunk); $fr = Parse-Frame $buf.ToArray(); if ($fr -and $fr.cmd -eq "MS06") { break } }
  }
  if ($fr -and $fr.cmd -eq "MS06" -and $fr.payload.Count -gt 6) {
    Write-Host ("   EVENTO MS06: " + (Hex $fr.payload)) -ForegroundColor Green
    $partes = @(); $cur = @()
    foreach ($b in $fr.payload) { if ($b -eq $FSEP) { if ($cur.Count) { $partes += , $cur }; $cur = @() } else { $cur += $b } }
    if ($cur.Count) { $partes += , $cur }
    foreach ($p in $partes) {
      $n = [char]$p[0]; $txt = Ascii ([byte[]]$p[1..($p.Count - 1)])
      Write-Host ("   Trilha " + $n + ": " + (Mascarar $txt)) -ForegroundColor Green
    }
    Write-Host "RESULTADO: TARJA LIDA com sucesso" -ForegroundColor Green
  } else {
    Write-Host "RESULTADO: nenhum cartao passou durante a janela (o leitor continua armado)" -ForegroundColor Yellow
  }
  $sp.Close(); exit 0
}

# padrao: display + ACK
$f = New-Frame "MK10" "2" $Texto
Write-Host ("-> MK10 (display)  " + (Hex $f)) -ForegroundColor Cyan
$sp.Write($f, 0, $f.Length)
$acc = [System.Collections.Generic.List[byte]]::new(); Read-Chunks $sp 2000 ([ref]$acc)
$ack = if ($acc.Count) { $acc[0] } else { $null }
Write-Host ("<- resposta: " + (Hex $acc.ToArray())) -ForegroundColor Gray
if ($ack -eq $ACK) {
  Write-Host "RESULTADO: PINPAD OK - comunicacao funcionando (ACK). Verifique o display do equipamento." -ForegroundColor Green
} elseif ($ack -eq $NAK) {
  Write-Host "RESULTADO: equipamento respondeu NAK - frame rejeitado." -ForegroundColor Yellow
} elseif ($null -eq $ack) {
  Write-Host "RESULTADO: sem resposta. Confira a porta COM / cabo / se a aplicacao esta rodando (tela nao pode estar em 'Program Manager')." -ForegroundColor Red
} else {
  Write-Host "RESULTADO: resposta inesperada." -ForegroundColor Yellow
}
$sp.Close()
