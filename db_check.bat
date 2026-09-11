@echo off
REM =====================================================================
REM  Logistik_App - Durchstich Phase 1 pruefen
REM
REM  Prueft das Schema LOG_* gegen die Anforderungen und gegen eine
REM  unabhaengige Java-Rechnung (Entfernungen, ISO-6346-Pruefziffer).
REM  Alles, was die Pruefung schreibt, wird am Ende zurueckgerollt -
REM  sie laesst sich beliebig oft wiederholen.
REM
REM  Ergebnis: db_check.log und db_karte.png
REM =====================================================================
setlocal
cd /d "%~dp0"

set OJDBC=lib\ojdbc11.jar
if not exist "%OJDBC%" (
  echo FEHLER: %OJDBC% nicht gefunden.
  pause
  exit /b 1
)

if not exist build\check mkdir build\check

echo Uebersetze Anwendungsklassen und Pruefprogramm ...
dir /s /b src\*.java > build\sources.txt
javac --release 11 -nowarn -encoding UTF-8 -cp "lib\FStyle.jar;%OJDBC%" ^
      -d build\check @build\sources.txt
if errorlevel 1 (
  echo FEHLER beim Uebersetzen der Anwendungsklassen.
  pause
  exit /b 1
)

javac --release 11 -nowarn -encoding UTF-8 -cp "lib\FStyle.jar;%OJDBC%;build\check" ^
      -d build\check tools\LogDbCheck.java
if errorlevel 1 (
  echo FEHLER beim Uebersetzen des Pruefprogramms.
  pause
  exit /b 1
)

echo.
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "lib\FStyle.jar;%OJDBC%;build\check" LogDbCheck

echo.
echo Fertig. db_check.log liegt im Projektordner.
pause
