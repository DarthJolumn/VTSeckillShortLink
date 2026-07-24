param(
    [int]$Count = 1000
)

# JWT 配置（与 application.yml 一致）
$secret = "bGl2ZW1hbGwtZGV2LXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLTEyMzQ1Njc4OTA="
$secretBytes = [Convert]::FromBase64String($secret)

# HS384 HMAC
$hmac = [System.Security.Cryptography.HMACSHA384]::new($secretBytes)

function Base64UrlEncode($bytes) {
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function New-Jwt($userId, $role, $ttlSeconds) {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $exp = $now + $ttlSeconds

    $header = '{"alg":"HS384","typ":"JWT"}'
    $payload = '{"sub":"' + $userId + '","role":' + $role + ',"iat":' + $now + ',"exp":' + $exp + '}'

    $b64header = Base64UrlEncode ([Text.Encoding]::UTF8.GetBytes($header))
    $b64payload = Base64UrlEncode ([Text.Encoding]::UTF8.GetBytes($payload))

    $signInput = $b64header + "." + $b64payload
    $signInputBytes = [Text.Encoding]::UTF8.GetBytes($signInput)

    $sig = $hmac.ComputeHash($signInputBytes)
    $b64sig = Base64UrlEncode $sig

    return $b64header + "." + $b64payload + "." + $b64sig
}

# reg-users.csv 读取 deviceId
$regFile = "$PSScriptRoot\reg-users.csv"
$outFile = "$PSScriptRoot\seckill-users.csv"

$lines = Get-Content $regFile | Select-Object -First $Count
$null | Set-Content $outFile -Encoding UTF8

$ttl = 30 * 24 * 3600  # 30 天

$userId = 1
foreach ($line in $lines) {
    $parts = $line.Split(",")
    $deviceId = $parts[2]
    $token = New-Jwt $userId 1 $ttl
    "$userId,$token,$deviceId" | Add-Content $outFile -Encoding UTF8
    $userId++

    if ($userId % 100 -eq 0) {
        Write-Host "已生成 $userId / $Count" -ForegroundColor Cyan
    }
}

Write-Host "完成！生成 $Count 个 token，过期时间 30 天" -ForegroundColor Green
