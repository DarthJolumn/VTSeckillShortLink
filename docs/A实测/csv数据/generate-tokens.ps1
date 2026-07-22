# generate-tokens.ps1
# 批量登录 reg-users.csv 中的用户，生成带 token 的 CSV 文件
# 用法: .\generate-tokens.ps1 [-Count 100]

param(
    [int]$Count = 100,
    [string]$BaseUrl = "http://localhost:8080"
)

$regFile = "$PSScriptRoot\reg-users.csv"
$seckillFile = "$PSScriptRoot\seckill-users.csv"
$lbFile = "$PSScriptRoot\lb-users.csv"
$wsFile = "$PSScriptRoot\ws-users.csv"

# 清空输出文件（不写表头，变量名由 JMeter CSV Data Set Config 管理）
$null | Set-Content $seckillFile -Encoding UTF8
$null | Set-Content $lbFile -Encoding UTF8
$null | Set-Content $wsFile -Encoding UTF8

$eventTypes = @("WATCH","WATCH","WATCH","WATCH","WATCH","WATCH","WATCH","LIKE","LIKE","GIFT")

$lines = Get-Content $regFile | Select-Object -First $Count
$userId = 1

foreach ($line in $lines) {
    $parts = $line.Split(",")
    $username = $parts[0]
    $password = $parts[1]
    $deviceId = $parts[2]

    try {
        $body = @{ username = $username; password = $password } | ConvertTo-Json
        $resp = Invoke-RestMethod -Uri "$BaseUrl/auth/login" `
            -Method POST `
            -ContentType "application/json" `
            -Headers @{ "X-Device-Id" = $deviceId } `
            -Body $body

        $token = $resp.data.accessToken
        if ($token) {
            "$userId,$token,$deviceId" | Add-Content $seckillFile -Encoding UTF8

            $evt = $eventTypes | Get-Random
            "$userId,$token,$evt" | Add-Content $lbFile -Encoding UTF8

            "$userId,$token,$deviceId,1" | Add-Content $wsFile -Encoding UTF8

            Write-Host "[$userId/$Count] $username OK" -ForegroundColor Green
        } else {
            Write-Host "[$userId/$Count] $username FAIL (no token)" -ForegroundColor Red
        }
    } catch {
        Write-Host "[$userId/$Count] $username ERROR: $($_.Exception.Message)" -ForegroundColor Red
    }

    $userId++
}

Write-Host "`nDone! Generated $Count tokens." -ForegroundColor Cyan
Write-Host "  seckill-users.csv : $seckillFile"
Write-Host "  lb-users.csv      : $lbFile"
Write-Host "  ws-users.csv      : $wsFile"
