# Logistik_App

Eine Java-Desktopanwendung zur Visualisierung und Verwaltung von Containertransporten in Europa. Die interaktive Karte zeigt Städte, Länderzonen und Container; Aufträge, Fahrten, Zollvorgänge und Kosten lassen sich über den Leitstand nachvollziehen.

## Einblicke

![Europa-Karte mit Städten, Zonen und Containern](Claude%20outputs/karte_vorschau.png)

*Europakarte mit farblich gekennzeichneten Zonen und Waren.*

![Container während einer Fahrt zwischen zwei Städten](Claude%20outputs/ziehen_vorschau.png)

*Eine Containerbewegung wird direkt auf der Karte geplant.*

![Containerdarstellung nach Warenart](Claude%20outputs/container_vorschau.png)

*Die Warenart bestimmt die Darstellung des Containers.*

## Funktionen

- Europakarte mit Ländergrenzen, Städten, Zonen und Warenlegende
- Container auf der Karte verschieben; Fahrten, Grenzübertritte und Zollfreigaben verfolgen
- Aufträge planen und fällige Fahrten automatisch ausführen lassen
- Bestand, Fahrtverlauf, Zollfälle und Transportkosten im Leitstand einsehen
- Städte und Container anlegen sowie Stammdaten per CSV importieren oder exportieren
- Zeitleiste zur Ansicht früherer Zustände und eine integrierte Demo

## Voraussetzungen

- Windows
- JDK 21 (das NetBeans-Projekt ist auf Java 21 eingestellt)
- Apache Ant oder Apache NetBeans
- Oracle-Datenbank mit Oracle Spatial-Unterstützung und einem erreichbaren Schema `DEMO`

Die benötigten Java-Bibliotheken liegen im Ordner `lib`. Die Anwendung verbindet sich standardmäßig mit `jdbc:oracle:thin:@//localhost:1521/PDBORCL` als `DEMO`. Die im Quellcode hinterlegten Zugangsdaten sind nur für eine lokale Demo gedacht; für andere Umgebungen sollten die Verbindungsparameter überschrieben werden.

## Starten

### Mit NetBeans

1. Dieses Verzeichnis als bestehendes NetBeans-Projekt öffnen.
2. Sicherstellen, dass die Oracle-Datenbank erreichbar und das Schema vorbereitet ist.
3. Das Projekt ausführen.

### Mit Apache Ant

Im Projektverzeichnis bauen und starten:

```powershell
ant clean jar
ant run
```

Das erzeugte Archiv liegt unter `dist/Logistik_App.jar`. Beim direkten Start müssen die Bibliotheken aus `lib` im Classpath stehen:

```powershell
java -cp "dist\Logistik_App.jar;lib\*" com.dan.logistikapp.ui.LogistikApp
```

Für eine andere Datenbankverbindung können die Systemeigenschaften beim Start gesetzt werden:

```powershell
java -Dlogistik.db.url="jdbc:oracle:thin:@//localhost:1521/MEIN_SERVICE" `
     -Dlogistik.db.user="MEIN_BENUTZER" `
     -Dlogistik.db.password="MEIN_PASSWORT" `
     -cp "dist\Logistik_App.jar;lib\*" com.dan.logistikapp.ui.LogistikApp
```

## Datenbank einrichten

`db_setup.bat` führt die Skripte aus `db/` in der vorgesehenen Reihenfolge gegen die lokale Verbindung `PDBORCL` und den Benutzer `DEMO` aus. Voraussetzung ist, dass Datenbank, Listener und Benutzer bereits existieren. Das Ergebnis steht in `db_setup.log`.

**Achtung:** Der vollständige Neuaufbau beginnt mit `00_drop.sql` und löscht die vorhandenen Datenbankobjekte mit dem Präfix `LOG_` einschließlich ihrer Bewegungsdaten. Vorhandene `FCF_`-Objekte bleiben laut Einrichtungsskript unberührt. Den Neuaufbau nur ausführen, wenn das Löschen dieser Logistikdaten beabsichtigt ist.

Die GUI unterstützt abweichende Verbindungsdaten über `logistik.db.url`, `logistik.db.user` und `logistik.db.password` (siehe Startbeispiel). Das separate Einrichtungsskript `db_setup.bat` verwendet dagegen fest eingetragene lokale Verbindungsdaten.

## Projektstruktur

| Pfad | Inhalt |
| --- | --- |
| `src/com/dan/logistikapp/` | Java-Anwendung, Oberfläche, Karte, Geschäftslogik und Datenbankzugriff |
| `db/` | Oracle-DDL, PL/SQL-Pakete, Regeln, Beispieldaten und Geodaten |
| `tools/` | Hilfsprogramme zur Datenbankeinrichtung und Prüfung sowie Kartentests |
| `lib/` | Java-Abhängigkeiten einschließlich Oracle JDBC und Oracle Spatial |
| `Claude outputs/` | PNG-Vorschauen für diese Dokumentation |

## Hilfsskripte

- `db_check.bat` prüft das Schema und schreibt ein Protokoll nach `db_check.log`.
- `karte_test.bat` prüft die Kartenkomponenten ohne Datenbank und erzeugt Vorschau-PNGs.
- `Verknuepfung.bat` erstellt eine Desktopverknüpfung zur Anwendung.
