param(
  [string]$ApiBase = "https://watchlist-backend-one.vercel.app/api/v1",
  [string]$DeviceId = "living-room-tv",
  [string]$BridgeKey = $env:WATCHCUE_TV_BRIDGE_KEY,
  [string]$TvHost = "192.168.0.100",
  [int]$PollSeconds = 15
)
if ([string]::IsNullOrWhiteSpace($BridgeKey)) { throw "Set WATCHCUE_TV_BRIDGE_KEY or pass -BridgeKey." }
$headers = @{ "X-Bridge-Key" = $BridgeKey }
Write-Host "WatchCue TV Bridge started for $DeviceId -> $TvHost"
while ($true) {
  try {
    $url = "$ApiBase/tv/jobs/pending?deviceId=$([uri]::EscapeDataString($DeviceId))"
    $jobs = @(Invoke-RestMethod -Method Get -Uri $url -Headers $headers)
    foreach ($job in $jobs) {
      $body = @{ duration=12; position=0; title=$job.title; titleColor="#FFFFFF"; titleSize=20; message=$job.message; messageColor="#FFFFFF"; messageSize=14; backgroundColor="#212121" }
      Invoke-RestMethod -Method Post -Uri "http://$($TvHost):7979/notify" -ContentType "application/json" -Body ($body | ConvertTo-Json)
      Invoke-RestMethod -Method Post -Uri "$ApiBase/tv/jobs/$($job.id)/delivered" -Headers $headers | Out-Null
      Write-Host "Delivered $($job.id)"
    }
  } catch { Write-Warning $_.Exception.Message }
  Start-Sleep -Seconds $PollSeconds
}
