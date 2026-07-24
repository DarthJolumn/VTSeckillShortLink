param(
    [int]$Count = 100000
)

$secret = "bGl2ZW1hbGwtZGV2LXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLTEyMzQ1Njc4OTA="
$secretBytes = [Convert]::FromBase64String($secret)
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
    $sig = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($signInput))
    $b64sig = Base64UrlEncode $sig
    return $b64header + "." + $b64payload + "." + $b64sig
}

$outFile = "$PSScriptRoot\seckill-users.csv"
$ttl = 30 * 24 * 3600

# 清空
$null | Set-Content $outFile -Encoding UTF8

# 分块
$batch = @()
$batchSize = 5000

for ($id = 1; $id -le $Count; $id++) {
    $token = New-Jwt $id 1 $ttl
    $did = "dev-$("{0:D5}" -f $id)"
    $batch += "$id,$token,$did"

    if ($batch.Count -ge $batchSize) {
        $batch -join "`r`n" | Add-Content $outFile -Encoding UTF8
        $batch = @()
        Write-Host "$id / $Count" -ForegroundColor Cyan
    }
}

# 剩余
if ($batch.Count -gt 0) {
    $batch -join "`r`n" | Add-Content $outFile -Encoding UTF8
}

Write-Host "完成！生成 $Count 个 token（30天过期）" -ForegroundColor Green
