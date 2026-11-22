@echo off
REM =====================================================================
REM  Windows: elk-logger'i classpath'e eklemenin yollari (run.sh ile ayni modlar).
REM    run.bat [cp^|argfile^|env^|jar^|workdir^|file] [adet] [bekleme_ms]
REM  Windows'ta classpath ayiraci ';' dir.
REM =====================================================================
setlocal
cd /d "%~dp0"
if not exist build\classes call build.bat || exit /b 1

set MODE=%1
if "%MODE%"=="" set MODE=cp
set MAIN=com.example.billing.BillingBatchApp

if "%MODE%"=="cp"      java -cp "build\classes;lib\*;config" %MAIN% %2 %3 & goto :eof
if "%MODE%"=="argfile" java @application-windows.classpath %MAIN% %2 %3 & goto :eof
if "%MODE%"=="env"     set "CLASSPATH=build\classes;lib\*;config" & java %MAIN% %2 %3 & goto :eof
if "%MODE%"=="jar"     java -jar billing-batch.jar %2 %3 & goto :eof
if "%MODE%"=="workdir" java -cp "build\classes;lib\*" %MAIN% %2 %3 & goto :eof
if "%MODE%"=="file"    java -Delk.config.file=config\elk-logger.properties -cp "build\classes;lib\*" %MAIN% %2 %3 & goto :eof
echo Kullanim: run.bat [cp^|argfile^|env^|jar^|workdir^|file] [adet] [bekleme_ms]
exit /b 1
