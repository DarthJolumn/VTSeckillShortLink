@echo off
chcp 65001 >nul 2>nul
echo ============================================
echo   LiveMall JMeter 全量压测 - Windows版
echo ============================================

set SCRIPT_DIR=%~dp0
set JMX_DIR=%SCRIPT_DIR%..\jmx
set CSV_DIR=%SCRIPT_DIR%..\csv
set REPORTS_DIR=%SCRIPT_DIR%..\reports
set GC_TYPE=%1
if "%GC_TYPE%"=="" set GC_TYPE=g1

set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%-%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
set JTL_FILE=%REPORTS_DIR%\all-in-one-%GC_TYPE%-%TIMESTAMP%.jtl
set RPT_DIR=%REPORTS_DIR%\all-in-one-%GC_TYPE%-%TIMESTAMP%

echo GC类型: %GC_TYPE%
echo JTL:    %JTL_FILE%
echo 报告:   %RPT_DIR%
echo.

if not exist "%REPORTS_DIR%" mkdir "%REPORTS_DIR%"
if not exist "%RPT_DIR%" mkdir "%RPT_DIR%"

set JAVA_HOME=C:\Program Files\Java\jdk-25.0.3
set PATH=%JAVA_HOME%\bin;%SystemRoot%\system32;%SystemRoot%;%PATH%

echo [START] 开始压测...
"D:\software\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin\jmeter" -n -t "%JMX_DIR%\00-all-in-one.jmx" -l "%JTL_FILE%" -e -o "%RPT_DIR%" -j "%RPT_DIR%\jmeter.log" -Jjmeterengine.force.system.exit=true

echo [DONE] 测试完成
echo 报告: %RPT_DIR%\index.html
