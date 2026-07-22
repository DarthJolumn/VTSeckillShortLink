@echo off
chcp 65001 >nul
echo 修复文件编码为 UTF-8 No BOM...
powershell -Command "Get-ChildItem '%~dp0..\jmx' -Filter *.jmx | ForEach-Object { $c = Get-Content $_.FullName -Raw; [System.IO.File]::WriteAllText($_.FullName, $c, [System.Text.UTF8Encoding]::new($false)) }"
powershell -Command "Get-ChildItem '%~dp0..\csv' -Filter *.csv | ForEach-Object { $c = Get-Content $_.FullName -Raw; [System.IO.File]::WriteAllText($_.FullName, $c, [System.Text.UTF8Encoding]::new($false)) }"
echo 完成！
