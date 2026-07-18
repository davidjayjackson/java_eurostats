<#
.SYNOPSIS
  Builds build\EurostatAddin-<version>.oxt using only the LibreOffice SDK's own
  toolchain (unoidl-write, javamaker, javac, jar) plus .NET zip -- no Maven, no
  external libraries. Windows counterpart of build.sh.

.DESCRIPTION
  Auto-detects the LibreOffice install and a JDK 8+. Override either by setting
  the LO_HOME or JDK environment variables before running, e.g.
      $env:LO_HOME = 'C:\Program Files\LibreOfficeDev 26'
      .\build.ps1
#>
$ErrorActionPreference = 'Stop'

$REPO = $PSScriptRoot

# --- Locate LibreOffice (must contain an sdk\ subdir) ---------------------
$LO_HOME = $env:LO_HOME
if (-not $LO_HOME) {
    $LO_HOME = @(
        'C:\Program Files\LibreOffice',
        'C:\Program Files (x86)\LibreOffice'
    ) | Where-Object { Test-Path (Join-Path $_ 'sdk\bin\unoidl-write.exe') } | Select-Object -First 1
}
if (-not $LO_HOME -or -not (Test-Path (Join-Path $LO_HOME 'sdk\bin\unoidl-write.exe'))) {
    throw "LibreOffice SDK not found. Set `$env:LO_HOME to a LibreOffice install that includes the SDK (an sdk\ subfolder)."
}

# --- Locate a JDK (needs javac + jar) -------------------------------------
$JDK = $env:JDK
if (-not $JDK) {
    $JDK = @(
        Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue
        Get-ChildItem 'C:\Program Files\Java' -Directory -Filter 'jdk*' -ErrorAction SilentlyContinue
        Get-ChildItem 'C:\Program Files\Microsoft' -Directory -Filter 'jdk*' -ErrorAction SilentlyContinue
    ) | Sort-Object Name -Descending |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $JDK -or -not (Test-Path (Join-Path $JDK 'bin\javac.exe'))) {
    throw "No JDK found (need javac + jar). Set `$env:JDK to a JDK 8+ install directory."
}

$SDK_BIN      = Join-Path $LO_HOME 'sdk\bin'
$PROGRAM      = Join-Path $LO_HOME 'program'
$UNO_CLASSES  = Join-Path $PROGRAM 'classes'
$JAVAC        = Join-Path $JDK 'bin\javac.exe'
$JAR          = Join-Path $JDK 'bin\jar.exe'

$UNOIDL_WRITE = Join-Path $SDK_BIN 'unoidl-write.exe'
$JAVAMAKER    = Join-Path $SDK_BIN 'javamaker.exe'

$URE_TYPES    = Join-Path $PROGRAM 'types.rdb'
$OFFICE_TYPES = Join-Path $PROGRAM 'types\offapi.rdb'

$BUILD       = Join-Path $REPO 'build'
$GEN         = Join-Path $BUILD 'gen'
$CLASSES_OUT = Join-Path $BUILD 'classes'
$STAGE       = Join-Path $BUILD 'oxt-stage'

$IDL_FILE = Join-Path $REPO 'idl\org\libreoffice\eurostat\addin\XEurostatAddin.idl'
$IDL_TYPE = 'org.libreoffice.eurostat.addin.XEurostatAddin'

Write-Host "LibreOffice: $LO_HOME"
Write-Host "JDK:         $JDK"

# The SDK tools (unoidl-write, javamaker) link against DLLs shipped in
# LibreOffice's program\ directory; put it on PATH for the child processes.
$env:PATH = "$PROGRAM;$SDK_BIN;$env:PATH"

# Helper: run a native exe and stop on non-zero exit.
function Invoke-Checked {
    param([string]$Exe, [string[]]$Arguments)
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$([System.IO.Path]::GetFileName($Exe)) failed with exit code $LASTEXITCODE" }
}

Write-Host "== Cleaning build directory =="
if (Test-Path $BUILD) { Remove-Item $BUILD -Recurse -Force }
New-Item -ItemType Directory -Path $GEN, $CLASSES_OUT, (Join-Path $STAGE 'META-INF') -Force | Out-Null

$UNO_RDB = Join-Path $GEN 'EurostatAddin.uno.rdb'

Write-Host "== 1/6: unoidl-write (compile custom IDL to rdb) =="
Invoke-Checked $UNOIDL_WRITE @($URE_TYPES, $OFFICE_TYPES, $IDL_FILE, $UNO_RDB)

Write-Host "== 2/6: javamaker (generate Java stub for XEurostatAddin) =="
Invoke-Checked $JAVAMAKER @('-nD', "-T$IDL_TYPE", "-O$CLASSES_OUT", $UNO_RDB, "-X$URE_TYPES", "-X$OFFICE_TYPES")

Write-Host "== 3/6: javac (compile our sources against the UNO jars + generated stub) =="
$UNO_CP = @('ridl.jar','jurt.jar','juh.jar','unoil.jar','java_uno.jar') |
    ForEach-Object { Join-Path $UNO_CLASSES $_ }
$CP = ($UNO_CP + $CLASSES_OUT) -join ';'
$SOURCES = Get-ChildItem (Join-Path $REPO 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
Invoke-Checked $JAVAC (@('-nowarn', '-classpath', $CP, '-d', $CLASSES_OUT) + $SOURCES)

Write-Host "== 4/6: jar (pack EurostatAddin.uno.jar with UNO-Type-Path manifest) =="
$MANIFEST = Join-Path $GEN 'EurostatAddin.uno.Manifest'
@(
    'UNO-Type-Path: EurostatAddin.uno.jar'
    'RegistrationClassName: org.libreoffice.eurostat.addin.EurostatAddin'
) -join "`n" | Set-Content -Path $MANIFEST -Encoding ascii
$UNO_JAR = Join-Path $BUILD 'EurostatAddin.uno.jar'
Invoke-Checked $JAR @('cfm', $UNO_JAR, $MANIFEST, '-C', $CLASSES_OUT, '.')

Write-Host "== 5/6: assemble .oxt staging directory =="
@'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE manifest:manifest PUBLIC "-//OpenOffice.org//DTD Manifest 1.0//EN" "Manifest.dtd">
<manifest:manifest xmlns:manifest="http://openoffice.org/2001/manifest">
 <manifest:file-entry manifest:media-type="application/vnd.sun.star.uno-typelibrary;type=RDB"
                       manifest:full-path="EurostatAddin.uno.rdb"/>
 <manifest:file-entry manifest:media-type="application/vnd.sun.star.uno-components"
                       manifest:full-path="EurostatAddin.components"/>
</manifest:manifest>
'@ | Set-Content -Path (Join-Path $STAGE 'META-INF\manifest.xml') -Encoding utf8

Copy-Item (Join-Path $REPO 'packaging\description.xml')        (Join-Path $STAGE 'description.xml')
Copy-Item (Join-Path $REPO 'packaging\EurostatAddin.components') (Join-Path $STAGE 'EurostatAddin.components')
Copy-Item (Join-Path $REPO 'packaging\icon.png')               (Join-Path $STAGE 'icon.png')
Copy-Item $UNO_RDB (Join-Path $STAGE 'EurostatAddin.uno.rdb')
Copy-Item $UNO_JAR (Join-Path $STAGE 'EurostatAddin.uno.jar')

Write-Host "== 6/6: zip .oxt =="
# Name the artifact after the version declared in description.xml.
[xml]$desc = Get-Content (Join-Path $REPO 'packaging\description.xml')
$version = $desc.description.version.value
$OXT = if ($version) { Join-Path $BUILD "EurostatAddin-$version.oxt" } else { Join-Path $BUILD 'EurostatAddin.oxt' }
if (Test-Path $OXT) { Remove-Item $OXT -Force }
Add-Type -AssemblyName System.IO.Compression.FileSystem
# CreateFromDirectory writes forward-slash entry paths on PowerShell 7 (.NET Core), matching the OXT/zip spec.
[System.IO.Compression.ZipFile]::CreateFromDirectory($STAGE, $OXT, [System.IO.Compression.CompressionLevel]::Optimal, $false)

Write-Host "Built $OXT"
