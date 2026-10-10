param(
    [string]$Jdk17 = 'C:\Program Files\Java\jdk-17',
    [string]$Jdk8 = '',
    [string]$GradleUserHome = '',
    [string]$CMake = ''
)

$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskProject = Join-Path $taskRoot 'CrewX-2.1'
$taskLibs = Join-Path $taskProject 'build\libs'
$taskClasses = Join-Path $taskRoot 'build-tools'
$taskBuild = Join-Path $taskRoot 'native-build'
$taskOutput = Join-Path $taskRoot 'dist'

if (-not (Test-Path -LiteralPath (Join-Path $Jdk17 'bin\javac.exe'))) {
    throw "JDK 17 nao encontrado: $Jdk17"
}
if ($Jdk8 -and -not (Test-Path -LiteralPath (Join-Path $Jdk8 'lib\tools.jar'))) {
    throw "JDK 8 completo nao encontrado: $Jdk8"
}
if ($GradleUserHome) { $env:GRADLE_USER_HOME = $GradleUserHome }
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle' }
$env:JAVA_HOME = (Resolve-Path -LiteralPath $Jdk17).Path
$taskJava = Join-Path $env:JAVA_HOME 'bin\java.exe'
$taskJavac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
$taskGradleProperties = Join-Path $taskProject 'gradle.properties'
$originalProperties = [System.IO.File]::ReadAllText($taskGradleProperties)

try {
    if ($Jdk8) {
        $jdk8Path = (Resolve-Path -LiteralPath $Jdk8).Path.Replace('\', '/')
        [System.IO.File]::WriteAllText($taskGradleProperties,
            $originalProperties + "`r`norg.gradle.java.installations.auto-detect=false`r`norg.gradle.java.installations.paths=$jdk8Path`r`n",
            [System.Text.UTF8Encoding]::new($false))
    }
    Push-Location $taskProject
    try {
        & (Join-Path $taskProject 'gradlew.bat') assemble
        if ($LASTEXITCODE -ne 0) { throw 'Falha no build Java do CrewX.' }
    } finally { Pop-Location }
} finally {
    [System.IO.File]::WriteAllText($taskGradleProperties, $originalProperties,
        [System.Text.UTF8Encoding]::new($false))
}

$asmBase = Join-Path $env:GRADLE_USER_HOME 'caches\modules-2\files-2.1\org.ow2.asm'
$asmJars = @()
foreach ($module in @('asm', 'asm-commons', 'asm-tree')) {
    $candidate = Get-ChildItem -LiteralPath (Join-Path $asmBase $module) -Recurse `
        -Filter "$module-9.7.jar" -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $candidate) { throw "Dependencia ASM 9.7 ausente: $module" }
    $asmJars += $candidate.FullName
}
$asmCp = $asmJars -join ';'
New-Item -ItemType Directory -Path $taskClasses -Force | Out-Null
& $taskJavac -cp $asmCp -d $taskClasses `
    (Join-Path $taskRoot 'tools\AccessorRewriter.java') `
    (Join-Path $taskRoot 'tools\RemapCrewToNotch.java')
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar ferramentas de empacotamento.' }

$namedInput = Get-ChildItem -LiteralPath (Join-Path $taskProject 'build\intermediates') `
    -Filter '*non-obfuscated-with-deps.jar' -File | Select-Object -First 1
if (-not $namedInput) { throw 'JAR named nao foi gerado.' }
$forgePayload = Join-Path $taskLibs 'CrewX-inject-forge.jar'
$namedPayload = Join-Path $taskLibs 'CrewX-inject-named.jar'
$notchPayload = Join-Path $taskLibs 'CrewX-inject-notch.jar'
& $taskJava -cp "$taskClasses;$asmCp" AccessorRewriter `
    (Join-Path $taskLibs 'CrewX.jar') $forgePayload
if ($LASTEXITCODE -ne 0) { throw 'Falha no payload Forge.' }
& $taskJava -cp "$taskClasses;$asmCp" AccessorRewriter `
    $namedInput.FullName $namedPayload
if ($LASTEXITCODE -ne 0) { throw 'Falha no payload Lunar.' }
$maps = Join-Path $taskProject 'src\main\resources\crewx\inject'
& $taskJava -cp "$taskClasses;$asmCp" RemapCrewToNotch $namedPayload $notchPayload `
    (Join-Path $maps 'notch-joined.srg') `
    (Join-Path $maps 'notch-methods.csv') `
    (Join-Path $maps 'notch-fields.csv')
if ($LASTEXITCODE -ne 0) { throw 'Falha no payload Badlion.' }

if (-not $CMake) {
    $foundCMake = Get-Command cmake -ErrorAction SilentlyContinue
    if ($foundCMake) { $CMake = $foundCMake.Source }
    else {
        $CMake = 'C:\Program Files\Microsoft Visual Studio\18\Professional\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe'
    }
}
if (-not (Test-Path -LiteralPath $CMake)) { throw "CMake nao encontrado: $CMake" }
& $CMake -S (Join-Path $taskRoot 'native') -B $taskBuild -A x64 `
    "-DCREWX_JAVA_HOME=$env:JAVA_HOME" `
    "-DCREWX_PRODUCT_JAR=$forgePayload" `
    "-DCREWX_PRODUCT_JAR_NAMED=$namedPayload" `
    "-DCREWX_PRODUCT_JAR_NOTCH=$notchPayload"
if ($LASTEXITCODE -ne 0) { throw 'Falha na configuracao nativa.' }
& $CMake --build $taskBuild --config Release --target CrewXInjector
if ($LASTEXITCODE -ne 0) { throw 'Falha no build do EXE.' }
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $taskBuild 'dist\CrewXInjector.exe') `
    -Destination (Join-Path $taskOutput 'CrewXInjector.exe') -Force
Write-Host "Concluido: $(Join-Path $taskOutput 'CrewXInjector.exe')"
