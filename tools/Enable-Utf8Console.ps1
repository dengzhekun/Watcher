[CmdletBinding()]
param()

$utf8 = New-Object System.Text.UTF8Encoding($false)

chcp 65001 > $null
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'
$PSDefaultParameterValues['Set-Content:Encoding'] = 'utf8'
$PSDefaultParameterValues['Add-Content:Encoding'] = 'utf8'
$env:PYTHONIOENCODING = 'utf-8'

Write-Host 'PowerShell UTF-8 mode enabled (code page 65001).'
