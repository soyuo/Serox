[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$wixVersion = '3.14.1'
$wixUri = 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip'
$expectedSha256 = '6AC824E1642D6F7277D0ED7EA09411A508F6116BA6FAE0AA5F2C7DAA2FF43D31'
$wixDirectory = Join-Path $env:LOCALAPPDATA "Serox\tools\wix-$wixVersion"

if ((Test-Path (Join-Path $wixDirectory 'candle.exe')) -and
    (Test-Path (Join-Path $wixDirectory 'light.exe'))) {
    Write-Host "WiX Toolset $wixVersion is already available at $wixDirectory"
    return
}

$downloadPath = Join-Path $env:TEMP "wix-$wixVersion-$([guid]::NewGuid()).zip"
$extractDirectory = Join-Path $env:TEMP "wix-$wixVersion-$([guid]::NewGuid())"

try {
    Invoke-WebRequest -Uri $wixUri -OutFile $downloadPath
    $actualSha256 = (Get-FileHash -LiteralPath $downloadPath -Algorithm SHA256).Hash
    if ($actualSha256 -ne $expectedSha256) {
        throw "WiX archive SHA-256 mismatch. Expected $expectedSha256, got $actualSha256."
    }

    Expand-Archive -LiteralPath $downloadPath -DestinationPath $extractDirectory
    foreach ($tool in @('candle.exe', 'light.exe')) {
        if (-not (Get-ChildItem -LiteralPath $extractDirectory -Filter $tool -Recurse)) {
            throw "WiX archive did not contain $tool."
        }
    }

    New-Item -ItemType Directory -Path $wixDirectory -Force | Out-Null
    Copy-Item -Path (Join-Path $extractDirectory '*') -Destination $wixDirectory -Recurse -Force
    Write-Host "WiX Toolset $wixVersion is ready at $wixDirectory"
}
finally {
    Remove-Item -LiteralPath $downloadPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $extractDirectory -Recurse -Force -ErrorAction SilentlyContinue
}
