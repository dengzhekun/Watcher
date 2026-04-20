$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

[Console]::InputEncoding = $utf8NoBom
[Console]::OutputEncoding = $utf8NoBom
$OutputEncoding = $utf8NoBom

$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'
$PSDefaultParameterValues['Set-Content:Encoding'] = 'utf8'
$PSDefaultParameterValues['Add-Content:Encoding'] = 'utf8'
$PSDefaultParameterValues['Export-Csv:Encoding'] = 'utf8'

try {
    chcp 65001 | Out-Null
} catch {
}

Write-Host "PowerShell UTF-8 mode enabled."
Write-Host ("Code page: " + (chcp))
Write-Host ("InputEncoding: " + [Console]::InputEncoding.WebName)
Write-Host ("OutputEncoding: " + [Console]::OutputEncoding.WebName)
Write-Host ("`$OutputEncoding: " + $OutputEncoding.WebName)

Write-Host ""
Write-Host "If this works, copy the same logic into:"
Write-Host $PROFILE
