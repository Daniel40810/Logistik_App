@echo off
REM =====================================================================
REM  Logistik_App - Datenbank einrichten (Schema DEMO, Praefix LOG_)
REM
REM  Doppelklick genuegt. Das Protokoll landet als db_setup.log direkt
REM  neben dieser Datei.
REM
REM  Aufrufvarianten:
REM     db_setup.bat                  alle Skripte (Neuaufbau)
REM     db_setup.bat 02_api.sql       nur ein einzelnes Skript
REM
REM  ACHTUNG: der Neuaufbau beginnt mit 00_drop.sql und loescht alle
REM  LOG_-Objekte samt Bewegungen. Die FCF_-Objekte bleiben unberuehrt.
REM
REM  Uebersetzt wird mit --release 11 wie bei FCurvedField: javac und java
REM  stammen auf diesem Rechner aus verschiedenen JDKs.
REM =====================================================================
setlocal
cd /d "%~dp0"

set OJDBC=lib\ojdbc11.jar
if not exist "%OJDBC%" (
  echo FEHLER: %OJDBC% nicht gefunden.
  pause
  exit /b 1
)

echo Verwendete Werkzeuge:
javac -version
java -version
echo.

if not exist build\tools mkdir build\tools

echo Uebersetze das Einrichtungsprogramm ...
javac --release 11 -nowarn -encoding UTF-8 -cp "%OJDBC%" -d build\tools tools\LogDbSetup.java
if errorlevel 1 (
  echo.
  echo FEHLER beim Uebersetzen. Steht ein JDK im PATH?
  pause
  exit /b 1
)

echo.
java -Dfile.encoding=UTF-8 -cp "%OJDBC%;build\tools" LogDbSetup %*
if errorlevel 1 (
  echo.
  echo Das Programm wurde nicht sauber beendet.
)

echo.
echo Fertig. Bitte den Inhalt von db_setup.log pruefen.
pause
