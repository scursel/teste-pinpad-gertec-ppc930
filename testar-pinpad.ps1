<#
  Teste rapido de comunicacao com PIN pad Gertec PPC930 (ou PPC900/910).
  Uso:  powershell -ExecutionPolicy Bypass -File teste-pinpad-ppc930.ps1 -Port COM7
  Mostra "PINPAD OK" no display do equipamento e imprime ACK/NAK.
  Protocolo: STX | LEN(total) | CMD(5) | 0x1D | DADOS | ETX | XOR(STX..ETX)
#>
param(
  [string]$Port   = "COM7",
  [string]$Texto  = "PINPAD OK",
  [int]$Baud      = 19200
)
function New-GertecFrame([string]$text) {
    $body = [System.Collections.Generic.List[byte]]::new()
    $body.AddRange([Text.Encoding]::ASCII.GetBytes("MK102"))   # MK102 = exibe mensagem no display
    $body.Add(0x1D)
    $body.AddRange([Text.Encoding]::ASCII.GetBytes($text))
    $body.Add(0x03)
    $f = [System.Collections.Generic.List[byte]]::new()
    $f.Add(0x02)
    $f.Add([byte](2 + $body.Count + 1))
    $f.AddRange($body)
    $x = 0; foreach ($b in $f) { $x = $x -bxor $b }
    $f.Add([byte]$x)
    return $f.ToArray()
}
if ([System.IO.Ports.SerialPort]::GetPortNames() -notcontains $Port) {
    Write-Host "Porta $Port nao encontrada. Portas atuais: $([System.IO.Ports.SerialPort]::GetPortNames() -join ', ')" -ForegroundColor Red
    exit 1
}
$frame = New-GertecFrame $Texto
$sp = New-Object System.IO.Ports.SerialPort($Port, $Baud, [System.IO.Ports.Parity]::None, 8, [System.IO.Ports.StopBits]::One)
$sp.ReadTimeout = 300; $sp.WriteTimeout = 300
try { $sp.Open() } catch { Write-Host "Falha ao abrir $Port : $($_.Exception.Message)" -ForegroundColor Red; exit 1 }
$sp.DiscardInBuffer()
Start-Sleep -Milliseconds 150
$sp.Write($frame, 0, $frame.Length)
$got = [System.Collections.Generic.List[byte]]::new()
$sw = [Diagnostics.Stopwatch]::StartNew()
while ($sw.ElapsedMilliseconds -lt 2000) {
    if ($sp.BytesToRead -gt 0) {
        $buf = New-Object byte[] $sp.BytesToRead
        [void]$sp.Read($buf, 0, $buf.Length)
        $got.AddRange($buf); $sw.Restart()
    } else { Start-Sleep -Milliseconds 40 }
}
$sp.Close()
$hex = ($got | ForEach-Object { $_.ToString('X2') }) -join ' '
Write-Host "Comando enviado : $Texto"
Write-Host "Resposta        : [$hex]"
if ($got.Count -eq 1 -and $got[0] -eq 0x06) {
    Write-Host "RESULTADO: PINPAD OK - comunicacao funcionando (ACK). Verifique o display do equipamento." -ForegroundColor Green
} elseif ($got.Count -eq 1 -and $got[0] -eq 0x15) {
    Write-Host "RESULTADO: equipamento respondeu NAK - frame rejeitado." -ForegroundColor Yellow
} elseif ($got.Count -eq 0) {
    Write-Host "RESULTADO: sem resposta. Confira a porta COM / cabo / alimentacao." -ForegroundColor Red
} else {
    Write-Host "RESULTADO: resposta inesperada." -ForegroundColor Yellow
}
