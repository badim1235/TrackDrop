$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $repositoryRoot ".env"

foreach ($line in Get-Content -LiteralPath $environmentFile) {
    $trimmedLine = $line.Trim()
    if ($trimmedLine.Length -eq 0 -or $trimmedLine.StartsWith("#")) {
        continue
    }

    $separatorIndex = $line.IndexOf("=")
    if ($separatorIndex -lt 1) {
        throw "Invalid .env entry: $line"
    }

    $name = $line.Substring(0, $separatorIndex).Trim()
    $value = $line.Substring($separatorIndex + 1)
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

$applicationGenres = Invoke-RestMethod "http://127.0.0.1:8080/api/v1/genres"
if (@($applicationGenres.items).Count -eq 0) {
    throw "TrackDrop API did not return the seeded genres."
}

$headers = @{
    apikey = $env:SUPABASE_PUBLISHABLE_KEY
    Accept = "application/json"
}
$dataApiUrl = "$($env:SUPABASE_URL)/rest/v1/genres?select=id&limit=1"
$curlOutput = @(& curl.exe --silent --show-error --write-out "`n%{http_code}" --header "apikey: $($headers.apikey)" --header "Accept: application/json" $dataApiUrl)
if ($LASTEXITCODE -ne 0 -or $curlOutput.Count -lt 2) {
    throw "Failed to call the Supabase Data API."
}

$dataApiStatusCode = [int] $curlOutput[-1]
$dataApiContent = $curlOutput[0..($curlOutput.Count - 2)] -join "`n"

if ($dataApiStatusCode -eq 200) {
    $rows = $dataApiContent | ConvertFrom-Json
    if (@($rows).Count -gt 0) {
        throw "Anonymous Data API access unexpectedly returned TrackDrop data."
    }
}
elseif ($dataApiStatusCode -notin 401, 403, 404) {
    throw "Unexpected Supabase Data API response: $dataApiStatusCode"
}

Write-Output "TrackDrop API genres: $(@($applicationGenres.items).Count)"
Write-Output "Anonymous Supabase Data API access: blocked"
