param(
    [string]$Jdk17 = $env:JAVA_HOME,
    [string]$Jdk8
)

$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskJavaProject = $taskRoot
$taskNative = Join-Path $taskRoot 'native'
$taskTools = Join-Path $taskRoot 'tools'
$taskOutput = Join-Path $taskRoot 'dist'

if (-not $Jdk17 -or -not (Test-Path -LiteralPath (Join-Path $Jdk17 'bin\javac.exe'))) {
    throw 'Informe o JDK 17: .\Build-Injector.ps1 -Jdk17 "C:\caminho\jdk-17"'
}
$env:JAVA_HOME = (Resolve-Path -LiteralPath $Jdk17).Path
$taskJava = Join-Path $env:JAVA_HOME 'bin\java.exe'
$taskJavac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
if (-not $Jdk8 -or -not (Test-Path -LiteralPath (Join-Path $Jdk8 'bin\javac.exe'))) {
    throw 'Informe o JDK 8: .\Build-Injector.ps1 -Jdk17 "C:\caminho\jdk-17" -Jdk8 "C:\caminho\jdk-8"'
}
$Jdk8 = (Resolve-Path -LiteralPath $Jdk8).Path

$taskGradleProperties = Join-Path $taskJavaProject 'gradle.properties'
$taskOriginalProperties = [System.IO.File]::ReadAllBytes($taskGradleProperties)
$taskGradleJdk8 = $Jdk8.Replace('\', '/')
[System.IO.File]::AppendAllText($taskGradleProperties,
    "`r`norg.gradle.java.installations.paths=$taskGradleJdk8`r`n")
Push-Location $taskJavaProject
try {
    & (Join-Path $taskJavaProject 'gradlew.bat') assemble
    if ($LASTEXITCODE -ne 0) { throw 'Falha no build Java do CrewX.' }
} finally {
    Pop-Location
    [System.IO.File]::WriteAllBytes($taskGradleProperties, $taskOriginalProperties)
}

$taskAsmBase = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\org.ow2.asm'
$taskAsmJars = @()
foreach ($taskModule in @('asm', 'asm-commons', 'asm-tree')) {
    $taskCandidates = @(Get-ChildItem -LiteralPath (Join-Path $taskAsmBase $taskModule) -Recurse -Filter "$taskModule-9.7.jar" -File -ErrorAction SilentlyContinue)
    if ($taskCandidates.Count -eq 0) { throw "Dependencia ASM 9.7 ausente: $taskModule" }
    $taskAsmJars += $taskCandidates[0].FullName
}
$taskAsmCp = $taskAsmJars -join ';'
$taskClasses = Join-Path $taskRoot 'build-tools'
New-Item -ItemType Directory -Path $taskClasses -Force | Out-Null
& $taskJavac -cp $taskAsmCp -d $taskClasses (Join-Path $taskTools 'AccessorRewriter.java') (Join-Path $taskTools 'RemapCrewToNotch.java')
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar as ferramentas de empacotamento.' }

$taskLibs = Join-Path $taskJavaProject 'build\libs'
$taskNamedInput = Get-ChildItem -LiteralPath (Join-Path $taskJavaProject 'build\intermediates') -Filter '*non-obfuscated-with-deps.jar' -File | Select-Object -First 1
if (-not $taskNamedInput) { throw 'JAR named com dependencias nao foi gerado.' }
$taskForgeInput = Join-Path $taskLibs 'CrewX.jar'
$taskForgePayload = Join-Path $taskLibs 'CrewX-inject-forge.jar'
$taskNamedPayload = Join-Path $taskLibs 'CrewX-inject-named.jar'
$taskNotchPayload = Join-Path $taskLibs 'CrewX-inject-notch.jar'
foreach ($taskFile in @($taskForgePayload, $taskNamedPayload, $taskNotchPayload)) {
    if (Test-Path -LiteralPath $taskFile) { Remove-Item -LiteralPath $taskFile -Force }
}
& $taskJava -cp "$taskClasses;$taskAsmCp" AccessorRewriter $taskForgeInput $taskForgePayload
if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar o payload Forge.' }
& $taskJava -cp "$taskClasses;$taskAsmCp" AccessorRewriter $taskNamedInput.FullName $taskNamedPayload
if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar o payload Lunar.' }

$taskMappings = Join-Path $taskJavaProject 'src\main\resources\crewx\inject'
& $taskJava -cp "$taskClasses;$taskAsmCp" RemapCrewToNotch $taskNamedPayload $taskNotchPayload `
    (Join-Path $taskMappings 'notch-joined.srg') `
    (Join-Path $taskMappings 'notch-methods.csv') `
    (Join-Path $taskMappings 'notch-fields.csv')
if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar o payload Badlion.' }

$taskBuild = Join-Path $taskRoot 'native-build'
$taskCmake = (Get-Command cmake.exe -ErrorAction SilentlyContinue).Source
if (-not $taskCmake) { $taskCmake = 'C:\Program Files\CMake\bin\cmake.exe' }
if (-not (Test-Path -LiteralPath $taskCmake)) { throw 'CMake nao encontrado.' }
if (-not (Get-Command cl.exe -ErrorAction SilentlyContinue)) {
    $taskVswhere = 'C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe'
    if (-not (Test-Path -LiteralPath $taskVswhere)) { throw 'Visual Studio C++ nao encontrado.' }
    $taskVsPath = & $taskVswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    $taskVsDevCmd = Join-Path $taskVsPath 'Common7\Tools\VsDevCmd.bat'
    if (-not (Test-Path -LiteralPath $taskVsDevCmd)) { throw 'VsDevCmd.bat nao encontrado.' }
    $taskEnvironment = & cmd.exe /c ('"' + $taskVsDevCmd + '" -arch=x64 >nul && set')
    foreach ($taskLine in $taskEnvironment) {
        if ($taskLine -match '^([^=]+)=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
}
& $taskCmake -S $taskNative -B $taskBuild -G 'NMake Makefiles' `
    '-DCMAKE_BUILD_TYPE=Release' `
    "-DCREWX_JAVA_HOME=$env:JAVA_HOME" `
    "-DCREWX_PRODUCT_JAR=$taskForgePayload" `
    "-DCREWX_PRODUCT_JAR_NAMED=$taskNamedPayload" `
    "-DCREWX_PRODUCT_JAR_NOTCH=$taskNotchPayload"
if ($LASTEXITCODE -ne 0) { throw 'Falha na configuracao nativa.' }
& $taskCmake --build $taskBuild --config Release --target CrewXInjector
if ($LASTEXITCODE -ne 0) { throw 'Falha no build do executavel.' }
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
$taskExe = Join-Path $taskBuild 'dist\CrewXInjector.exe'
Copy-Item -LiteralPath $taskExe -Destination (Join-Path $taskOutput 'CrewXInjector.exe') -Force
Write-Host "Concluido: $(Join-Path $taskOutput 'CrewXInjector.exe')"
