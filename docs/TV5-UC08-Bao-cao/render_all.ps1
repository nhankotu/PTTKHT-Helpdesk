param(
    [Parameter(Mandatory=$true)][string]$PlantUmlJar,
    [string]$JavaPath = 'java',
    [string]$PythonPath = 'python'
)
$ErrorActionPreference = 'Stop'
$jar = (Resolve-Path -LiteralPath $PlantUmlJar).Path
$sources = Join-Path $PSScriptRoot '*.puml'
foreach ($format in @('-tsvg','-tpng')) {
    & $JavaPath '-Djava.awt.headless=true' -jar $jar -charset UTF-8 $format $sources
    if ($LASTEXITCODE -ne 0) { throw "PlantUML failed for $format" }
}
& $PythonPath (Join-Path $PSScriptRoot 'render_mockups.py')
if ($LASTEXITCODE -ne 0) { throw 'Mockup rendering failed' }
