@echo off
REM =====================================================================
REM  Logistik_App - Karte OHNE Datenbank pruefen
REM
REM  Projektion, WKT-Leser, Flaechen, Ansicht, Container, Tempo.
REM  Die Laender kommen aus db\07_grenzen.tsv, die Zonen aus 05/06.
REM  Ergebnis: karte_test.log, karte_vorschau.png, container_vorschau.png
REM =====================================================================
setlocal
cd /d "%~dp0"

if not exist build\check mkdir build\check
dir /s /b src\*.java > build\sources.txt
javac --release 11 -nowarn -encoding UTF-8 -cp "lib\FStyle.jar;lib\ojdbc11.jar" ^
      -d build\check @build\sources.txt
if errorlevel 1 (
  echo FEHLER beim Uebersetzen.
  pause
  exit /b 1
)
javac --release 11 -nowarn -encoding UTF-8 -cp "lib\FStyle.jar;build\check" ^
      -d build\check tools\LogKarteTest.java
if errorlevel 1 (
  echo FEHLER beim Uebersetzen des Pruefprogramms.
  pause
  exit /b 1
)
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "lib\FStyle.jar;build\check" LogKarteTest
echo.
echo Fertig. karte_test.log, karte_vorschau.png und container_vorschau.png liegen im Projektordner.
pause
