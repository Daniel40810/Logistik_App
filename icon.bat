@echo off
REM =====================================================================
REM  Logistik_App - Programmsymbol neu malen
REM
REM  Schreibt icon\logistik.ico (16 bis 256 Pixel) und icon\logistik-256.png
REM  aus com.dan.logistikapp.ui.AppIcon. Nur noetig, wenn das Symbol
REM  geaendert wurde - sonst liegt es schon im Ordner icon.
REM =====================================================================
setlocal
cd /d "%~dp0"

if not exist build\check mkdir build\check

echo Uebersetze Symbol und Werkzeug ...
dir /s /b src\*.java > build\sources.txt
javac --release 11 -nowarn -encoding UTF-8 -cp "lib\FStyle.jar" -d build\check @build\sources.txt
if errorlevel 1 (
  echo FEHLER beim Uebersetzen der Anwendungsklassen.
  pause
  exit /b 1
)
javac --release 11 -nowarn -encoding UTF-8 -cp "lib\FStyle.jar;build\check" ^
      -d build\check tools\LogIconExport.java
if errorlevel 1 (
  echo FEHLER beim Uebersetzen des Werkzeugs.
  pause
  exit /b 1
)

java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "lib\FStyle.jar;build\check" LogIconExport .
echo.
echo Fertig. icon\logistik.ico und icon\logistik-256.png liegen im Projektordner.
pause
