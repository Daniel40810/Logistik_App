@echo off
REM =====================================================================
REM  Logistik_App - die Anwendung starten (Europa-Karte aus DEMO)
REM
REM  Mausrad zoomt, Ziehen verschiebt, Doppelklick zeigt alle Staedte.
REM  In NetBeans geht es genauso: Hauptklasse ist
REM  com.dan.logistikapp.ui.LogistikApp.
REM =====================================================================
setlocal
cd /d "%~dp0"

if not exist build\app mkdir build\app
dir /s /b src\*.java > build\sources.txt
javac --release 11 -nowarn -encoding UTF-8 -cp "lib\FStyle.jar;lib\ojdbc11.jar" ^
      -d build\app @build\sources.txt
if errorlevel 1 (
  echo FEHLER beim Uebersetzen.
  pause
  exit /b 1
)
start "" javaw -Dfile.encoding=UTF-8 -cp "lib\FStyle.jar;lib\ojdbc11.jar;build\app" com.dan.logistikapp.ui.LogistikApp
