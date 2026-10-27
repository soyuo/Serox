[CmdletBinding()]
param(
    [ValidateSet('app-image', 'msi', 'exe')]
    [string] $Type = 'msi'
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $projectRoot

function Resolve-Jpackage {
    $command = Get-Command jpackage.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $javaSettings = & $javaCommand.Source '-XshowSettings:properties' '-version' 2>&1
        $ErrorActionPreference = $previousErrorAction
        $javaHomeSetting = $javaSettings | Select-String '^\s*java\.home\s*=' | Select-Object -First 1
        if ($javaHomeSetting -and $javaHomeSetting.Line -match '=\s*(.+?)\s*$') {
            $candidate = Join-Path $Matches[1] 'bin\jpackage.exe'
            if (Test-Path $candidate) {
                return $candidate
            }
        }
    }

    throw 'jpackage.exe was not found. Install JDK 25 or newer and add its bin directory to PATH.'
}

if ($Type -in @('msi', 'exe')) {
    $localWixDirectory = Join-Path $env:LOCALAPPDATA 'Serox\tools\wix-3.14.1'
    if ((Test-Path (Join-Path $localWixDirectory 'candle.exe')) -and
        (Test-Path (Join-Path $localWixDirectory 'light.exe'))) {
        $env:Path = "$localWixDirectory;$env:Path"
    }

    foreach ($tool in @('candle.exe', 'light.exe')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
            throw "${tool} was not found. Run scripts/prepare-wix.ps1 or install WiX Toolset 3.x and add its bin directory to PATH."
        }
    }
}

$pom = [xml] (Get-Content -Raw -Encoding UTF8 'pom.xml')
$version = $pom.project.properties.'app.release.version'
if (-not $version) {
    throw 'Set app.release.version in pom.xml before packaging.'
}

$jpackage = Resolve-Jpackage
$dependencyPlugin = 'org.apache.maven.plugins:maven-dependency-plugin:3.11.0:copy-dependencies'
# Windows PowerShell 5.1 turns any native stderr output (even a JVM warning) into a terminating
# error, so rely on the exit code instead.
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& mvn clean package $dependencyPlugin '-DincludeScope=runtime' '-DoutputDirectory=target/jpackage-win'
$mavenExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorAction
if ($mavenExitCode -ne 0) {
    throw "Maven packaging failed with exit code $mavenExitCode."
}

# The platform-neutral JavaFX jars carry no native libraries; keep only the -win ones.
Get-ChildItem 'target/jpackage-win' -File -Filter 'javafx-*.jar' |
    Where-Object { $_.Name -notmatch '-win\.jar$' } |
    Remove-Item -Force

$applicationJar = Get-ChildItem 'target' -File -Filter 'serox-browser-*.jar' |
    Where-Object { $_.Name -notmatch '(sources|javadoc)' } |
    Select-Object -First 1
if (-not $applicationJar) {
    throw 'The Serox application JAR was not produced under target/.'
}
Copy-Item -LiteralPath $applicationJar.FullName -Destination 'target/jpackage-win' -Force

$destination = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$jpackageArgs = @(
    '--type', $Type,
    '--name', 'Serox',
    '--dest', $destination,
    '--module-path', (Join-Path $projectRoot 'target/jpackage-win'),
    '--module', 'kr.soyuo.serox/kr.soyuo.serox.app.SeroxApplication',
    '--app-version', $version,
    '--vendor', 'soyuo.kr',
    '--description', 'Serox AI Searching Browser',
    '--java-options', '--enable-native-access=javafx.graphics',
    '--icon', (Join-Path $projectRoot 'scripts/icon/serox.ico')
)

if ($Type -in @('msi', 'exe')) {
    $jpackageArgs += @('--win-menu', '--win-menu-group', 'Serox', '--win-shortcut')
}

$ErrorActionPreference = 'Continue'
& $jpackage @jpackageArgs
$jpackageExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorAction
if ($jpackageExitCode -ne 0) {
    throw "jpackage failed with exit code $jpackageExitCode."
}

Write-Host "Created $Type package in $destination"
