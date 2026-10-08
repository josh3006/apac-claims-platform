param([Parameter(ValueFromRemainingArguments = $true)][string[]]$NpmArguments)
$ErrorActionPreference = 'Stop'
# Windows npm.cmd may use its adjacent old node.exe even when PATH selects Node 22.
# Explicitly run npm's JS entry point with the project-local Node, when present.
$projectNode = Join-Path $PSScriptRoot '../.tools/package/bin/node.exe'
$nodeExecutable = if (Test-Path $projectNode) { (Resolve-Path $projectNode).Path } else { (Get-Command node.exe).Source }
$nodeVersion = [version]((& $nodeExecutable --version).TrimStart('v'))
if ($nodeVersion.Major -ne 22 -or $nodeVersion -lt [version]'22.12.0') { throw 'Use Node 22.12+ (22.x), as specified in README.' }
$npmDirectory = Split-Path (Get-Command npm.cmd).Source
$npmEntryPoint = Join-Path $npmDirectory 'node_modules/npm/bin/npm-cli.js'
if (!(Test-Path $npmEntryPoint)) { throw 'Cannot find npm-cli.js next to npm.cmd. Use a standard Node/npm installation.' }
$env:Path = "$(Split-Path $nodeExecutable);$env:Path"
& $nodeExecutable $npmEntryPoint @NpmArguments
exit $LASTEXITCODE
