@echo off
REM =====================================================================
REM  Maven/Gradle OLMADAN derleme (Windows, JDK 11+).   build.bat
REM  Cikti: build\classes\  ve  billing-batch.jar
REM =====================================================================
setlocal
cd /d "%~dp0"

if not exist lib\elk-logger-1.0.0.jar (
  if exist ..\..\dist\elk-logger-1.0.0.jar copy ..\..\dist\elk-logger-1.0.0.jar lib\ >nul
)
if not exist lib\elk-logger-1.0.0.jar (
  echo HATA: lib\elk-logger-1.0.0.jar yok. elk-logger jar'ini lib\ dizinine kopyalayin.
  exit /b 1
)

if not exist build\classes mkdir build\classes
dir /s /b src\*.java > build\sources.txt
javac --release 11 -encoding UTF-8 -cp "lib\*" -d build\classes @build\sources.txt || exit /b 1

(
  echo Main-Class: com.example.billing.BillingBatchApp
  echo Class-Path: lib/elk-logger-1.0.0.jar config/
) > build\MANIFEST.MF
jar cfm billing-batch.jar build\MANIFEST.MF -C build\classes . || exit /b 1
echo Derlendi: build\classes ve billing-batch.jar
