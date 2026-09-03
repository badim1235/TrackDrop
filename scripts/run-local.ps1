param(
    [switch]$UseConfiguredDatabase
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $repositoryRoot ".env"

if (-not (Test-Path -LiteralPath $environmentFile)) {
    throw "Missing .env file. Copy .env.example to .env and fill in the local values."
}

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

if ($UseConfiguredDatabase) {
    if ($env:POSTGRES_PASSWORD -eq "REPLACE_WITH_NEW_DATABASE_PASSWORD") {
        throw "Replace POSTGRES_PASSWORD in .env before starting TrackPick."
    }
}
else {
    $env:DATABASE_URL = "jdbc:postgresql://localhost:5432/trackdrop"
    $env:POSTGRES_USER = "trackdrop"
    $env:POSTGRES_PASSWORD = "trackdrop"
}

Push-Location (Join-Path $repositoryRoot "backend")
try {
    & .\mvnw.cmd spring-boot:run
}
finally {
    Pop-Location
}
