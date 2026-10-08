param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'

function New-DemoHeaders([string]$User, [string]$Password) {
    $token = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${User}:${Password}"))
    return @{ Authorization = "Basic $token" }
}
$claimantHeaders = New-DemoHeaders 'claimant' 'claimant-demo'
$officerHeaders = New-DemoHeaders 'officer' 'officer-demo'
function Send-Json([string]$Path, $Body, $Headers) {
    Invoke-RestMethod -Uri "$BaseUrl$Path" -Method Post -Headers $Headers -ContentType 'application/json' -Body ($Body | ConvertTo-Json)
}
function Assert-Status($Claim, [string]$Expected) {
    if ($Claim.status -ne $Expected) { throw "Expected $Expected, got $($Claim.status)" }
}

$claim = Send-Json '/api/claims' @{type='MOTOR';market='MY';incidentDate='2026-01-01';description='Smoke test: rear bumper damage';estimatedAmount=1200} $claimantHeaders
Assert-Status $claim 'SUBMITTED'
$claimPath = "/api/officer/claims/$($claim.id)"
$claim = Send-Json "$claimPath/assign" @{version=$claim.version;message='Picked up'} $officerHeaders
Assert-Status $claim 'UNDER_REVIEW'
$claim = Send-Json "$claimPath/request-information" @{version=$claim.version;message='Please describe damage'} $officerHeaders
Assert-Status $claim 'INFORMATION_REQUIRED'
$claim = Send-Json "/api/claims/$($claim.id)/information" @{version=$claim.version;message='Rear bumper cracked'} $claimantHeaders
Assert-Status $claim 'UNDER_REVIEW'
$claim = Send-Json "$claimPath/decision" @{version=$claim.version;status='SETTLED';message='Approved';settlementAmount=1000} $officerHeaders
Assert-Status $claim 'SETTLED'
if ($claim.history.Count -ne 5) { throw 'Expected five history entries' }
$dashboard = Invoke-RestMethod -Uri "$BaseUrl/api/officer/dashboard" -Headers $officerHeaders
if ($dashboard.settledAmountByCurrency.MYR -lt 1000) { throw 'Settlement missing from dashboard' }
Write-Output "PASS: submitted -> review -> information request -> response -> settled; five history entries; dashboard updated. Reference: $($claim.id)"
