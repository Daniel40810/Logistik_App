import com.dan.logistikapp.model.Iso6346;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Durchstich Phase 1: prueft das Schema LOG_* in DEMO gegen die
 * Anforderungen und gegen eine unabhaengige Java-Rechnung.
 *
 * <p>Alles, was hier schreibt, laeuft in EINER Transaktion, die am Ende
 * zurueckgerollt wird. Die Pruefung laesst sich deshalb beliebig oft
 * wiederholen und prueft den Betrieb, nicht nur den frischen Aufbau.
 * Letzte Pruefung: nach dem Rollback steht alles wie vorher.</p>
 *
 * <p>Regel aus FCurvedField: eine Pruefung, die einen Fehler ERWARTET,
 * ist nur gruen, wenn genau diese ORA-Nummer kommt. Eine Abfrage, die
 * aus anderem Grund scheitert, ist rot.</p>
 *
 * <p>Ergebnis: db_check.log im Projektordner.</p>
 *
 * @author Dan
 */
public final class LogDbCheck {

    private static final String URL  = "jdbc:oracle:thin:@//localhost:1521/PDBORCL";
    private static final String USER = "DEMO";
    private static final String PWD  = "de";

    /** Mittlerer Erdradius (IUGG) fuer die Kugelrechnung. */
    private static final double R_KM = 6371.0088;

    private static PrintWriter log;
    private static int ok;
    private static int rot;
    private static Connection con;
    /** Container, wie sie VOR dem Lauf festgeschrieben waren - das sieht eine zweite Verbindung. */
    private static int containerFest;

    private LogDbCheck() {
    }

    // ================================================================ main

    public static void main(String[] args) throws Exception {
        File base = new File(".").getCanonicalFile();
        File logFile = new File(base, "db_check.log");
        log = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(logFile), "UTF-8"), true);
        say("Logistik_App - Durchstich Phase 1 (Datenbank)");
        say("Zeit : " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date()));
        say("URL  : " + URL + "   Benutzer: " + USER);
        say("");

        // Auffangnetz um den ganzen Lauf: eine durchschlagende Exception
        // darf das Protokoll nicht mitten im Satz abschneiden.
        try {
            con = DriverManager.getConnection(URL, USER, PWD);
            con.setAutoCommit(false);
            int bewegungenVorher = zahl("SELECT COUNT(*) FROM log_bewegung");
            int containerVorher = zahl("SELECT COUNT(*) FROM log_container");
            containerFest = containerVorher;
            Map<Integer, String> standVorher = standKarte();
            // Ab hier kann der Auffang-Job fahren (Runde 2): seine Fahrten
            // sind festgeschrieben und gehoeren nicht zu diesem Lauf.
            String start = text("SELECT TO_CHAR(SYSTIMESTAMP, 'YYYY-MM-DD HH24:MI:SS.FF6') FROM dual");

            abschnitt("A  Aufbau", LogDbCheck::aufbau);
            abschnitt("B  Zonen - die Staedteliste aus dem Auftrag", LogDbCheck::zonen);
            abschnitt("C  Geometrie und Entfernung", LogDbCheck::geometrie);
            abschnitt("D  Kennung nach ISO 6346", LogDbCheck::kennung);
            abschnitt("E  Verschieben ueber LOG_API", LogDbCheck::verschieben);
            abschnitt("F  Sichten", LogDbCheck::sichten);
            abschnitt("H  Karte - Laender, Projektion, Anwendung", LogDbCheck::karte);
            abschnitt("I  Container auf der Karte", LogDbCheck::containerAufKarte);
            abschnitt("J  FDAL-Dauerlauf: dieselbe Verbindung, viele Abfragen", LogDbCheck::dauerlauf);
            abschnitt("K  Drag and Drop: Regeln, Entfernung, Fangen, Schreibweg", LogDbCheck::ziehen);
            abschnitt("L  Zonenregeln: Zoll-Liste LOG_ZOLL_V", LogDbCheck::zoll);
            abschnitt("M  Politur: Bestand, Historie, Demo-Dienst", LogDbCheck::politur);
            abschnitt("N  Runde 2: Kapazitaet, Kosten, Auftraege", LogDbCheck::runde2);
            abschnitt("P  Karte Runde 2: Tarif, Belegung, Kosten-Reiter, Demo-Dienst", LogDbCheck::karteRunde2);
            abschnitt("Q  Auftraege der App: Ausfuehren mit Fahrt, Demo-Dienst, Weg ueber FDAL", LogDbCheck::auftraegeApp);
            abschnitt("R  Zeitreise: ganze Historie, SQL-Gegenprobe, Kosten um t, Prognose", LogDbCheck::zeitreise);

            abschnitt("G  Wiederholbarkeit");
            con.rollback();
            String seit = "TO_TIMESTAMP('" + start + "', 'YYYY-MM-DD HH24:MI:SS.FF6')";
            int jobFahrten = zahl("SELECT COUNT(*) FROM log_bewegung WHERE ausgeloest = 'JOB' AND zeitpunkt >= " + seit);
            check("Nach dem Rollback: gleich viele Bewegungen (ohne Fahrten des Jobs)",
                    zahl("SELECT COUNT(*) FROM log_bewegung") - jobFahrten == bewegungenVorher,
                    bewegungenVorher + " Bewegungen" + (jobFahrten > 0 ? ", dazu " + jobFahrten + " vom Job" : ""));
            check("Nach dem Rollback: gleich viele Container",
                    zahl("SELECT COUNT(*) FROM log_container") == containerVorher,
                    containerVorher + " Container");
            Map<Integer, String> standNachher = standKarte();
            Statement js = con.createStatement();
            ResultSet jr = js.executeQuery("SELECT DISTINCT container_id FROM log_bewegung "
                    + "WHERE ausgeloest = 'JOB' AND zeitpunkt >= " + seit);
            int ohne = 0;
            while (jr.next()) {
                standVorher.remove(jr.getInt(1));
                standNachher.remove(jr.getInt(1));
                ohne++;
            }
            jr.close();
            js.close();
            check("Nach dem Rollback: jeder Container an seinem Platz, gleicher Status",
                    standVorher.equals(standNachher),
                    "Standortliste verglichen" + (ohne > 0 ? ", " + ohne + " vom Job bewegte ausgenommen" : ""));

            abschnitt("O  Nebenlaeufigkeit: zwei eigene Sitzungen, nichts wird gespeichert", LogDbCheck::nebenlaeufig);
            abschnitt("S  Demo mit dem echten Stand: alle Schritte, nichts gespeichert", LogDbCheck::demoEcht);
        } catch (Throwable t) {
            rot++;
            say("");
            say("[ABBRUCH] " + t);
            for (StackTraceElement e : t.getStackTrace()) {
                if (e.getClassName().startsWith("LogDbCheck")) {
                    say("          bei " + e);
                }
            }
        } finally {
            if (con != null) {
                try {
                    con.rollback();
                    con.close();
                } catch (SQLException ignore) {
                    // nichts mehr zu retten
                }
            }
        }

        say("");
        say("=========================================================");
        say("Ergebnis: " + ok + "/" + (ok + rot) + " gruen" + (rot == 0 ? "" : "   (" + rot + " ROT)"));
        log.close();
        System.out.println();
        System.out.println("Protokoll: " + logFile.getAbsolutePath());
        // Abschnitt S startet Swing-Timer; ohne exit liefe das Programm weiter
        System.exit(0);
    }

    // ================================================================ A

    private static void aufbau() throws SQLException {
        check("Fuenf LOG-Tabellen vorhanden",
                zahl("SELECT COUNT(*) FROM user_tables WHERE table_name IN "
                        + "('LOG_LAND','LOG_ORT','LOG_WARE','LOG_CONTAINER','LOG_BEWEGUNG')") == 5, null);
        int ungueltig = zahl("SELECT COUNT(*) FROM user_objects WHERE status <> 'VALID' "
                + "AND object_name LIKE 'LOG\\_%' ESCAPE '\\'");
        check("Keine ungueltigen LOG-Objekte", ungueltig == 0, ungueltig + " ungueltig");
        check("LOG_API Spezifikation und Rumpf gueltig",
                zahl("SELECT COUNT(*) FROM user_objects WHERE object_name = 'LOG_API' "
                        + "AND object_type IN ('PACKAGE','PACKAGE BODY') AND status = 'VALID'") == 2, null);
        check("Fuenf Sichten vorhanden",
                zahl("SELECT COUNT(*) FROM user_views WHERE view_name IN "
                        + "('LOG_ORT_V','LOG_CONTAINER_V','LOG_BESTAND_V','LOG_BEWEGUNG_V','LOG_LAND_V')") == 5, null);
        // Namentlich statt gezaehlt: Runde 2 brachte LOG_AUFTRAG_SEQ dazu, und
        // eine blosse Zahl im Text hat einmal "4" gemeldet, als es 5 waren.
        int identity = zahl("SELECT COUNT(*) FROM user_tab_identity_cols WHERE table_name LIKE 'LOG\\_%' ESCAPE '\\'");
        int seqSoll = zahl("SELECT COUNT(*) FROM user_sequences WHERE sequence_name IN "
                + "('LOG_ORT_SEQ', 'LOG_WARE_SEQ', 'LOG_CONTAINER_SEQ', 'LOG_BEWEGUNG_SEQ', 'LOG_AUFTRAG_SEQ')");
        int seqAlle = zahl("SELECT COUNT(*) FROM user_sequences WHERE sequence_name LIKE 'LOG\\_%' ESCAPE '\\'");
        check("Schluessel ueber Sequenz, keine IDENTITY-Spalte",
                identity == 0 && seqSoll == 5 && seqAlle == 5,
                seqAlle + " LOG-Sequenzen (" + seqSoll + " von 5 erwarteten), " + identity + " IDENTITY-Spalten");
        String idx = text("SELECT domidx_opstatus FROM user_indexes WHERE index_name = 'LOG_ORT_SX'");
        check("Spatial-Index auf den Staedten benutzbar", "VALID".equals(idx), "Status " + idx);
    }

    // ================================================================ B

    private static void zonen() throws SQLException {
        Map<String, String> soll = new LinkedHashMap<String, String>();
        soll.put("München", "INLAND");
        soll.put("Hamburg", "INLAND");
        soll.put("Köln", "INLAND");
        soll.put("Berlin", "INLAND");
        soll.put("Paris", "EU");
        soll.put("Warschau", "EU");
        soll.put("Barcelona", "EU");
        soll.put("Oslo", "DRITTLAND");
        soll.put("London", "DRITTLAND");
        soll.put("Zürich", "DRITTLAND");
        for (Map.Entry<String, String> e : soll.entrySet()) {
            String ist = text1("SELECT zone_typ FROM log_ort_v WHERE name = ?", e.getKey());
            check(e.getKey() + " ist " + e.getValue(), e.getValue().equals(ist), "Datenbank: " + ist);
        }
        check("Genau zehn Staedte, keine ueberzaehlige",
                zahl("SELECT COUNT(*) FROM log_ort") == soll.size(), null);
        check("Genau ein Land ist Inland, und das ist DE",
                "DE".equals(text("SELECT LISTAGG(iso2, ',') WITHIN GROUP (ORDER BY iso2) "
                        + "FROM log_land WHERE inland = 'J'")), null);
        check("Schweiz: Schengen ja, Zollunion nein",
                "J/N".equals(text("SELECT schengen || '/' || zollunion FROM log_land WHERE iso2 = 'CH'")),
                "genau die Unterscheidung, an der der Zoll haengt");
        erwarteFehler("Zweites Inland-Land wird abgewiesen", 1,
                "UPDATE log_land SET inland = 'J' WHERE iso2 = 'FR'");
        erwarteFehler("EU-Mitglied ausserhalb der Zollunion wird abgewiesen", 2290,
                "UPDATE log_land SET zollunion = 'N' WHERE iso2 = 'PL'");
    }

    // ================================================================ C

    private static void geometrie() throws SQLException {
        int ungueltig = zahl("SELECT COUNT(*) FROM log_ort o "
                + "WHERE SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(o.lage, 0.05) <> 'TRUE'");
        check("Alle Stadtpunkte sind gueltige Geometrien", ungueltig == 0, ungueltig + " ungueltig");
        check("Alle Stadtpunkte in SRID 8307",
                zahl("SELECT COUNT(*) FROM log_ort o WHERE o.lage.sdo_srid = 8307") == 10, null);
        check("Laenge vor Breite: alle Punkte liegen in Europa",
                zahl("SELECT COUNT(*) FROM log_ort_v WHERE laenge BETWEEN -11 AND 32 "
                        + "AND breite BETWEEN 35 AND 72") == 10,
                "vertauschte Achsen landeten in Afrika oder im Eismeer");

        // Alle Paare: Oracle (Ellipsoid) gegen Java (Kugel). Die Kugel
        // weicht bis etwa 0,5 % ab - mehr hiesse, eine Seite rechnet falsch.
        List<double[]> orte = new ArrayList<double[]>();
        List<Integer> ids = new ArrayList<Integer>();
        List<String> namen = new ArrayList<String>();
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT ort_id, name, laenge, breite FROM log_ort_v ORDER BY ort_id");
        while (rs.next()) {
            ids.add(rs.getInt(1));
            namen.add(rs.getString(2));
            orte.add(new double[] {rs.getDouble(3), rs.getDouble(4)});
        }
        rs.close();
        st.close();

        PreparedStatement ps = con.prepareStatement("SELECT log_api.distanz_m(?, ?), log_api.distanz_m(?, ?) FROM dual");
        double maxAbw = 0;
        String maxPaar = "";
        int paare = 0;
        boolean symmetrisch = true;
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                ps.setInt(1, ids.get(i));
                ps.setInt(2, ids.get(j));
                ps.setInt(3, ids.get(j));
                ps.setInt(4, ids.get(i));
                ResultSet r = ps.executeQuery();
                r.next();
                double db = r.getDouble(1);
                double rueck = r.getDouble(2);
                r.close();
                double kugel = haversineM(orte.get(i), orte.get(j));
                double abw = Math.abs(db - kugel) / kugel;
                if (abw > maxAbw) {
                    maxAbw = abw;
                    maxPaar = namen.get(i) + "-" + namen.get(j);
                }
                if (Math.abs(db - rueck) > 1) {
                    symmetrisch = false;
                }
                paare++;
            }
        }
        ps.close();
        check("Entfernung aller " + paare + " Paare: Oracle und Java einig",
                paare == 45 && maxAbw < 0.0075,
                String.format("groesste Abweichung %.3f %% (%s)", maxAbw * 100, maxPaar));
        check("Entfernung ist symmetrisch", symmetrisch, null);

        int hamOslo = zahl("SELECT log_api.distanz_m(a.ort_id, b.ort_id) FROM log_ort a, log_ort b "
                + "WHERE a.name = 'Hamburg' AND b.name = 'Oslo'");
        check("Hamburg-Oslo rund 710 km Luftlinie", hamOslo > 690000 && hamOslo < 730000,
                String.format("%.1f km", hamOslo / 1000.0));
    }

    private static double haversineM(double[] a, double[] b) {
        double la1 = Math.toRadians(a[1]);
        double la2 = Math.toRadians(b[1]);
        double dLa = la2 - la1;
        double dLo = Math.toRadians(b[0] - a[0]);
        double h = Math.sin(dLa / 2) * Math.sin(dLa / 2)
                + Math.cos(la1) * Math.cos(la2) * Math.sin(dLo / 2) * Math.sin(dLo / 2);
        return 2 * R_KM * 1000 * Math.asin(Math.sqrt(h));
    }

    // ================================================================ D

    private static void kennung() throws SQLException {
        // Fremde Referenz, nicht selbst ausgerechnet: das gaengige
        // Lehrbuchbeispiel zur ISO 6346.
        check("Referenz CSQU 305438 3 (Datenbank)",
                zahl("SELECT log_api.pruefziffer('CSQU305438') FROM dual") == 3, null);
        check("Referenz CSQU 305438 3 (Java)", Iso6346.pruefziffer("CSQU305438") == 3, null);
        check("DANU 100001 5 gueltig, DANU 100001 6 nicht",
                "J".equals(text("SELECT log_api.kennung_gueltig('DANU 100001 5') FROM dual"))
                && "N".equals(text("SELECT log_api.kennung_gueltig('DANU1000016') FROM dual")), null);
        check("Formatfehler werden erkannt (Kategorie X, Ziffer zu wenig)",
                "N".equals(text("SELECT log_api.kennung_gueltig('DANX1000015') FROM dual"))
                && "N".equals(text("SELECT log_api.kennung_gueltig('DANU100015') FROM dual")), null);

        // 1000 Serien in einem Rutsch: PL/SQL (Formel) gegen Java (Tabelle).
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT LEVEL - 1, log_api.neue_kennung(LEVEL - 1) "
                + "FROM dual CONNECT BY LEVEL <= 1000");
        int n = 0;
        int abw = 0;
        String erste = null;
        while (rs.next()) {
            String java = Iso6346.neueKennung("DANU", rs.getInt(1));
            if (!java.equals(rs.getString(2))) {
                abw++;
                if (erste == null) {
                    erste = rs.getString(2) + " <> " + java;
                }
            }
            n++;
        }
        rs.close();
        st.close();
        check("DANU-Serien 0..999: Datenbank und Java einig", n == 1000 && abw == 0,
                abw == 0 ? n + " verglichen" : abw + " Abweichungen, z.B. " + erste);

        // Zufaellige Eigentuemer: jeder Buchstabe kommt an jeder Stelle vor.
        Random rnd = new Random(6346);
        PreparedStatement ps = con.prepareStatement("SELECT log_api.pruefziffer(?) FROM dual");
        abw = 0;
        erste = null;
        for (int i = 0; i < 300; i++) {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < 3; k++) {
                sb.append((char) ('A' + rnd.nextInt(26)));
            }
            sb.append("UJZ".charAt(rnd.nextInt(3)));
            sb.append(String.format("%06d", rnd.nextInt(1000000)));
            ps.setString(1, sb.toString());
            ResultSet r = ps.executeQuery();
            r.next();
            if (r.getInt(1) != Iso6346.pruefziffer(sb.toString())) {
                abw++;
                if (erste == null) {
                    erste = sb + ": DB " + r.getInt(1) + ", Java " + Iso6346.pruefziffer(sb.toString());
                }
            }
            r.close();
        }
        ps.close();
        check("300 zufaellige Eigentuemer: Datenbank und Java einig", abw == 0,
                abw == 0 ? "alle Buchstaben an allen Stellen" : abw + " Abweichungen, z.B. " + erste);

        int schlecht = 0;
        st = con.createStatement();
        rs = st.executeQuery("SELECT kennung FROM log_container");
        int alle = 0;
        while (rs.next()) {
            alle++;
            if (!Iso6346.gueltig(rs.getString(1))) {
                schlecht++;
            }
        }
        rs.close();
        st.close();
        check("Alle Container-Kennungen gueltig (von Java nachgeprueft)", alle > 0 && schlecht == 0,
                alle + " Container");
        check("Erster Container heisst DANU 100001 5",
                "DANU 100001 5".equals(text("SELECT kennung_anzeige FROM log_container_v "
                        + "WHERE container_id = (SELECT MIN(container_id) FROM log_container)")), null);

        int ware = zahl("SELECT MIN(ware_id) FROM log_ware");
        int ort = zahl("SELECT MIN(ort_id) FROM log_ort");
        erwarteFehler("Trigger weist falsche Pruefziffer ab", 20010,
                "INSERT INTO log_container (kennung, ware_id, ort_id) VALUES ('DANU1000016', "
                + ware + ", " + ort + ")");
        boolean fremdOk;
        try {
            exec("INSERT INTO log_container (kennung, ware_id, ort_id) VALUES ('csqu 305438 3', "
                    + ware + ", " + ort + ")");
            fremdOk = "CSQU3054383".equals(text("SELECT kennung FROM log_container WHERE kennung = 'CSQU3054383'"));
        } catch (SQLException e) {
            fremdOk = false;
            say("         " + einzeilig(e.getMessage()));
        }
        check("Fremdkennung mit Leerzeichen und klein wird kompakt gespeichert", fremdOk, "csqu 305438 3 -> CSQU3054383");
    }

    // ================================================================ E

    private static void verschieben() throws SQLException {
        platzSchaffen();
        int hamburg = ortId("Hamburg");
        int koeln = ortId("Köln");
        int paris = ortId("Paris");
        int zuerich = ortId("Zürich");
        int oslo = ortId("Oslo");
        int barcelona = ortId("Barcelona");

        // Ein Container, der in Hamburg bereitsteht: der bekommt eine Reise. Steht dort
        // keiner (echte Fahrten in DEMO), holt bereiterIn einen hin - die Kette unten
        // zaehlt deshalb nur die Fahrten ab hier.
        int c = bereiterIn(hamburg, 20);
        long abBewegung = zahl("SELECT NVL(MAX(bewegung_id), 0) FROM log_bewegung");
        say("   Testcontainer: " + text("SELECT kennung_anzeige || ' (' || ware || ')' "
                + "FROM log_container_v WHERE container_id = " + c));

        fahrt("Hamburg -> Köln: Inland, kein Zoll", c, koeln, "INLAND", "INLAND", "N", "BEREIT");
        fahrt("Köln -> Paris: Inland -> EU, kein Zoll", c, paris, "INLAND", "EU", "N", "BEREIT");
        fahrt("Paris -> Zürich: EU -> Drittland, Zoll", c, zuerich, "EU", "DRITTLAND", "J", "ZOLL");
        erwarteFehler("Verschieben beim Zoll wird abgewiesen", 20021, call(c, oslo));
        exec("BEGIN log_api.zoll_freigeben(" + c + "); END;");
        check("Zoll freigegeben -> BEREIT",
                "BEREIT".equals(text("SELECT status FROM log_container WHERE container_id = " + c)), null);
        erwarteFehler("Zweite Freigabe wird abgewiesen", 20024,
                "BEGIN log_api.zoll_freigeben(" + c + "); END;");
        fahrt("Zürich -> Oslo: Drittland -> Drittland, andere Laender, Zoll", c, oslo,
                "DRITTLAND", "DRITTLAND", "J", "ZOLL");
        exec("BEGIN log_api.zoll_freigeben(" + c + "); END;");
        erwarteFehler("Verschieben an den eigenen Standort wird abgewiesen", 20020, call(c, oslo));
        erwarteFehler("Unbekannter Container wird abgewiesen", 20022, call(-1, oslo));
        erwarteFehler("Unbekannter Zielort wird abgewiesen", 20023, call(c, -1));

        int cb = bereiterIn(barcelona, 20);
        fahrt("Barcelona -> Paris: EU -> EU, kein Zoll", cb, paris, "EU", "EU", "N", "BEREIT");

        // Die Reise als Kette: jedes Ziel ist der naechste Start.
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT von_ort_id, nach_ort_id FROM log_bewegung "
                + "WHERE container_id = " + c + " AND bewegung_id > " + abBewegung + " ORDER BY bewegung_id");
        List<int[]> kette = new ArrayList<int[]>();
        while (rs.next()) {
            kette.add(new int[] {rs.getInt(1), rs.getInt(2)});
        }
        rs.close();
        st.close();
        boolean lueckenlos = kette.size() == 4 && kette.get(0)[0] == hamburg && kette.get(3)[1] == oslo;
        for (int i = 1; i < kette.size(); i++) {
            lueckenlos &= kette.get(i)[0] == kette.get(i - 1)[1];
        }
        check("Historie lueckenlos: Hamburg -> Köln -> Paris -> Zürich -> Oslo", lueckenlos,
                kette.size() + " Bewegungen");

        // Die Zone steht als Schnappschuss in der Bewegung. Frankreich
        // probeweise aus der EU nehmen: die alte Fahrt bleibt eine EU-Fahrt.
        exec("UPDATE log_land SET eu_mitglied = 'N', zollunion = 'N' WHERE iso2 = 'FR'");
        String heute = text("SELECT log_api.zone_von(" + paris + ") FROM dual");
        String damals = text("SELECT nach_zone FROM log_bewegung WHERE container_id = " + c
                + " AND nach_ort_id = " + paris);
        check("Historie behaelt die Zone von damals", "DRITTLAND".equals(heute) && "EU".equals(damals),
                "Paris heute " + heute + ", in der Bewegung " + damals);
        exec("UPDATE log_land SET eu_mitglied = 'J', zollunion = 'J' WHERE iso2 = 'FR'");
    }

    /** Eine Fahrt ueber LOG_API und alles, was danach stimmen muss. */
    private static void fahrt(String name, int container, int ziel,
            String vonZone, String nachZone, String zoll, String status) throws SQLException {
        int von = zahl("SELECT ort_id FROM log_container WHERE container_id = " + container);
        CallableStatement cs = con.prepareCall("{call log_api.verschieben(?, ?, ?)}");
        cs.setInt(1, container);
        cs.setInt(2, ziel);
        cs.registerOutParameter(3, Types.NUMERIC);
        try {
            cs.execute();
        } catch (SQLException e) {
            cs.close();
            check(name, false, "ORA-" + e.getErrorCode() + " " + einzeilig(e.getMessage()));
            return;
        }
        int bew = cs.getInt(3);
        cs.close();

        PreparedStatement ps = con.prepareStatement(
                "SELECT b.von_ort_id, b.nach_ort_id, b.von_zone, b.nach_zone, b.zoll, b.distanz_m, "
                + "       c.ort_id, c.status, log_api.distanz_m(b.von_ort_id, b.nach_ort_id) "
                + "  FROM log_bewegung b JOIN log_container c ON c.container_id = b.container_id "
                + " WHERE b.bewegung_id = ?");
        ps.setInt(1, bew);
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            ps.close();
            check(name, false, "Bewegung " + bew + " nicht gefunden");
            return;
        }
        List<String> fehler = new ArrayList<String>();
        if (rs.getInt(1) != von) {
            fehler.add("von");
        }
        if (rs.getInt(2) != ziel) {
            fehler.add("nach");
        }
        if (!vonZone.equals(rs.getString(3))) {
            fehler.add("von_zone=" + rs.getString(3));
        }
        if (!nachZone.equals(rs.getString(4))) {
            fehler.add("nach_zone=" + rs.getString(4));
        }
        if (!zoll.equals(rs.getString(5))) {
            fehler.add("zoll=" + rs.getString(5));
        }
        if (rs.getLong(6) <= 0 || rs.getLong(6) != rs.getLong(9)) {
            fehler.add("distanz=" + rs.getLong(6));
        }
        if (rs.getInt(7) != ziel) {
            fehler.add("Container steht nicht am Ziel");
        }
        if (!status.equals(rs.getString(8))) {
            fehler.add("status=" + rs.getString(8));
        }
        long m = rs.getLong(6);
        rs.close();
        ps.close();
        check(name, fehler.isEmpty(),
                fehler.isEmpty() ? String.format("%.0f km, Status %s", m / 1000.0, status) : fehler.toString());
    }

    private static String call(int container, int ziel) {
        return "DECLARE v NUMBER; BEGIN log_api.verschieben(" + container + ", " + ziel + ", v); END;";
    }

    // ================================================================ F

    private static void sichten() throws SQLException {
        int alle = zahl("SELECT COUNT(*) FROM log_container");
        check("Jeder Container erscheint in LOG_CONTAINER_V",
                zahl("SELECT COUNT(*) FROM log_container_v") == alle, alle + " Container");
        check("Bestand zaehlt jeden Container genau einmal",
                zahl("SELECT SUM(anzahl) FROM log_bestand_v") == alle, null);
        check("TEU: 40-Fuss zaehlt doppelt",
                zahl("SELECT SUM(teu) FROM log_bestand_v")
                == alle + zahl("SELECT COUNT(*) FROM log_container WHERE groesse_fuss = 40"), null);
        check("Kuehlware faehrt im Reefer (xxR1), alles andere im Standardcontainer (xxG1)",
                zahl("SELECT COUNT(*) FROM log_container_v WHERE "
                        + "(ware_code = 'KUEHLWARE' AND typ_code NOT LIKE '__R1') "
                        + "OR (ware_code <> 'KUEHLWARE' AND typ_code NOT LIKE '__G1')") == 0
                && zahl("SELECT COUNT(*) FROM log_container_v WHERE ware_code = 'KUEHLWARE'") > 0, null);
        check("Typcode: 40 Fuss beginnt mit 42, 20 Fuss mit 22",
                zahl("SELECT COUNT(*) FROM log_container_v WHERE "
                        + "(groesse_fuss = 40 AND typ_code NOT LIKE '42%') "
                        + "OR (groesse_fuss = 20 AND typ_code NOT LIKE '22%')") == 0, null);
        check("Jede Ware hat eine gueltige Farbe und mindestens einen Container",
                zahl("SELECT COUNT(*) FROM log_ware w WHERE NOT EXISTS "
                        + "(SELECT 1 FROM log_container c WHERE c.ware_id = w.ware_id)") == 0, null);
        check("Bewegungssicht nennt Staedte und Kilometer",
                zahl("SELECT COUNT(*) FROM log_bewegung_v WHERE von_ort IS NULL OR nach_ort IS NULL "
                        + "OR distanz_km IS NULL") == 0
                && zahl("SELECT COUNT(*) FROM log_bewegung_v") == zahl("SELECT COUNT(*) FROM log_bewegung"),
                null);
    }

    // ================================================================ H

    private static void karte() throws SQLException {
        check("63 Laender, jedes mit Flaeche",
                zahl("SELECT COUNT(*) FROM log_land") == 63 && zahl("SELECT COUNT(grenze) FROM log_land") == 63,
                zahl("SELECT COUNT(grenze) FROM log_land") + " mit Flaeche");
        String ungueltig = text("SELECT LISTAGG(l.iso2, ',') WITHIN GROUP (ORDER BY l.iso2) FROM log_land l "
                + "WHERE SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(l.grenze, 0.05) <> 'TRUE'");
        check("Alle Landesflaechen gueltig", ungueltig == null, ungueltig == null ? null : "ungueltig: " + ungueltig);
        check("Alle Landesflaechen in SRID 8307",
                zahl("SELECT COUNT(*) FROM log_land l WHERE l.grenze.sdo_srid = 8307") == 63, null);
        String idx = text("SELECT domidx_opstatus FROM user_indexes WHERE index_name = 'LOG_LAND_SX'");
        check("Spatial-Index auf den Laendern benutzbar", "VALID".equals(idx), "Status " + idx);
        check("Zonen: 1 Inland, 27 EU (26 Staaten + Aland), 35 Drittland",
                "DRITTLAND:35,EU:27,INLAND:1".equals(text("SELECT LISTAGG(zone_typ || ':' || n, ',') "
                        + "WITHIN GROUP (ORDER BY zone_typ) FROM (SELECT zone_typ, COUNT(*) n FROM log_land GROUP BY zone_typ)")),
                null);
        check("EU 27 + Aland, Schengen 29 + Aland, Zollunion EU + Aland + Monaco + San Marino",
                zahl("SELECT COUNT(*) FROM log_land WHERE eu_mitglied = 'J'") == 28
                && zahl("SELECT COUNT(*) FROM log_land WHERE schengen = 'J'") == 30
                && zahl("SELECT COUNT(*) FROM log_land WHERE zollunion = 'J'") == 30, null);

        check("Jede Stadt liegt in ihrem Land (SDO_RELATE)",
                zahl("SELECT COUNT(*) FROM log_ort o JOIN log_land l ON l.iso2 = o.iso2 "
                        + "WHERE SDO_RELATE(l.grenze, o.lage, 'mask=CONTAINS+COVERS') = 'TRUE'") == 10, null);
        String fremd = text("SELECT LISTAGG(o.name || '/' || l.iso2, ',') WITHIN GROUP (ORDER BY o.name) "
                + "FROM log_ort o, log_land l WHERE l.iso2 <> o.iso2 "
                + "AND SDO_ANYINTERACT(l.grenze, o.lage) = 'TRUE'");
        check("Keine Stadt beruehrt ein fremdes Land", fremd == null, fremd);

        // Java-Projektion gegen Oracle
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT v.name, v.lx, v.ly, v.g.sdo_point.x, v.g.sdo_point.y "
                + "FROM (SELECT o.name, o.lage.sdo_point.x lx, o.lage.sdo_point.y ly, "
                + "             SDO_CS.TRANSFORM(o.lage, 3035) g FROM log_ort o) v");
        double maxM = 0;
        String wo = "";
        int n = 0;
        while (rs.next()) {
            double[] j = com.dan.logistikapp.geo.Laea3035.vor(rs.getDouble(2), rs.getDouble(3));
            double d = Math.hypot(j[0] - rs.getDouble(4), j[1] - rs.getDouble(5));
            if (d > maxM) {
                maxM = d;
                wo = rs.getString(1);
            }
            n++;
        }
        rs.close();
        st.close();
        check("Java-Projektion = SDO_CS.TRANSFORM nach EPSG:3035", n == 10 && maxM < 1.0,
                String.format("groesste Abweichung %.3f m (%s)", maxM, wo));

        // Durch die Anwendung: FDAL, Repositories, WKT, Modell
        com.dan.fdal.FDatabaseManager db = com.dan.logistikapp.db.LogDb.oeffnen();
        try {
            com.dan.logistikapp.db.DbKartenQuelle q = new com.dan.logistikapp.db.DbKartenQuelle(db);
            List<com.dan.logistikapp.model.Land> laender = q.laender();
            PreparedStatement ps = con.prepareStatement(
                    "SELECT SDO_UTIL.GETNUMVERTICES(l.grenze), SDO_GEOM.SDO_AREA(l.grenze, 0.05, 'unit=SQ_KM') "
                    + "FROM log_land l WHERE l.iso2 = ?");
            int vAbw = 0;
            String erste = null;
            double maxFl = 0;
            String flWo = "";
            double deQkm = 0;
            for (com.dan.logistikapp.model.Land l : laender) {
                ps.setString(1, l.getIso2());
                ResultSet r = ps.executeQuery();
                r.next();
                int ov = r.getInt(1);
                double oq = r.getDouble(2);
                r.close();
                if (l.getGrenze() == null || l.getGrenze().stuetzpunkte() != ov) {
                    vAbw++;
                    if (erste == null) {
                        erste = l.getIso2() + ": Oracle " + ov + ", Java "
                                + (l.getGrenze() == null ? "-" : l.getGrenze().stuetzpunkte());
                    }
                }
                if (l.getGrenze() != null && oq > 1000) {
                    double jq = l.getGrenze().flaecheQkm();
                    double abw = Math.abs(jq - oq) / oq;
                    if (abw > maxFl) {
                        maxFl = abw;
                        flWo = l.getIso2();
                    }
                    if ("DE".equals(l.getIso2())) {
                        deQkm = oq;
                    }
                }
            }
            ps.close();
            check("Anwendung liest 63 Laender ueber FDAL", laender.size() == 63, laender.size() + " Laender");
            check("SDO-Leser: jeder Stuetzpunkt wie bei Oracle", vAbw == 0,
                    vAbw == 0 ? null : vAbw + " Abweichungen, z.B. " + erste);
            check("Flaechentreu: Java-Flaeche = SDO_AREA (Laender ueber 1000 km2)", maxFl < 0.005,
                    String.format("groesste Abweichung %.3f %% (%s)", maxFl * 100, flWo));
            check("Deutschland rund 357 000 km2 (Oracle)", deQkm > 345000 && deQkm < 365000,
                    String.format("%.0f km2", deQkm));

            com.dan.logistikapp.karte.KartenModell m = com.dan.logistikapp.karte.KartenModell.aus(q);
            int drin = 0;
            for (com.dan.logistikapp.karte.KartenModell.OrtPunkt p : m.getPunkte()) {
                com.dan.logistikapp.karte.KartenModell.LandForm f = m.landBei(p.getX(), p.getY());
                if (f != null && f.getLand().getIso2().equals(p.getOrt().getIso2())) {
                    drin++;
                }
            }
            check("Karte: jede Stadt im eigenen Land, gerechnet in Java", m.getPunkte().size() == 10 && drin == 10,
                    drin + "/" + m.getPunkte().size());

            com.dan.logistikapp.karte.KartenPanel panel = new com.dan.logistikapp.karte.KartenPanel();
            panel.setSize(1400, 900);
            panel.setModell(m);
            panel.allesZeigen();
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1400, 900,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            panel.paint(g);
            g.dispose();
            try {
                javax.imageio.ImageIO.write(img, "png", new File("db_karte.png"));
            } catch (java.io.IOException e) {
                say("         db_karte.png nicht geschrieben: " + e.getMessage());
            }
            java.util.Set<Integer> farben = new java.util.HashSet<Integer>();
            for (int y = 0; y < img.getHeight(); y += 3) {
                for (int x = 0; x < img.getWidth(); x += 3) {
                    farben.add(img.getRGB(x, y) & 0xFFFFFF);
                }
            }
            boolean alle = true;
            for (com.dan.logistikapp.model.Zone z : com.dan.logistikapp.model.Zone.values()) {
                alle &= farben.contains(com.dan.logistikapp.karte.KartenFarben.flaeche(z).getRGB() & 0xFFFFFF);
            }
            check("Karte aus der Datenbank gezeichnet, alle drei Zonen sichtbar", alle, "db_karte.png");
        } finally {
            db.close();
        }
    }

    // ================================================================ I

    private static void containerAufKarte() throws SQLException {
        com.dan.fdal.FDatabaseManager db = com.dan.logistikapp.db.LogDb.oeffnen();
        try {
            com.dan.logistikapp.db.DbKartenQuelle q = new com.dan.logistikapp.db.DbKartenQuelle(db);
            List<com.dan.logistikapp.model.ContainerInfo> cs = q.container();
            List<com.dan.logistikapp.model.Ware> ws = q.waren();
            // FDAL hat eine eigene Verbindung und sieht nur Festgeschriebenes - also den
            // Stand vor diesem Lauf, nicht die offenen Aenderungen aus D und E.
            int sqlW = zahl("SELECT COUNT(*) FROM log_ware");
            check("Anwendung liest alle Container und Waren ueber FDAL", cs.size() == containerFest && ws.size() == sqlW,
                    cs.size() + " Container (fest: " + containerFest + "), " + ws.size() + " Waren");
            boolean sortiert = true;
            for (int i = 1; i < ws.size(); i++) {
                sortiert &= ws.get(i - 1).getSortierung() <= ws.get(i).getSortierung();
            }
            check("Waren in der Reihenfolge der Legende", sortiert, null);

            com.dan.logistikapp.karte.KartenModell m = com.dan.logistikapp.karte.KartenModell.aus(q);
            int ohneStadt = 0;
            int reeferFalsch = 0;
            for (com.dan.logistikapp.model.ContainerInfo c : cs) {
                if (m.punkt(c.getOrtId()) == null) {
                    ohneStadt++;
                }
                if (c.isReefer() != "KUEHLWARE".equals(c.getWareCode())) {
                    reeferFalsch++;
                }
            }
            check("Jeder Container steht an einer Stadt der Karte", ohneStadt == 0, ohneStadt + " ohne Stadt");
            check("Reefer genau bei Kuehlware (aus dem Typcode)", reeferFalsch == 0, reeferFalsch + " falsch");

            com.dan.logistikapp.karte.KartenPanel panel = new com.dan.logistikapp.karte.KartenPanel();
            panel.getContainerEbene().vorwaermen(m.getContainer());
            int leer = 0;
            java.util.Set<String> arten = new java.util.HashSet<String>();
            for (com.dan.logistikapp.model.ContainerInfo c : cs) {
                arten.add(c.getFarbe().getRGB() + "/" + c.getGroesseFuss() + "/" + c.isReefer());
                java.awt.image.BufferedImage b = panel.getContainerEbene().getCache()
                        .get(c.getFarbe(), c.getGroesseFuss(), c.isReefer(), 0).getBild();
                if ((b.getRGB(b.getWidth() / 2, b.getHeight() / 2) >>> 24) < 200) {
                    leer++;
                }
            }
            check("Jede Bauart einmal mit RayPhong gerendert, keines leer",
                    panel.getContainerEbene().getCache().groesse() == arten.size() && leer == 0,
                    arten.size() + " Bilder fuer " + cs.size() + " Container");

            panel.setSize(1400, 900);
            panel.setModell(m);
            panel.allesZeigen();
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1400, 900,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            panel.paint(g);
            g.dispose();
            try {
                javax.imageio.ImageIO.write(img, "png", new File("db_karte.png"));
            } catch (java.io.IOException e) {
                say("         db_karte.png nicht geschrieben: " + e.getMessage());
            }
            java.util.Set<Integer> ids = new java.util.HashSet<Integer>();
            for (com.dan.logistikapp.karte.ContainerEbene.Platz p : panel.getContainerEbene().getPlaetze()) {
                ids.add(p.getContainer().getContainerId());
            }
            check("Karte zeigt jeden Container genau einmal",
                    panel.getContainerEbene().getPlaetze().size() == cs.size() && ids.size() == cs.size(),
                    ids.size() + " auf der Karte, db_karte.png");
        } finally {
            db.close();
        }
    }

    // ================================================================ J

    /**
     * Dieselben Abfragen mehrfach auf EINER FDAL-Verbindung, mit Garbage
     * Collection dazwischen. Der Treiber gibt tempor&auml;re LOBs verz&ouml;gert frei,
     * wenn der Java-Gegenstand eingesammelt wird - Fehler daraus zeigen sich
     * erst bei einer sp&auml;teren Abfrage, deshalb mehrere Runden.
     */
    private static void dauerlauf() throws SQLException {
        com.dan.fdal.FDatabaseManager db = com.dan.logistikapp.db.LogDb.oeffnen();
        try {
            com.dan.logistikapp.db.DbKartenQuelle q = new com.dan.logistikapp.db.DbKartenQuelle(db);
            int gut = 0;
            int alle = 0;
            String erster = null;
            for (int runde = 1; runde <= 4; runde++) {
                String[] namen = {"laender", "orte", "waren", "container"};
                for (String n : namen) {
                    alle++;
                    try {
                        if ("laender".equals(n)) {
                            q.laender();
                        } else if ("orte".equals(n)) {
                            q.orte();
                        } else if ("waren".equals(n)) {
                            q.waren();
                        } else {
                            q.container();
                        }
                        gut++;
                    } catch (RuntimeException e) {
                        if (erster == null) {
                            erster = "Runde " + runde + " " + n + ": " + ursachen(e);
                        }
                    }
                }
                System.gc();
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            check("16 Abfragen hintereinander ueber FDAL, ohne Fehler", gut == alle,
                    gut + "/" + alle + (erster == null ? "" : "  erster Fehler: " + erster)
                    + "  Pool: " + db.getPool().size() + " offen, " + db.getPool().idleCount() + " frei");

        } finally {
            db.close();
        }
    }

    // ================================================================ K

    private static void ziehen() throws SQLException {
        platzSchaffen();
        // Staedte mit Land und Zugehoerigkeit, direkt aus der Datenbank
        List<Integer> ids = new ArrayList<Integer>();
        Map<Integer, com.dan.logistikapp.model.Land> land = new LinkedHashMap<Integer, com.dan.logistikapp.model.Land>();
        Map<Integer, double[]> lage = new LinkedHashMap<Integer, double[]>();
        Map<Integer, String> name = new LinkedHashMap<Integer, String>();
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT ort_id, name, iso2, zone_typ, eu_mitglied, zollunion, schengen, laenge, breite "
                + "FROM log_ort_v ORDER BY ort_id");
        while (rs.next()) {
            int id = rs.getInt(1);
            ids.add(id);
            name.put(id, rs.getString(2));
            land.put(id, new com.dan.logistikapp.model.Land(rs.getString(3).trim(), rs.getString(3),
                    com.dan.logistikapp.model.Zone.vonCode(rs.getString(4)), "J".equals(rs.getString(5)),
                    "J".equals(rs.getString(6)), "J".equals(rs.getString(7)), null));
            lage.put(id, new double[] {rs.getDouble(8), rs.getDouble(9)});
        }
        rs.close();
        st.close();

        PreparedStatement ps = con.prepareStatement("SELECT log_api.zoll_noetig(?, ?), log_api.distanz_m(?, ?) FROM dual");
        int zollAbw = 0;
        String zollErst = null;
        double maxM = 0;
        String maxWo = "";
        int paare = 0;
        for (int a : ids) {
            for (int b : ids) {
                if (a == b) {
                    continue;
                }
                ps.setInt(1, a);
                ps.setInt(2, b);
                ps.setInt(3, a);
                ps.setInt(4, b);
                ResultSet r = ps.executeQuery();
                r.next();
                boolean db = "J".equals(r.getString(1));
                long dbM = r.getLong(2);
                r.close();
                if (db != com.dan.logistikapp.model.Regeln.zollNoetig(land.get(a), land.get(b))) {
                    zollAbw++;
                    if (zollErst == null) {
                        zollErst = name.get(a) + "->" + name.get(b);
                    }
                }
                double j = com.dan.logistikapp.geo.Geodaesie.entfernungM(lage.get(a)[0], lage.get(a)[1],
                        lage.get(b)[0], lage.get(b)[1]);
                if (Math.abs(j - dbM) > maxM) {
                    maxM = Math.abs(j - dbM);
                    maxWo = name.get(a) + "-" + name.get(b);
                }
                paare++;
            }
        }
        ps.close();
        check("Zollvorschau beim Ziehen = LOG_API.zoll_noetig, alle " + paare + " Richtungen", paare == 90 && zollAbw == 0,
                zollAbw == 0 ? null : zollAbw + " Abweichungen, z.B. " + zollErst);
        check("Kilometer beim Ziehen (Vincenty) = LOG_API.distanz_m", maxM < 2,
                String.format("groesste Abweichung %.2f m (%s), Datenbank rundet auf ganze Meter", maxM, maxWo));

        // Fangen: SDO_NN gegen die naechste Stadt in Java
        PreparedStatement nn = con.prepareStatement("SELECT log_api.naechster_ort(?, ?, ?) FROM dual");
        int stadtTreffer = 0;
        for (int id : ids) {
            Integer t = naechster(nn, lage.get(id)[0] + 0.05, lage.get(id)[1] - 0.03, 100);
            if (t != null && t == id) {
                stadtTreffer++;
            }
        }
        check("SDO_NN findet zu jedem Punkt nahe einer Stadt genau diese", stadtTreffer == ids.size(),
                stadtTreffer + "/" + ids.size());
        check("Nordsee mit 100 km Suchradius: keine Stadt", naechster(nn, 3.0, 56.0, 100) == null, null);
        java.util.Random rnd = new java.util.Random(4711);
        int gleich = 0;
        for (int i = 0; i < 25; i++) {
            double lo = -8 + rnd.nextDouble() * 32;
            double la = 38 + rnd.nextDouble() * 24;
            Integer t = naechster(nn, lo, la, 4000);
            int best = -1;
            double bd = Double.MAX_VALUE;
            for (int id : ids) {
                double d = com.dan.logistikapp.geo.Geodaesie.entfernungM(lo, la, lage.get(id)[0], lage.get(id)[1]);
                if (d < bd) {
                    bd = d;
                    best = id;
                }
            }
            if (t != null && t == best) {
                gleich++;
            }
        }
        nn.close();
        check("25 Zufallspunkte: SDO_NN und Java waehlen dieselbe naechste Stadt", gleich == 25, gleich + "/25");
        erwarteFehler("Suchradius 0 wird abgewiesen", 20025, "SELECT log_api.naechster_ort(10, 50, 0) FROM dual");

        // Der Schreibweg der Anwendung, in dieser (zurueckgerollten) Transaktion
        int hamburg = ortId("Hamburg");
        int koeln = ortId("Köln");
        int zuerich = ortId("Zürich");
        int c = bereiterIn(hamburg, 20);
        com.dan.logistikapp.dienst.Fahrt f = com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c, koeln);
        long soll = zahl("SELECT log_api.distanz_m(" + hamburg + ", " + koeln + ") FROM dual");
        check("Schreibweg der App: Hamburg -> Koeln, Fahrt zurueckgelesen",
                "INLAND".equals(f.getVonZone()) && "INLAND".equals(f.getNachZone()) && !f.isZoll()
                && f.getDistanzM() == soll && zahl("SELECT ort_id FROM log_container WHERE container_id = " + c) == koeln,
                String.format("%.0f km, Bewegung %d", f.getDistanzM() / 1000.0, f.getBewegungId()));
        com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c, zuerich);
        com.dan.logistikapp.dienst.DienstFehler df = null;
        java.sql.Savepoint sp = con.setSavepoint();
        try {
            com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c, hamburg);
        } catch (SQLException e) {
            df = com.dan.logistikapp.db.DbContainerDienst.uebersetze(new com.dan.fdal.FDataAccessException("x", e));
        }
        con.rollback(sp);
        check("Abgelehnte Fahrt wird zu 'beim Zoll' uebersetzt, Meldung ohne ORA-Vorspann",
                df != null && df.getArt() == com.dan.logistikapp.dienst.DienstFehler.Art.BEIM_ZOLL
                && !df.getMessage().startsWith("ORA-"), df == null ? "kein Fehler" : df.getArt() + ": " + df.getMessage());
        com.dan.logistikapp.db.DbContainerDienst.zollFreigeben(con, c);
        check("Freigabe ueber den Schreibweg der App",
                "BEREIT".equals(text("SELECT status FROM log_container WHERE container_id = " + c)), null);
        check("LOG_LAND_V liefert GRENZE, die ORA-13199-Falle GRENZE_WKT ist weg",
                zahl("SELECT COUNT(*) FROM user_tab_columns WHERE table_name = 'LOG_LAND_V' AND column_name = 'GRENZE'") == 1
                && zahl("SELECT COUNT(*) FROM user_tab_columns WHERE table_name = 'LOG_LAND_V' "
                        + "AND column_name = 'GRENZE_WKT'") == 0, null);
    }

    private static Integer naechster(PreparedStatement nn, double lo, double la, double km) throws SQLException {
        nn.setDouble(1, lo);
        nn.setDouble(2, la);
        nn.setDouble(3, km);
        ResultSet r = nn.executeQuery();
        try {
            r.next();
            int v = r.getInt(1);
            return r.wasNull() ? null : v;
        } finally {
            r.close();
        }
    }

    // ================================================================ L Zoll

    private static void zoll() throws SQLException {
        platzSchaffen();
        check("Sicht LOG_ZOLL_V vorhanden und gueltig",
                zahl("SELECT COUNT(*) FROM user_objects WHERE object_name = 'LOG_ZOLL_V' AND status = 'VALID'") == 1,
                null);
        int zollVorher = zahl("SELECT COUNT(*) FROM log_container WHERE status = 'ZOLL'");
        check("LOG_ZOLL_V zeigt genau die Container mit Status ZOLL",
                zahl("SELECT COUNT(*) FROM log_zoll_v") == zollVorher, zollVorher + " beim Zoll");

        int koeln = ortId("Köln");
        int zuerich = ortId("Zürich");
        int c = zahl("SELECT MIN(container_id) FROM log_container WHERE ort_id = " + koeln + " AND status = 'BEREIT'");
        if (c == Integer.MIN_VALUE) {
            c = zahl("SELECT MIN(container_id) FROM log_container WHERE status = 'BEREIT'");
            com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c, koeln);
        }
        com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c, zuerich);
        String zeile = text("SELECT von_ort || '|' || ort || '|' || von_land || '|' || nach_land || '|' || von_zone "
                + "|| '|' || nach_zone || '|' || wartet_sek FROM log_zoll_v WHERE container_id = " + c);
        String[] t = zeile == null ? new String[0] : zeile.split("\\|");
        check("Koeln -> Zuerich: Zeile mit Grenze DE -> CH, Zonen INLAND -> DRITTLAND",
                t.length == 7 && "Köln".equals(t[0]) && "Zürich".equals(t[1]) && "DE".equals(t[2].trim())
                && "CH".equals(t[3].trim()) && "INLAND".equals(t[4]) && "DRITTLAND".equals(t[5])
                && Integer.parseInt(t[6]) >= 0 && Integer.parseInt(t[6]) <= 5, zeile);
        int w1 = zahl("SELECT wartet_sek FROM log_zoll_v WHERE container_id = " + c);
        try {
            Thread.sleep(2200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int w2 = zahl("SELECT wartet_sek FROM log_zoll_v WHERE container_id = " + c);
        check("Wartezeit rechnet die Datenbank: nach 2,2 s mehr Sekunden", w2 >= w1 + 2 && w2 <= w1 + 4,
                w1 + " s -> " + w2 + " s");

        // Der Lesepfad der App: ZollRepository.SQL_ALLE + MAPPER
        List<com.dan.logistikapp.model.ZollFall> faelle = new ArrayList<com.dan.logistikapp.model.ZollFall>();
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(com.dan.logistikapp.db.ZollRepository.SQL_ALLE);
        while (rs.next()) {
            faelle.add(com.dan.logistikapp.db.ZollRepository.MAPPER.map(rs));
        }
        rs.close();
        st.close();
        com.dan.logistikapp.model.ZollFall fall = faelle.isEmpty() ? null : faelle.get(faelle.size() - 1);
        check("App liest die Liste (SQL_ALLE + MAPPER), neuester Fall zuletzt, Farbe der Ware",
                faelle.size() == zollVorher + 1 && fall != null && fall.getContainerId() == c
                && "DE \u2192 CH".equals(fall.grenze()) && fall.getFarbe() != null
                && fall.getKennungAnzeige().length() == 13,
                fall == null ? faelle.size() + " Faelle" : faelle.size() + " Faelle, zuletzt " + fall.getKennungAnzeige()
                        + " " + fall.grenze() + ", " + fall.wartetSek(System.currentTimeMillis()) + " s");

        com.dan.logistikapp.db.DbContainerDienst.zollFreigeben(con, c);
        check("Nach der Freigabe verschwindet der Container aus der Liste",
                zahl("SELECT COUNT(*) FROM log_zoll_v WHERE container_id = " + c) == 0
                && zahl("SELECT COUNT(*) FROM log_zoll_v") == zollVorher, null);
    }

    // ================================================================ M Politur

    private static <T> List<T> lies(String sql, com.dan.fdal.FRowMapper<T> m, Integer p) throws SQLException {
        List<T> out = new ArrayList<T>();
        PreparedStatement ps = con.prepareStatement(sql);
        try {
            if (p != null) {
                ps.setInt(1, p);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                out.add(m.map(rs));
            }
            rs.close();
        } finally {
            ps.close();
        }
        return out;
    }

    private static void politur() throws SQLException {
        platzSchaffen();
        List<com.dan.logistikapp.model.Ort> orte =
                lies(com.dan.logistikapp.db.OrtRepository.SQL_ALLE, com.dan.logistikapp.db.OrtRepository.MAPPER, null);
        List<com.dan.logistikapp.model.Ware> waren =
                lies(com.dan.logistikapp.db.WareRepository.SQL_ALLE, com.dan.logistikapp.db.WareRepository.MAPPER, null);
        List<com.dan.logistikapp.model.ContainerInfo> stand = lies(com.dan.logistikapp.db.ContainerRepository.SQL_ALLE,
                com.dan.logistikapp.db.ContainerRepository.MAPPER, null);

        // Bestandsliste der App gegen LOG_BESTAND_V
        Map<String, String> ist = new java.util.TreeMap<String, String>();
        for (com.dan.logistikapp.model.Bestand.Stadt s
                : com.dan.logistikapp.model.Bestand.aus(orte, waren, stand)) {
            for (com.dan.logistikapp.model.Bestand.Anteil a : s.getAnteile()) {
                ist.put(s.getOrt().getOrtId() + "|" + a.getWareCode(),
                        a.getAnzahl() + "/" + a.getTeu() + "/" + a.getBeimZoll());
            }
        }
        Map<String, String> soll = new java.util.TreeMap<String, String>();
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT ort_id, ware_code, anzahl, teu, beim_zoll FROM log_bestand_v");
        while (rs.next()) {
            soll.put(rs.getInt(1) + "|" + rs.getString(2), rs.getInt(3) + "/" + rs.getInt(4) + "/" + rs.getInt(5));
        }
        rs.close();
        check("LOG_BESTAND_V = Bestandsliste der App (je Stadt und Ware: Anzahl, TEU, beim Zoll)",
                !soll.isEmpty() && soll.equals(ist), soll.equals(ist) ? soll.size() + " Zeilen"
                        : "Sicht " + soll + " / App " + ist);

        // Die juengsten Fahrten
        List<com.dan.logistikapp.model.Bewegung> letzte = lies(com.dan.logistikapp.db.BewegungRepository.SQL_LETZTE,
                com.dan.logistikapp.db.BewegungRepository.MAPPER, 5);
        boolean fallend = !letzte.isEmpty();
        for (int i = 1; i < letzte.size(); i++) {
            fallend &= letzte.get(i).getBewegungId() < letzte.get(i - 1).getBewegungId();
        }
        long maxId = zahl("SELECT MAX(bewegung_id) FROM log_bewegung");
        check("Fahrtenliste (SQL_LETZTE + MAPPER): hoechstens 5, neueste zuerst, Namen und Zeit gefuellt",
                letzte.size() <= 5 && fallend && letzte.get(0).getBewegungId() == maxId
                && letzte.get(0).getVonOrt() != null && letzte.get(0).getZeitpunktMs() > 0,
                letzte.size() + " Zeilen, oben " + (letzte.isEmpty() ? "-" : letzte.get(0).toString()));

        // Historie jedes Containers als Kette
        Map<Integer, Integer> standort = new java.util.HashMap<Integer, Integer>();
        for (com.dan.logistikapp.model.ContainerInfo c : stand) {
            standort.put(c.getContainerId(), c.getOrtId());
        }
        rs = st.executeQuery("SELECT container_id, von_ort_id, nach_ort_id, zeitpunkt FROM log_bewegung "
                + "ORDER BY container_id, bewegung_id");
        int container = 0;
        int bruch = 0;
        int zeitRueck = 0;
        String erster = null;
        int cid = -1;
        int zuletztNach = -1;
        java.sql.Timestamp zuletztZeit = null;
        while (rs.next()) {
            int c = rs.getInt(1);
            if (c != cid) {
                if (cid >= 0 && !Integer.valueOf(zuletztNach).equals(standort.get(cid))) {
                    bruch++;
                    erster = erster == null ? "Container " + cid + " endet nicht am Standort" : erster;
                }
                cid = c;
                container++;
                zuletztZeit = null;
            } else if (rs.getInt(2) != zuletztNach) {
                bruch++;
                erster = erster == null ? "Container " + c + ": Luecke vor Ort " + rs.getInt(2) : erster;
            }
            java.sql.Timestamp t = rs.getTimestamp(4);
            if (zuletztZeit != null && t.before(zuletztZeit)) {
                zeitRueck++;
            }
            zuletztZeit = t;
            zuletztNach = rs.getInt(3);
        }
        if (cid >= 0 && !Integer.valueOf(zuletztNach).equals(standort.get(cid))) {
            bruch++;
            erster = erster == null ? "Container " + cid + " endet nicht am Standort" : erster;
        }
        rs.close();
        st.close();
        check("Historie jedes Containers lueckenlos: Ziel = naechster Start, letzte Fahrt endet am Standort",
                container > 0 && bruch == 0, bruch == 0 ? container + " Container mit Fahrten" : bruch + " Brueche, " + erster);
        check("Zeitpunkte steigen mit der Bewegungsnummer", zeitRueck == 0, zeitRueck + " Rueckspruenge");

        // Historie waechst mit einer Fahrt der App
        int koeln = ortId("Köln");
        int c = zahl("SELECT MIN(container_id) FROM log_container WHERE status = 'BEREIT' AND ort_id <> " + koeln);
        List<com.dan.logistikapp.model.Bewegung> h0 = lies(com.dan.logistikapp.db.BewegungRepository.SQL_HISTORIE,
                com.dan.logistikapp.db.BewegungRepository.MAPPER, c);
        com.dan.logistikapp.dienst.Fahrt f = com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c, koeln);
        List<com.dan.logistikapp.model.Bewegung> h1 = lies(com.dan.logistikapp.db.BewegungRepository.SQL_HISTORIE,
                com.dan.logistikapp.db.BewegungRepository.MAPPER, c);
        com.dan.logistikapp.model.Bewegung neu = h1.isEmpty() ? null : h1.get(h1.size() - 1);
        check("Historie (SQL_HISTORIE): eine Fahrt mehr, zuletzt die neue, aelteste zuerst",
                h1.size() == h0.size() + 1 && neu != null && neu.getBewegungId() == f.getBewegungId()
                && neu.getNachOrtId() == koeln && neu.getDistanzM() == f.getDistanzM(),
                h0.size() + " -> " + h1.size() + " Fahrten");
        if (f.isZoll()) {
            // kam er von ausserhalb der Zollunion, wartet er jetzt in Koeln - erst freigeben
            com.dan.logistikapp.db.DbContainerDienst.zollFreigeben(con, c);
        }

        // Der Demo-Dienst rechnet wie LOG_API
        Map<String, com.dan.logistikapp.model.Land> laender = new LinkedHashMap<String, com.dan.logistikapp.model.Land>();
        st = con.createStatement();
        rs = st.executeQuery("SELECT DISTINCT iso2, zone_typ, eu_mitglied, zollunion, schengen FROM log_ort_v");
        while (rs.next()) {
            String iso = rs.getString(1).trim();
            laender.put(iso, new com.dan.logistikapp.model.Land(iso, iso,
                    com.dan.logistikapp.model.Zone.vonCode(rs.getString(2)), "J".equals(rs.getString(3)),
                    "J".equals(rs.getString(4)), "J".equals(rs.getString(5)), null));
        }
        rs.close();
        st.close();
        List<com.dan.logistikapp.model.ContainerInfo> jetzt = lies(com.dan.logistikapp.db.ContainerRepository.SQL_ALLE,
                com.dan.logistikapp.db.ContainerRepository.MAPPER, null);
        com.dan.logistikapp.dienst.SpeicherDienst sp = new com.dan.logistikapp.dienst.SpeicherDienst(orte,
                new ArrayList<com.dan.logistikapp.model.Land>(laender.values()), jetzt);
        int fahrten = 0;
        int abw = 0;
        String abwText = null;
        for (com.dan.logistikapp.model.Ort o : orte) {
            if (o.getOrtId() == zahl("SELECT ort_id FROM log_container WHERE container_id = " + c)) {
                continue;
            }
            com.dan.logistikapp.dienst.Fahrt db = com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c, o.getOrtId());
            com.dan.logistikapp.dienst.Fahrt mem;
            try {
                mem = sp.verschieben(c, o.getOrtId());
            } catch (com.dan.logistikapp.dienst.DienstFehler e) {
                abw++;
                abwText = abwText == null ? o.getName() + ": " + e.getMessage() : abwText;
                continue;
            }
            fahrten++;
            if (db.isZoll() != mem.isZoll() || !db.getVonZone().equals(mem.getVonZone())
                    || !db.getNachZone().equals(mem.getNachZone()) || Math.abs(db.getDistanzM() - mem.getDistanzM()) > 2) {
                abw++;
                abwText = abwText == null ? o.getName() + ": DB " + db.getNachZone() + "/" + db.isZoll() + "/"
                        + db.getDistanzM() + " m, Speicher " + mem.getNachZone() + "/" + mem.isZoll() + "/"
                        + mem.getDistanzM() + " m" : abwText;
            }
            if (db.isZoll()) {
                com.dan.logistikapp.db.DbContainerDienst.zollFreigeben(con, c);
                try {
                    sp.zollFreigeben(c);
                } catch (com.dan.logistikapp.dienst.DienstFehler e) {
                    abw++;
                }
            }
        }
        check("Demo-Dienst im Speicher rechnet wie LOG_API: Zonen, Zoll und Meter gleich",
                fahrten >= orte.size() - 1 && abw == 0,
                abw == 0 ? fahrten + " Fahrten verglichen" : abw + " Abweichungen, z.B. " + abwText);
    }

    /** container_id -> "ort:status", Stand aus Sicht von con. */
    private static Map<Integer, String> standKarte() throws SQLException {
        Map<Integer, String> m = new java.util.TreeMap<Integer, String>();
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT container_id, ort_id, status FROM log_container");
        while (rs.next()) {
            m.put(rs.getInt(1), rs.getInt(2) + ":" + rs.getString(3));
        }
        rs.close();
        st.close();
        return m;
    }

    // ================================================================ N Runde 2

    /** Auftrag mit Termin relativ zur Datenbankuhr, etwa "- INTERVAL '1' MINUTE". */
    private static long auftrag(int container, int ziel, String versatz) throws SQLException {
        CallableStatement cs = con.prepareCall("BEGIN log_api.auftrag_anlegen(?, ?, SYSTIMESTAMP "
                + versatz + ", ?); END;");
        try {
            cs.setInt(1, container);
            cs.setInt(2, ziel);
            cs.registerOutParameter(3, Types.NUMERIC);
            cs.execute();
            return cs.getLong(3);
        } finally {
            cs.close();
        }
    }

    private static com.dan.logistikapp.db.DbContainerDienst.Ausfuehrung ausfuehren(long auftrag) throws SQLException {
        return com.dan.logistikapp.db.DbContainerDienst.auftragAusfuehren(con, auftrag, "APP");
    }

    /** Ein Betrag exakt als BigDecimal - ueber TO_CHAR, damit keine Gleitkommazahl dazwischenkommt. */
    private static java.math.BigDecimal betrag(String sql) throws SQLException {
        String t = text("SELECT TO_CHAR((" + sql + "), 'TM9', 'NLS_NUMERIC_CHARACTERS=''.,''') FROM dual");
        return t == null ? null : new java.math.BigDecimal(t);
    }

    /** Ein bereiter Container an diesem Ort, am liebsten mit dieser Groesse. */
    private static int bereiterIn(int ort, int fuss) throws SQLException {
        int c = zahl("SELECT MIN(container_id) FROM log_container WHERE status = 'BEREIT' AND ort_id = " + ort
                + " AND groesse_fuss = " + fuss);
        if (c == Integer.MIN_VALUE) {
            c = zahl("SELECT MIN(container_id) FROM log_container WHERE status = 'BEREIT' AND ort_id = " + ort);
        }
        if (c == Integer.MIN_VALUE) {
            // niemand da: einen aus dem Inland holen
            c = zahl("SELECT MIN(c.container_id) FROM log_container c JOIN log_ort_v o ON o.ort_id = c.ort_id "
                    + "WHERE c.status = 'BEREIT' AND o.zone_typ = 'INLAND' AND c.ort_id <> " + ort);
            if (c == Integer.MIN_VALUE) {
                c = zahl("SELECT MIN(container_id) FROM log_container WHERE status = 'BEREIT' AND ort_id <> " + ort);
            }
            com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c, ort);
        }
        return c;
    }

    private static void runde2() throws SQLException {
        int spalten = zahl("SELECT COUNT(*) FROM user_tab_columns WHERE "
                + "(table_name = 'LOG_ORT' AND column_name = 'KAPAZITAET_TEU') "
                + "OR (table_name = 'LOG_WARE' AND column_name = 'FRACHT_EUR_TEU_KM') "
                + "OR (table_name = 'LOG_BEWEGUNG' AND column_name IN ('FRACHT_EUR', 'ZOLL_EUR', 'STANDGELD_EUR', "
                + "'FREIGEGEBEN_AM', 'AUSGELOEST'))");
        int tarife = zahl("SELECT COUNT(*) FROM log_tarif");
        int auftragTab = zahl("SELECT COUNT(*) FROM user_tables WHERE table_name = 'LOG_AUFTRAG'");
        int job = zahl("SELECT COUNT(*) FROM user_objects WHERE object_name = 'LOG_JOB' AND status = 'VALID'");
        check("Struktur Runde 2: 7 neue Spalten, LOG_TARIF mit 3 Werten, LOG_AUFTRAG, LOG_JOB gueltig",
                spalten == 7 && tarife == 3 && auftragTab == 1 && job == 2,
                spalten + " Spalten, " + tarife + " Tarife, LOG_JOB " + job + "/2");

        // --- Kapazitaet ------------------------------------------------
        check("Alle 10 Staedte haben eine Kapazitaet, keine ist ueberbelegt",
                zahl("SELECT COUNT(kapazitaet_teu) FROM log_ort") == 10
                && zahl("SELECT COUNT(*) FROM log_ort_v WHERE frei_teu < 0") == 0,
                text("SELECT LISTAGG(name || ' ' || belegt_teu || '/' || kapazitaet_teu, ', ') "
                        + "WITHIN GROUP (ORDER BY ort_id) FROM log_ort_v"));
        List<com.dan.logistikapp.model.Ort> orte =
                lies(com.dan.logistikapp.db.OrtRepository.SQL_ALLE, com.dan.logistikapp.db.OrtRepository.MAPPER, null);
        List<com.dan.logistikapp.model.Ware> waren =
                lies(com.dan.logistikapp.db.WareRepository.SQL_ALLE, com.dan.logistikapp.db.WareRepository.MAPPER, null);
        List<com.dan.logistikapp.model.ContainerInfo> stand = lies(com.dan.logistikapp.db.ContainerRepository.SQL_ALLE,
                com.dan.logistikapp.db.ContainerRepository.MAPPER, null);
        Map<Integer, Integer> javaTeu = new java.util.HashMap<Integer, Integer>();
        Map<Integer, Integer> javaKap = new java.util.HashMap<Integer, Integer>();
        for (com.dan.logistikapp.model.Bestand.Stadt b : com.dan.logistikapp.model.Bestand.aus(orte, waren, stand)) {
            javaTeu.put(b.getOrt().getOrtId(), b.getTeu());
            javaKap.put(b.getOrt().getOrtId(), b.getOrt().getKapazitaetTeu());
        }
        int abw = 0;
        String abwText = null;
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT ort_id, name, belegt_teu, log_api.belegt_teu(ort_id), frei_teu, "
                + "log_api.frei_teu(ort_id), kapazitaet_teu FROM log_ort_v");
        while (rs.next()) {
            int id = rs.getInt(1);
            boolean gleich = rs.getInt(3) == rs.getInt(4) && rs.getInt(5) == rs.getInt(6)
                    && Integer.valueOf(rs.getInt(3)).equals(javaTeu.get(id))
                    && Integer.valueOf(rs.getInt(7)).equals(javaKap.get(id));
            if (!gleich) {
                abw++;
                abwText = abwText == null ? rs.getString(2) + ": Sicht " + rs.getInt(3) + ", API " + rs.getInt(4)
                        + ", Java " + javaTeu.get(id) + ", Kapazitaet " + rs.getInt(7) + "/" + javaKap.get(id) : abwText;
            }
        }
        rs.close();
        st.close();
        check("Belegte und freie TEU: LOG_ORT_V = LOG_API = Bestandsliste der App; Kapazitaet ueber OrtRepository",
                abw == 0, abw == 0 ? orte.size() + " Staedte verglichen" : abwText);

        platzSchaffen();
        int paris = ortId("Paris");
        int hamburg = ortId("Hamburg");
        int kapParis = zahl("SELECT kapazitaet_teu FROM log_ort WHERE ort_id = " + paris);
        int belegtParis = zahl("SELECT belegt_teu FROM log_ort_v WHERE ort_id = " + paris);
        exec("UPDATE log_ort SET kapazitaet_teu = " + (belegtParis + 1) + " WHERE ort_id = " + paris);
        int c40 = zahl("SELECT MIN(container_id) FROM log_container WHERE groesse_fuss = 40 AND status = 'BEREIT' "
                + "AND ort_id <> " + paris);
        erwarteFehler("Paris mit 1 freien TEU: ein 40-Fuss-Container passt nicht", 20026, call(c40, paris));
        int c20a = zahl("SELECT MIN(container_id) FROM log_container WHERE groesse_fuss = 20 AND status = 'BEREIT' "
                + "AND ort_id <> " + paris);
        com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c20a, paris);
        int c20b = zahl("SELECT MIN(container_id) FROM log_container WHERE groesse_fuss = 20 AND status = 'BEREIT' "
                + "AND ort_id <> " + paris);
        String meldung = null;
        int ora = 0;
        java.sql.Savepoint sp = con.setSavepoint();
        try {
            com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c20b, paris);
        } catch (SQLException e) {
            ora = e.getErrorCode();
            meldung = com.dan.logistikapp.db.DbContainerDienst.uebersetze(
                    new com.dan.fdal.FDataAccessException("x", e)).getMessage();
        }
        con.rollback(sp);
        check("Danach ist Paris voll: auch 20 Fuss wird abgewiesen, die Meldung nennt Stadt und Zahlen",
                ora == 20026 && zahl("SELECT frei_teu FROM log_ort_v WHERE ort_id = " + paris) == 0
                && meldung != null && meldung.contains("Paris") && meldung.contains("TEU"), "ORA-" + ora + ": " + meldung);
        exec("UPDATE log_ort SET kapazitaet_teu = " + kapParis + " WHERE ort_id = " + paris);

        // --- Kosten ----------------------------------------------------
        com.dan.logistikapp.model.Tarif tarif = com.dan.logistikapp.db.TarifRepository.lade(con);
        int oslo = ortId("Oslo");
        int c = bereiterIn(hamburg, 20);
        java.math.BigDecimal vorschau = com.dan.logistikapp.db.DbContainerDienst.kostenVorschau(con, c, oslo);
        com.dan.logistikapp.dienst.Fahrt f = com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c, oslo);
        java.math.BigDecimal dbFracht = betrag("SELECT fracht_eur FROM log_bewegung WHERE bewegung_id = "
                + f.getBewegungId());
        java.math.BigDecimal dbZoll = betrag("SELECT zoll_eur FROM log_bewegung WHERE bewegung_id = "
                + f.getBewegungId());
        String code = text("SELECT w.code FROM log_container c JOIN log_ware w ON w.ware_id = c.ware_id "
                + "WHERE c.container_id = " + c);
        int teu = zahl("SELECT CASE groesse_fuss WHEN 40 THEN 2 ELSE 1 END FROM log_container WHERE container_id = " + c);
        java.math.BigDecimal javaFracht = tarif.fracht(f.getDistanzM(), teu, code);
        check("Kosten Hamburg -> Oslo: Fracht = Java-Rechnung auf den Cent, Zoll 85 EUR, Vorschau = Fracht + Zoll",
                dbFracht != null && javaFracht != null && dbFracht.compareTo(javaFracht) == 0
                && dbZoll != null && dbZoll.compareTo(new java.math.BigDecimal("85")) == 0
                && vorschau != null && vorschau.compareTo(dbFracht.add(dbZoll)) == 0,
                "DB " + dbFracht + " + " + dbZoll + ", Java " + javaFracht + ", Vorschau " + vorschau + " (" + code
                + ", " + teu + " TEU, " + f.getDistanzM() + " m)");
        com.dan.logistikapp.model.Ort oh = null;
        com.dan.logistikapp.model.Ort oo = null;
        for (com.dan.logistikapp.model.Ort o : orte) {
            oh = o.getOrtId() == hamburg ? o : oh;
            oo = o.getOrtId() == oslo ? o : oo;
        }
        long vincenty = Math.round(com.dan.logistikapp.geo.Geodaesie.entfernungM(oh.getLaenge(), oh.getBreite(),
                oo.getLaenge(), oo.getBreite()));
        java.math.BigDecimal karte = tarif.vorschau(vincenty, teu, code, true);
        check("Kostenvorschau der Karte (Vincenty statt SDO_DISTANCE) weicht hoechstens 1 Cent ab",
                karte != null && vorschau != null
                && karte.subtract(vorschau).abs().compareTo(new java.math.BigDecimal("0.01")) <= 0,
                "Karte " + karte + ", LOG_API " + vorschau);

        // Standgeld: die Ankunft 31 Minuten vorverlegen
        exec("UPDATE log_bewegung SET zeitpunkt = zeitpunkt - INTERVAL '31' MINUTE WHERE bewegung_id = "
                + f.getBewegungId());
        java.math.BigDecimal bisher = betrag("SELECT standgeld_bisher FROM log_zoll_v WHERE container_id = " + c);
        com.dan.logistikapp.db.DbContainerDienst.zollFreigeben(con, c);
        java.math.BigDecimal standgeld = betrag("SELECT standgeld_eur FROM log_bewegung WHERE bewegung_id = "
                + f.getBewegungId());
        int wartezeit = zahl("SELECT ROUND((CAST(freigegeben_am AS DATE) - CAST(zeitpunkt AS DATE)) * 86400) "
                + "FROM log_bewegung WHERE bewegung_id = " + f.getBewegungId());
        java.math.BigDecimal javaStand = tarif.standgeld(wartezeit * 1000L);
        check("Standgeld: 31 min beim Zoll = 3 angefangene Viertelstunden = 9 EUR (LOG_ZOLL_V zeigte es vorher)",
                standgeld != null && standgeld.compareTo(new java.math.BigDecimal("9")) == 0
                && bisher != null && bisher.compareTo(standgeld) == 0 && javaStand != null
                && javaStand.compareTo(standgeld) == 0,
                "Freigabe nach " + wartezeit + " s, " + standgeld + " EUR, vorher " + bisher + ", Java " + javaStand);

        // --- Auftraege -------------------------------------------------
        int muenchen = ortId("München");
        int berlin = ortId("Berlin");
        int zuerich = ortId("Zürich");
        int a = bereiterIn(hamburg, 20);
        String m0 = com.dan.logistikapp.db.DbContainerDienst.standMarke(con);
        long spaet = auftrag(a, muenchen, "- INTERVAL '1' MINUTE");
        long frueh = auftrag(a, berlin, "- INTERVAL '2' MINUTE");
        String m1 = com.dan.logistikapp.db.DbContainerDienst.standMarke(con);
        int rangFrueh = zahl("SELECT rang FROM log_auftrag_v WHERE auftrag_id = " + frueh);
        int rangSpaet = zahl("SELECT rang FROM log_auftrag_v WHERE auftrag_id = " + spaet);
        com.dan.logistikapp.db.DbContainerDienst.Ausfuehrung e1 = ausfuehren(spaet);
        check("Terminfolge: der spaetere Auftrag WARTET, LOG_AUFTRAG_V zeigt Rang 1 und 2",
                "WARTET".equals(e1.getErgebnis()) && rangFrueh == 1 && rangSpaet == 2,
                e1 + ", Rang " + rangFrueh + "/" + rangSpaet);
        com.dan.logistikapp.db.DbContainerDienst.Ausfuehrung e2 = ausfuehren(frueh);
        com.dan.logistikapp.db.DbContainerDienst.Ausfuehrung e3 = ausfuehren(spaet);
        check("Erst Hamburg -> Berlin, dann Berlin -> Muenchen; Fahrten als AUFTRAG, Auftrag kennt seine Fahrt",
                "ERLEDIGT".equals(e2.getErgebnis()) && "ERLEDIGT".equals(e3.getErgebnis())
                && zahl("SELECT ort_id FROM log_container WHERE container_id = " + a) == muenchen
                && zahl("SELECT von_ort_id FROM log_bewegung WHERE bewegung_id = " + e3.getBewegungId()) == berlin
                && "AUFTRAG".equals(text("SELECT ausgeloest FROM log_bewegung WHERE bewegung_id = " + e2.getBewegungId()))
                && zahl("SELECT bewegung_id FROM log_auftrag WHERE auftrag_id = " + frueh) == e2.getBewegungId()
                && "APP".equals(text("SELECT ausgefuehrt_von FROM log_auftrag WHERE auftrag_id = " + spaet)),
                e2 + ", " + e3);

        long zukunft = auftrag(a, hamburg, "+ INTERVAL '1' HOUR");
        com.dan.logistikapp.db.DbContainerDienst.Ausfuehrung e4 = ausfuehren(zukunft);
        com.dan.logistikapp.db.DbContainerDienst.auftragStornieren(con, zukunft);
        check("Termin in einer Stunde: NICHT_FAELLIG; storniert: Status STORNIERT mit Zeitpunkt",
                "NICHT_FAELLIG".equals(e4.getErgebnis())
                && "STORNIERT".equals(text("SELECT status FROM log_auftrag WHERE auftrag_id = " + zukunft))
                && zahl("SELECT COUNT(erledigt_am) FROM log_auftrag WHERE auftrag_id = " + zukunft) == 1,
                e4.toString());
        erwarteFehler("Stornierten Auftrag ausfuehren wird abgewiesen", 20028,
                "DECLARE e VARCHAR2(20); b NUMBER; BEGIN log_api.auftrag_ausfuehren(" + zukunft
                + ", 'APP', e, b); END;");
        erwarteFehler("Unbekannten Auftrag stornieren wird abgewiesen", 20027,
                "BEGIN log_api.auftrag_stornieren(-1); END;");

        // Blockiert: Container beim Zoll
        com.dan.logistikapp.db.DbContainerDienst.verschieben(con, a, zuerich);
        long block = auftrag(a, muenchen, "- INTERVAL '1' MINUTE");
        com.dan.logistikapp.db.DbContainerDienst.Ausfuehrung e5 = ausfuehren(block);
        String grund = text("SELECT grund FROM log_auftrag WHERE auftrag_id = " + block);
        check("Beim Zoll: Auftrag BLOCKIERT, bleibt OFFEN, Grund und Versuch stehen dran",
                "BLOCKIERT".equals(e5.getErgebnis())
                && "OFFEN".equals(text("SELECT status FROM log_auftrag WHERE auftrag_id = " + block))
                && zahl("SELECT versuche FROM log_auftrag WHERE auftrag_id = " + block) == 1
                && grund != null && grund.contains("Zoll") && !grund.startsWith("ORA-"), e5 + ": " + grund);
        com.dan.logistikapp.db.DbContainerDienst.zollFreigeben(con, a);
        com.dan.logistikapp.db.DbContainerDienst.Ausfuehrung e6 = ausfuehren(block);
        check("Nach der Freigabe faehrt derselbe Auftrag", "ERLEDIGT".equals(e6.getErgebnis())
                && zahl("SELECT ort_id FROM log_container WHERE container_id = " + a) == muenchen, e6.toString());

        // Blockiert: Ziel voll. Zuerich -> Muenchen war eine Zollfahrt - erst
        // freigeben, sonst meldet LOG_API zu Recht "beim Zoll" statt "voll".
        if ("ZOLL".equals(text("SELECT status FROM log_container WHERE container_id = " + a))) {
            com.dan.logistikapp.db.DbContainerDienst.zollFreigeben(con, a);
        }
        int belegtB = zahl("SELECT belegt_teu FROM log_ort_v WHERE ort_id = " + berlin);
        int kapB = zahl("SELECT kapazitaet_teu FROM log_ort WHERE ort_id = " + berlin);
        exec("UPDATE log_ort SET kapazitaet_teu = " + Math.max(1, belegtB) + " WHERE ort_id = " + berlin);
        long voll = auftrag(a, berlin, "- INTERVAL '1' MINUTE");
        com.dan.logistikapp.db.DbContainerDienst.Ausfuehrung e7 = ausfuehren(voll);
        String grundVoll = text("SELECT grund FROM log_auftrag WHERE auftrag_id = " + voll);
        check("Ziel voll: Auftrag BLOCKIERT mit Grund 'voll'", "BLOCKIERT".equals(e7.getErgebnis())
                && grundVoll != null && grundVoll.contains("voll"), e7 + ": " + grundVoll);
        exec("UPDATE log_ort SET kapazitaet_teu = " + kapB + " WHERE ort_id = " + berlin);
        com.dan.logistikapp.db.DbContainerDienst.auftragStornieren(con, voll);

        long schon = auftrag(a, muenchen, "- INTERVAL '1' MINUTE");
        com.dan.logistikapp.db.DbContainerDienst.Ausfuehrung e8 = ausfuehren(schon);
        check("Steht schon am Ziel: ERLEDIGT ohne Fahrt", "ERLEDIGT".equals(e8.getErgebnis())
                && e8.getBewegungId() == 0, e8.toString());
        String m2 = com.dan.logistikapp.db.DbContainerDienst.standMarke(con);
        check("stand_marke aendert sich mit Auftraegen und Fahrten", m0 != null && !m0.equals(m1) && !m1.equals(m2),
                m0 + " -> " + m2);

        // Lesepfade der App
        List<com.dan.logistikapp.model.Auftrag> liste = lies(com.dan.logistikapp.db.AuftragRepository.SQL_LISTE,
                com.dan.logistikapp.db.AuftragRepository.MAPPER, null);
        int offen = zahl("SELECT COUNT(*) FROM log_auftrag WHERE status = 'OFFEN'");
        boolean offenZuerst = true;
        boolean abgeschlossen = false;
        for (com.dan.logistikapp.model.Auftrag au : liste) {
            abgeschlossen |= !au.istOffen();
            offenZuerst &= !(abgeschlossen && au.istOffen());
        }
        check("Auftragsliste (SQL_LISTE + MAPPER): offene zuerst, dazu die abgeschlossenen",
                offenZuerst && liste.size() >= offen + 5, liste.size() + " Auftraege, " + offen + " offen");
        List<com.dan.logistikapp.model.Bewegung> hist = lies(com.dan.logistikapp.db.BewegungRepository.SQL_HISTORIE,
                com.dan.logistikapp.db.BewegungRepository.MAPPER, a);
        com.dan.logistikapp.model.Bewegung letzte = hist.isEmpty() ? null : hist.get(hist.size() - 1);
        check("Historie der App traegt Kosten und Ausloeser",
                letzte != null && letzte.getFrachtEur() != null && "AUFTRAG".equals(letzte.getAusgeloest())
                && letzte.getKostenEur().signum() > 0,
                letzte == null ? "-" : letzte + ", " + letzte.getKostenEur() + " EUR, " + letzte.getAusgeloest());
        java.math.BigDecimal summeSicht = betrag("SELECT SUM(kosten_eur) FROM log_kosten_v");
        java.math.BigDecimal summeRoh = betrag("SELECT SUM(NVL(fracht_eur, 0) + NVL(zoll_eur, 0) "
                + "+ NVL(standgeld_eur, 0)) FROM log_bewegung");
        check("LOG_KOSTEN_V verliert keinen Cent gegenueber LOG_BEWEGUNG", summeSicht != null && summeRoh != null
                && summeSicht.compareTo(summeRoh) == 0, summeSicht + " EUR");

        // Job
        String intervall = text("SELECT repeat_interval FROM user_scheduler_jobs WHERE job_name = 'LOG_AUFTRAG_JOB' "
                + "AND enabled = 'TRUE'");
        String letzterLauf = text("SELECT status FROM (SELECT status FROM user_scheduler_job_run_details "
                + "WHERE job_name = 'LOG_AUFTRAG_JOB' ORDER BY log_date DESC) WHERE ROWNUM = 1");
        check("Auffang-Job aktiv, jede Minute, letzter Lauf ohne Fehler",
                intervall != null && intervall.contains("MINUTELY")
                && (letzterLauf == null || "SUCCEEDED".equals(letzterLauf)),
                intervall + ", letzter Lauf: " + (letzterLauf == null ? "noch keiner protokolliert" : letzterLauf));
    }

    // ================================================================ P Karte Runde 2

    private static void karteRunde2() throws SQLException {
        platzSchaffen();
        com.dan.logistikapp.model.Tarif db = com.dan.logistikapp.db.TarifRepository.lade(con);
        com.dan.fdal.FDatabaseManager fdal = com.dan.logistikapp.db.LogDb.oeffnen();
        com.dan.logistikapp.karte.KartenModell m;
        try {
            m = com.dan.logistikapp.karte.KartenModell.aus(new com.dan.logistikapp.db.DbKartenQuelle(fdal));
        } finally {
            fdal.close();
        }
        com.dan.logistikapp.model.Tarif t = m.getTarif();
        boolean gleich = t != null;
        StringBuilder was = new StringBuilder();
        for (String k : new String[] {com.dan.logistikapp.model.Tarif.ZOLL_PAUSCHALE,
            com.dan.logistikapp.model.Tarif.STANDGELD_VIERTELSTUNDE, com.dan.logistikapp.model.Tarif.JOB_KARENZ_MIN}) {
            gleich &= t != null && t.wert(k) != null && t.wert(k).compareTo(db.wert(k)) == 0;
        }
        for (com.dan.logistikapp.model.Ware w : m.getWaren()) {
            gleich &= t != null && t.frachtSatz(w.getCode()) != null
                    && t.frachtSatz(w.getCode()).compareTo(db.frachtSatz(w.getCode())) == 0;
            was.append(w.getCode()).append(' ').append(t == null ? null : t.frachtSatz(w.getCode())).append(", ");
        }
        check("Karte laedt den Tarif ueber FDAL (DbKartenQuelle): 3 Tarife und alle Frachtsaetze wie in DEMO",
                gleich && m.getWaren().size() == 6, was.toString());

        // Belegung: das Kartenmodell (FDAL, festgeschrieben) gegen LOG_ORT_V aus einer frischen Sitzung
        Connection frisch = sitzung();
        int abw = 0;
        String erste = null;
        try {
            Statement st = frisch.createStatement();
            ResultSet rs = st.executeQuery("SELECT ort_id, name, kapazitaet_teu, belegt_teu, frei_teu FROM log_ort_v");
            while (rs.next()) {
                int id = rs.getInt(1);
                com.dan.logistikapp.karte.KartenModell.OrtPunkt p = m.punkt(id);
                Integer frei = m.freiTeu(id);
                boolean ok = p != null && Integer.valueOf(rs.getInt(3)).equals(p.getOrt().getKapazitaetTeu())
                        && m.belegtTeu(id) == rs.getInt(4) && frei != null && frei == rs.getInt(5);
                if (!ok) {
                    abw++;
                    erste = erste == null ? rs.getString(2) + ": Karte " + m.belegtTeu(id) + "/" + frei
                            + ", Sicht " + rs.getInt(4) + "/" + rs.getInt(5) : erste;
                }
            }
            rs.close();
            st.close();
        } finally {
            frisch.rollback();
            frisch.close();
        }
        check("Kartenmodell: Kapazitaet, belegte und freie TEU je Stadt = LOG_ORT_V (Fuellstandsring, Warnung beim Ziehen)",
                abw == 0, abw == 0 ? m.getPunkte().size() + " Staedte" : erste);

        // Kosten-Reiter liest LOG_KOSTEN_V ueber KostenRepository
        List<com.dan.logistikapp.model.KostenZeile> kz = lies(com.dan.logistikapp.db.KostenRepository.SQL_ALLE,
                com.dan.logistikapp.db.KostenRepository.MAPPER, null);
        java.math.BigDecimal summe = java.math.BigDecimal.ZERO;
        int fahrten = 0;
        for (com.dan.logistikapp.model.KostenZeile z : kz) {
            summe = summe.add(z.getKostenEur());
            fahrten += z.getFahrten();
        }
        java.math.BigDecimal roh = betrag("SELECT SUM(NVL(fracht_eur, 0) + NVL(zoll_eur, 0) + NVL(standgeld_eur, 0)) "
                + "FROM log_bewegung");
        check("Kosten-Reiter (KostenRepository): Summe und Fahrten wie LOG_BEWEGUNG",
                roh != null && summe.compareTo(roh) == 0 && fahrten == zahl("SELECT COUNT(*) FROM log_bewegung"),
                kz.size() + " Zeilen, " + fahrten + " Fahrten, " + summe + " EUR");

        // Die Meldung nach der Fahrt nennt, was LOG_API festgeschrieben hat
        int hamburg = ortId("Hamburg");
        int oslo = ortId("Oslo");
        int c = bereiterIn(hamburg, 20);
        java.math.BigDecimal vorschau = com.dan.logistikapp.db.DbContainerDienst.kostenVorschau(con, c, oslo);
        com.dan.logistikapp.dienst.Fahrt f = com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c, oslo);
        check("Fahrt der App traegt die Kosten (Fracht + Zoll) = LOG_API.kosten_vorschau",
                f.getKostenEur() != null && vorschau != null && f.getKostenEur().compareTo(vorschau) == 0,
                f.getKostenEur() + " EUR");
        com.dan.logistikapp.db.DbContainerDienst.zollFreigeben(con, c);

        // Demo-Dienst mit Tarif rechnet wie LOG_API - Kosten, nicht nur Zonen (vgl. M)
        List<com.dan.logistikapp.model.Ort> orte =
                lies(com.dan.logistikapp.db.OrtRepository.SQL_ALLE, com.dan.logistikapp.db.OrtRepository.MAPPER, null);
        Map<String, com.dan.logistikapp.model.Land> laender = new LinkedHashMap<String, com.dan.logistikapp.model.Land>();
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT DISTINCT iso2, zone_typ, eu_mitglied, zollunion, schengen FROM log_ort_v");
        while (rs.next()) {
            String iso = rs.getString(1).trim();
            laender.put(iso, new com.dan.logistikapp.model.Land(iso, iso,
                    com.dan.logistikapp.model.Zone.vonCode(rs.getString(2)), "J".equals(rs.getString(3)),
                    "J".equals(rs.getString(4)), "J".equals(rs.getString(5)), null));
        }
        rs.close();
        st.close();
        com.dan.logistikapp.dienst.SpeicherDienst sp = new com.dan.logistikapp.dienst.SpeicherDienst(orte,
                new ArrayList<com.dan.logistikapp.model.Land>(laender.values()),
                lies(com.dan.logistikapp.db.ContainerRepository.SQL_ALLE, com.dan.logistikapp.db.ContainerRepository.MAPPER,
                        null)).setTarif(db);
        int n = 0;
        java.math.BigDecimal maxAbw = java.math.BigDecimal.ZERO;
        String wo = "";
        for (com.dan.logistikapp.model.Ort o : orte) {
            if (o.getOrtId() == zahl("SELECT ort_id FROM log_container WHERE container_id = " + c)) {
                continue;
            }
            com.dan.logistikapp.dienst.Fahrt fd = com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c, o.getOrtId());
            com.dan.logistikapp.dienst.Fahrt fm;
            try {
                fm = sp.verschieben(c, o.getOrtId());
            } catch (com.dan.logistikapp.dienst.DienstFehler e) {
                maxAbw = new java.math.BigDecimal("999");
                wo = o.getName() + ": " + e.getMessage();
                break;
            }
            java.math.BigDecimal a = fd.getKostenEur().subtract(fm.getKostenEur()).abs();
            if (a.compareTo(maxAbw) > 0) {
                maxAbw = a;
                wo = o.getName() + " (DB " + fd.getKostenEur() + ", Speicher " + fm.getKostenEur() + ")";
            }
            if (fd.isZoll()) {
                com.dan.logistikapp.db.DbContainerDienst.zollFreigeben(con, c);
                try {
                    sp.zollFreigeben(c);
                } catch (com.dan.logistikapp.dienst.DienstFehler e) {
                    maxAbw = new java.math.BigDecimal("999");
                }
            }
            n++;
        }
        check("Demo-Dienst mit Tarif: Kosten jeder Fahrt wie LOG_API (hoechstens 1 Cent, Vincenty statt SDO)",
                n >= orte.size() - 1 && maxAbw.compareTo(new java.math.BigDecimal("0.01")) <= 0,
                n + " Fahrten, groesste Abweichung " + maxAbw + " EUR" + (wo.isEmpty() ? "" : " bei " + wo));
    }

    // ================================================================ R Zeitreise

    /** Stand um t in SQL - unabhaengig von Java: je Container Ort und Zollstatus. */
    private static final String SQL_STAND_UM =
            "SELECT c.container_id, "
          + "  COALESCE((SELECT MAX(b.nach_ort_id) KEEP (DENSE_RANK LAST ORDER BY b.zeitpunkt, b.bewegung_id) "
          + "              FROM log_bewegung b WHERE b.container_id = c.container_id AND b.zeitpunkt <= ?), "
          + "           (SELECT MIN(b.von_ort_id) KEEP (DENSE_RANK FIRST ORDER BY b.zeitpunkt, b.bewegung_id) "
          + "              FROM log_bewegung b WHERE b.container_id = c.container_id), c.ort_id) AS ort, "
          + "  NVL((SELECT MAX(CASE WHEN b.zoll = 'J' AND (b.freigegeben_am IS NULL OR b.freigegeben_am > ?) "
          + "                       THEN 'ZOLL' ELSE 'BEREIT' END) "
          + "              KEEP (DENSE_RANK LAST ORDER BY b.zeitpunkt, b.bewegung_id) "
          + "          FROM log_bewegung b WHERE b.container_id = c.container_id AND b.zeitpunkt <= ?), 'BEREIT') "
          + "    AS status "
          + "  FROM log_container c ORDER BY c.container_id";

    private static Map<Integer, String> standSql(long t) throws SQLException {
        Map<Integer, String> m = new LinkedHashMap<Integer, String>();
        PreparedStatement ps = con.prepareStatement(SQL_STAND_UM);
        try {
            java.sql.Timestamp ts = new java.sql.Timestamp(t);
            ps.setTimestamp(1, ts);
            ps.setTimestamp(2, ts);
            ps.setTimestamp(3, ts);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                m.put(rs.getInt(1), rs.getInt(2) + "/" + rs.getString(3));
            }
            rs.close();
        } finally {
            ps.close();
        }
        return m;
    }

    private static com.dan.logistikapp.model.Zeitreise zeitreiseAusDb(List<com.dan.logistikapp.model.Auftrag> a)
            throws SQLException {
        List<com.dan.logistikapp.model.ContainerInfo> stand =
                lies(com.dan.logistikapp.db.ContainerRepository.SQL_ALLE, com.dan.logistikapp.db.ContainerRepository.MAPPER,
                        null);
        List<com.dan.logistikapp.model.Bewegung> alle =
                lies(com.dan.logistikapp.db.BewegungRepository.SQL_ALLE, com.dan.logistikapp.db.BewegungRepository.MAPPER,
                        null);
        List<com.dan.logistikapp.model.Ort> orte =
                lies(com.dan.logistikapp.db.OrtRepository.SQL_ALLE, com.dan.logistikapp.db.OrtRepository.MAPPER, null);
        return new com.dan.logistikapp.model.Zeitreise(stand, alle, a, orte, System.currentTimeMillis());
    }

    private static void zeitreise() throws SQLException {
        // Der Weg der App: ganze Historie ueber FDAL (festgeschrieben) - gegen eine frische Sitzung
        com.dan.fdal.FDatabaseManager fdal = com.dan.logistikapp.db.LogDb.oeffnen();
        List<com.dan.logistikapp.model.Bewegung> app;
        try {
            app = new com.dan.logistikapp.db.DbContainerDienst(fdal).alleBewegungen();
        } catch (com.dan.logistikapp.dienst.DienstFehler e) {
            throw new SQLException(e.getMessage(), e);
        } finally {
            fdal.close();
        }
        int fest;
        Connection frisch = sitzung();
        try {
            Statement st = frisch.createStatement();
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM log_bewegung");
            rs.next();
            fest = rs.getInt(1);
            rs.close();
            st.close();
        } finally {
            frisch.rollback();
            frisch.close();
        }
        boolean aufsteigend = true;
        for (int i = 1; i < app.size(); i++) {
            aufsteigend &= app.get(i).getZeitpunktMs() >= app.get(i - 1).getZeitpunktMs();
        }
        check("App liest die ganze Historie ueber FDAL (alleBewegungen): alle Fahrten, die aelteste zuerst",
                app.size() == fest && aufsteigend, app.size() + " von " + fest + " Fahrten");

        // Die festgeschriebene Historie, wie die App sie sieht: eigene Sitzung. Die
        // Pruef-Transaktion hat Fahrten verschoben (Standgeld-Test: 31 min
        // vorverlegt) - das gehoert nicht in die Gegenprobe.
        Connection pruefung = con;
        Connection sicht = sitzung();
        con = sicht;
        try {
            zeitreiseFest();
        } finally {
            con = pruefung;
            sicht.rollback();
            sicht.close();
        }
        zeitreisePrognose();
    }

    private static void zeitreiseFest() throws SQLException {
        com.dan.logistikapp.model.Zeitreise z =
                zeitreiseAusDb(java.util.Collections.<com.dan.logistikapp.model.Auftrag>emptyList());
        List<String> abw = z.abweichungen();
        check("Historie endet bei jedem Container am heutigen Stand (Ort und Zollstatus), ohne Luecke",
                abw.isEmpty(), abw.isEmpty() ? z.getFahrtenAnzahl() + " Fahrten" : abw.size() + ": " + abw.get(0));

        int ohneFrei = zahl("SELECT COUNT(*) FROM log_bewegung b JOIN log_container c ON c.container_id = b.container_id "
                + "WHERE b.zoll = 'J' AND b.freigegeben_am IS NULL AND (c.status <> 'ZOLL' OR EXISTS "
                + "(SELECT 1 FROM log_bewegung n WHERE n.container_id = b.container_id AND (n.zeitpunkt > b.zeitpunkt "
                + "OR (n.zeitpunkt = b.zeitpunkt AND n.bewegung_id > b.bewegung_id))))");
        int zollfahrten = zahl("SELECT COUNT(*) FROM log_bewegung WHERE zoll = 'J'");
        check("Jede erledigte Zollfahrt kennt ihre Freigabe (freigegeben_am) - nur die Wartenden nicht",
                ohneFrei == 0, ohneFrei + " ohne Freigabezeit, " + zollfahrten + " Zollfahrten");

        // Gegenprobe: an bis zu 60 Zeitpunkten zwischen den Fahrten Java gegen SQL. Die
        // Datenbank rechnet in Mikrosekunden, Java in Millisekunden - deshalb die Mitte
        // zwischen zwei Ereignissen (mindestens 4 ms Abstand), nie 1 ms daneben.
        List<Long> w = new ArrayList<Long>();
        for (Long x : z.wechsel()) {
            if (x <= z.getJetztMs()) {
                w.add(x);
            }
        }
        List<Long> zeiten = new ArrayList<Long>();
        if (!w.isEmpty()) {
            zeiten.add(w.get(0) - 2);
            for (int i = 0; i + 1 < w.size(); i++) {
                if (w.get(i + 1) - w.get(i) >= 4) {
                    zeiten.add((w.get(i) + w.get(i + 1)) / 2);
                }
            }
            zeiten.add(w.get(w.size() - 1) + 2);
        }
        List<Long> probe = new ArrayList<Long>();
        int schritt = Math.max(1, zeiten.size() / 60);
        for (int i = 0; i < zeiten.size(); i += schritt) {
            probe.add(zeiten.get(i));
        }
        if (!zeiten.isEmpty() && !probe.contains(zeiten.get(zeiten.size() - 1))) {
            probe.add(zeiten.get(zeiten.size() - 1));
        }
        int abweichend = 0;
        int verglichen = 0;
        String erste = null;
        for (long t : probe) {
            Map<Integer, String> sql = standSql(t);
            for (com.dan.logistikapp.model.ContainerInfo c : z.standUm(t)) {
                String j = c.getOrtId() + "/" + c.getStatus();
                verglichen++;
                if (!j.equals(sql.get(c.getContainerId()))) {
                    abweichend++;
                    erste = erste == null ? new SimpleDateFormat("dd.MM. HH:mm:ss.SSS").format(new Date(t)) + " "
                            + Iso6346.anzeige(c.getKennung()) + ": Java " + j + ", SQL " + sql.get(c.getContainerId())
                            : erste;
                }
            }
        }
        check("SQL-Gegenprobe: Stand um t (Ort, Zoll) je Container = Java-Rekonstruktion der Zeitleiste",
                abweichend == 0 && probe.size() > 0, abweichend == 0 ? probe.size() + " Zeitpunkte, " + verglichen
                + " Vergleiche" : abweichend + " Abweichungen, zuerst " + erste);

        // Kosten um t: Java (KostenZeile.aus ueber fahrtenBis) gegen SQL
        long mitte = probe.isEmpty() ? z.getJetztMs() : probe.get(probe.size() / 2);
        Map<Integer, String[]> ware = new java.util.HashMap<Integer, String[]>();
        for (com.dan.logistikapp.model.ContainerInfo c : z.standUm(z.getJetztMs())) {
            ware.put(c.getContainerId(), new String[] {c.getWareCode(), c.getWare()});
        }
        java.math.BigDecimal javaSumme = java.math.BigDecimal.ZERO;
        for (com.dan.logistikapp.model.KostenZeile k : com.dan.logistikapp.model.KostenZeile.aus(z.fahrtenBis(mitte), ware)) {
            javaSumme = javaSumme.add(k.getKostenEur());
        }
        java.math.BigDecimal sqlSumme;
        PreparedStatement ps = con.prepareStatement("SELECT TO_CHAR(NVL(SUM(NVL(fracht_eur, 0) + NVL(zoll_eur, 0) "
                + "+ CASE WHEN freigegeben_am <= ? THEN NVL(standgeld_eur, 0) ELSE 0 END), 0), 'TM9', "
                + "'NLS_NUMERIC_CHARACTERS=''.,''') FROM log_bewegung WHERE zeitpunkt <= ?");
        try {
            ps.setTimestamp(1, new java.sql.Timestamp(mitte));
            ps.setTimestamp(2, new java.sql.Timestamp(mitte));
            ResultSet rs = ps.executeQuery();
            rs.next();
            sqlSumme = new java.math.BigDecimal(rs.getString(1));
            rs.close();
        } finally {
            ps.close();
        }
        check("Kosten um t (Kosten-Reiter auf der Zeitreise) = SQL-Summe bis t, Standgeld erst ab Freigabe",
                javaSumme.compareTo(sqlSumme) == 0, new SimpleDateFormat("dd.MM. HH:mm:ss").format(new Date(mitte))
                + ": Java " + javaSumme + ", SQL " + sqlSumme + " EUR");
    }

    /** Prognose mit echten Auftraegen - in der Pruef-Transaktion, wird zurueckgerollt. */
    private static void zeitreisePrognose() throws SQLException {
        int hamburg = ortId("Hamburg");
        int berlin = ortId("Berlin");
        int koeln = ortId("Köln");
        int x = bereiterIn(hamburg, 20);
        int start = zahl("SELECT ort_id FROM log_container WHERE container_id = " + x);
        int ziel1 = start == berlin ? koeln : berlin;
        int ziel2 = ziel1 == berlin ? koeln : berlin;
        long a1 = auftrag(x, ziel1, "+ INTERVAL '10' MINUTE");
        long a2 = auftrag(x, ziel2, "+ INTERVAL '20' MINUTE");
        List<com.dan.logistikapp.model.Auftrag> auftraege =
                lies(com.dan.logistikapp.db.AuftragRepository.SQL_LISTE, com.dan.logistikapp.db.AuftragRepository.MAPPER,
                        null);
        com.dan.logistikapp.model.Zeitreise zp = zeitreiseAusDb(auftraege);
        long jetzt = zp.getJetztMs();
        int in5 = -1;
        int in15 = -1;
        int in25 = -1;
        for (com.dan.logistikapp.model.ContainerInfo c : zp.standUm(jetzt + 5 * 60000L)) {
            in5 = c.getContainerId() == x ? c.getOrtId() : in5;
        }
        for (com.dan.logistikapp.model.ContainerInfo c : zp.standUm(jetzt + 15 * 60000L)) {
            in15 = c.getContainerId() == x ? c.getOrtId() : in15;
        }
        for (com.dan.logistikapp.model.ContainerInfo c : zp.standUm(jetzt + 25 * 60000L)) {
            in25 = c.getContainerId() == x ? c.getOrtId() : in25;
        }
        boolean offen15 = false;
        for (com.dan.logistikapp.model.Auftrag a : zp.offenNach(jetzt + 15 * 60000L)) {
            offen15 |= a.getAuftragId() == a2;
        }
        check("Prognose aus LOG_AUFTRAG: nach 5 min noch da, nach 15 min am ersten, nach 25 min am zweiten Ziel",
                in5 == start && in15 == ziel1 && in25 == ziel2 && offen15 && zp.istPrognose(jetzt + 1),
                "Auftraege " + a1 + ", " + a2 + ": Orte " + in5 + " -> " + in15 + " -> " + in25);
        com.dan.logistikapp.db.DbContainerDienst.auftragStornieren(con, a1);
        com.dan.logistikapp.db.DbContainerDienst.auftragStornieren(con, a2);
    }

    // ================================================================ S Demo mit echtem Stand

    /**
     * Die Demo-Szene mit dem echten Stand aus DEMO - wie beim Klick auf
     * "Demo abspielen" in der App. Sie muss mit jedem Stand zurechtkommen
     * (leere St&auml;dte, volle St&auml;dte, Container beim Zoll) und darf nichts
     * schreiben: gespielt wird im Speicher.
     */
    private static void demoEcht() throws SQLException {
        final int bewVorher = zahl("SELECT COUNT(*) FROM log_bewegung");
        final int auftrVorher = zahl("SELECT COUNT(*) FROM log_auftrag");
        final Map<Integer, String> standVorher = standKarte();
        final com.dan.fdal.FDatabaseManager fdal = com.dan.logistikapp.db.LogDb.oeffnen();
        final com.dan.logistikapp.karte.KartenPanel p = new com.dan.logistikapp.karte.KartenPanel();
        final com.dan.logistikapp.ui.Leitstand[] l = new com.dan.logistikapp.ui.Leitstand[1];
        final int[] schritt = {0};
        final boolean[] beob = new boolean[4];
        final Object[] spiel = new Object[1];
        long dauer;
        try {
            final com.dan.logistikapp.karte.KartenModell m =
                    com.dan.logistikapp.karte.KartenModell.aus(new com.dan.logistikapp.db.DbKartenQuelle(fdal));
            edtDb(new Runnable() {
                @Override
                public void run() {
                    p.setSize(1400, 830);
                    p.setModell(m);
                    p.allesZeigen();
                    p.setContainerDienst(new com.dan.logistikapp.db.DbContainerDienst(fdal));
                    l[0] = new com.dan.logistikapp.ui.Leitstand(p, 120);
                    l[0].getZeitleiste().setSize(1400, com.dan.logistikapp.ui.Zeitleiste.HOEHE);
                    l[0].neuLaden();
                }
            });
            ruhe(p, l[0], 15000);
            long t0 = System.currentTimeMillis();
            edtDb(new Runnable() {
                @Override
                public void run() {
                    l[0].getSeitenLeiste().klickDemo();
                }
            });
            long bis = t0 + 240000;
            final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1400, 830,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            while (System.currentTimeMillis() < bis) {
                final boolean[] laeuft = new boolean[1];
                edtDb(new Runnable() {
                    @Override
                    public void run() {
                        java.awt.Graphics2D g = img.createGraphics();
                        p.paint(g);
                        g.dispose();
                        com.dan.logistikapp.ui.DemoSzene d = l[0].getDemo();
                        laeuft[0] = d.laeuft();
                        String t = p.getDemoText();
                        if (t != null && t.indexOf('/') > 0) {
                            try {
                                schritt[0] = Math.max(schritt[0], Integer.parseInt(t.substring(0, t.indexOf('/'))));
                            } catch (NumberFormatException x) {
                                // ohne Nummer
                            }
                        }
                        beob[0] |= p.getPlanStrecken() > 0;
                        beob[1] |= p.istZeitreise();
                        beob[2] |= p.getUnterwegsAnzahl() > 0;
                        spiel[0] = d.getSpielDienst();
                    }
                });
                if (!laeuft[0] && System.currentTimeMillis() - t0 > 1000) {
                    break;
                }
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            dauer = System.currentTimeMillis() - t0;
            ruhe(p, l[0], 15000);
        } finally {
            edtDb(new Runnable() {
                @Override
                public void run() {
                    if (l[0] != null) {
                        l[0].stoppeHintergrund();
                    }
                }
            });
            fdal.close();
        }
        com.dan.logistikapp.dienst.SpeicherDienst s = (com.dan.logistikapp.dienst.SpeicherDienst) spiel[0];
        int fahrten = s == null ? 0 : s.getAufrufe();
        boolean auftrag = false;
        if (s != null) {
            for (com.dan.logistikapp.model.Auftrag a : s.auftraege()) {
                auftrag |= a.getStatus() == com.dan.logistikapp.model.Auftrag.Status.ERLEDIGT;
            }
        }
        int n = l[0].getDemo().getSchritte();
        check("Demo mit dem Stand aus DEMO: alle " + n + " Schritte, Fahrten im Speicher, Auftrag gefahren, Zeitreise",
                !l[0].getDemo().laeuft() && schritt[0] == n && fahrten >= 5 && auftrag && beob[0] && beob[1] && beob[2],
                schritt[0] + "/" + n + " in " + dauer / 1000 + " s, " + fahrten + " Fahrten, Auftrag " + auftrag
                + ", Plan " + beob[0] + ", Zeitreise " + beob[1] + "/" + beob[2]);
        check("Nach der Demo: DEMO unberuehrt - gleich viele Bewegungen und Auftraege, jeder Container am Platz",
                zahl("SELECT COUNT(*) FROM log_bewegung") == bewVorher
                && zahl("SELECT COUNT(*) FROM log_auftrag") == auftrVorher && standKarte().equals(standVorher)
                && !p.istZeitreise() && p.getDemoText() == null,
                bewVorher + " Bewegungen, " + auftrVorher + " Auftraege");
    }

    private static void edtDb(Runnable r) throws SQLException {
        try {
            javax.swing.SwingUtilities.invokeAndWait(r);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    /** Wartet, bis Karte und Leitstand nichts mehr lesen oder fahren. */
    private static void ruhe(final com.dan.logistikapp.karte.KartenPanel p, final com.dan.logistikapp.ui.Leitstand l,
            long maxMs) throws SQLException {
        long bis = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < bis) {
            final boolean[] r = new boolean[1];
            edtDb(new Runnable() {
                @Override
                public void run() {
                    r[0] = p.istRuhig() && l.istRuhig();
                }
            });
            if (r[0]) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ================================================================ Q Auftraege der App

    private static void auftraegeApp() throws SQLException {
        platzSchaffen();
        int hamburg = ortId("Hamburg");
        int berlin = ortId("Berlin");
        int muenchen = ortId("München");
        int zuerich = ortId("Zürich");

        // Auftrag faehrt ueber den Weg der App: Fahrt wird zurueckgelesen wie beim Ziehen
        int a = bereiterIn(hamburg, 20);
        long id = auftrag(a, berlin, "- INTERVAL '1' MINUTE");
        com.dan.logistikapp.dienst.AuftragErgebnis e = com.dan.logistikapp.db.DbContainerDienst.auftragMitFahrt(con, id, "APP");
        com.dan.logistikapp.dienst.Fahrt f = e.getFahrt();
        long bew = zahl("SELECT bewegung_id FROM log_auftrag WHERE auftrag_id = " + id);
        java.math.BigDecimal kosten = betrag("SELECT fracht_eur + zoll_eur FROM log_bewegung WHERE bewegung_id = " + bew);
        check("Auftrag ueber den Weg der App (auftragMitFahrt): gefahren, Fahrt mit Kosten und Metern wie LOG_BEWEGUNG",
                e.istGefahren() && f != null && f.getBewegungId() == bew && f.getKostenEur() != null
                && f.getKostenEur().compareTo(kosten) == 0
                && f.getDistanzM() == zahl("SELECT distanz_m FROM log_bewegung WHERE bewegung_id = " + bew),
                e + ", " + (f == null ? "-" : f.getKostenEur() + " EUR, " + f.getDistanzM() + " m"));

        com.dan.logistikapp.db.DbContainerDienst.verschieben(con, a, zuerich);
        long blk = auftrag(a, muenchen, "- INTERVAL '1' MINUTE");
        com.dan.logistikapp.dienst.AuftragErgebnis eb = com.dan.logistikapp.db.DbContainerDienst.auftragMitFahrt(con, blk, "APP");
        check("Blockiert ueber den Weg der App: keine Fahrt, Grund wie ihn die Liste zeigt",
                "BLOCKIERT".equals(eb.getErgebnis()) && eb.getFahrt() == null && eb.getGrund() != null
                && eb.getGrund().contains("Zoll"), eb.toString());
        com.dan.logistikapp.db.DbContainerDienst.zollFreigeben(con, a);
        com.dan.logistikapp.db.DbContainerDienst.auftragStornieren(con, blk);

        // Dieselbe Szene in LOG_API und im Dienst der Demo: gleiche Ergebnisse
        List<com.dan.logistikapp.model.Ort> orte =
                lies(com.dan.logistikapp.db.OrtRepository.SQL_ALLE, com.dan.logistikapp.db.OrtRepository.MAPPER, null);
        Map<String, com.dan.logistikapp.model.Land> laender = new LinkedHashMap<String, com.dan.logistikapp.model.Land>();
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery("SELECT DISTINCT iso2, zone_typ, eu_mitglied, zollunion, schengen FROM log_ort_v");
        while (rs.next()) {
            String iso = rs.getString(1).trim();
            laender.put(iso, new com.dan.logistikapp.model.Land(iso, iso,
                    com.dan.logistikapp.model.Zone.vonCode(rs.getString(2)), "J".equals(rs.getString(3)),
                    "J".equals(rs.getString(4)), "J".equals(rs.getString(5)), null));
        }
        rs.close();
        st.close();
        int c = bereiterIn(hamburg, 20);
        com.dan.logistikapp.dienst.SpeicherDienst sp = new com.dan.logistikapp.dienst.SpeicherDienst(orte,
                new ArrayList<com.dan.logistikapp.model.Land>(laender.values()),
                lies(com.dan.logistikapp.db.ContainerRepository.SQL_ALLE, com.dan.logistikapp.db.ContainerRepository.MAPPER,
                        null)).setTarif(com.dan.logistikapp.db.TarifRepository.lade(con));
        List<String> db = new ArrayList<String>();
        List<String> mem = new ArrayList<String>();
        long jetzt = System.currentTimeMillis();
        try {
            long ds = auftrag(c, muenchen, "- INTERVAL '1' MINUTE");
            long df = auftrag(c, berlin, "- INTERVAL '2' MINUTE");
            long ms = sp.auftragAnlegen(c, muenchen, jetzt - 60000);
            long mf = sp.auftragAnlegen(c, berlin, jetzt - 120000);
            db.add(ausfuehren(ds).getErgebnis());
            mem.add(sp.auftragAusfuehren(ms).getErgebnis());
            db.add(ausfuehren(df).getErgebnis());
            mem.add(sp.auftragAusfuehren(mf).getErgebnis());
            db.add(ausfuehren(ds).getErgebnis());
            mem.add(sp.auftragAusfuehren(ms).getErgebnis());
            long dz = auftrag(c, hamburg, "+ INTERVAL '1' HOUR");
            long mz = sp.auftragAnlegen(c, hamburg, jetzt + 3600000);
            db.add(ausfuehren(dz).getErgebnis());
            mem.add(sp.auftragAusfuehren(mz).getErgebnis());
            com.dan.logistikapp.db.DbContainerDienst.auftragStornieren(con, dz);
            sp.auftragStornieren(mz);
            com.dan.logistikapp.db.DbContainerDienst.verschieben(con, c, zuerich);
            sp.verschieben(c, zuerich);
            long db1 = auftrag(c, muenchen, "- INTERVAL '1' MINUTE");
            long mb1 = sp.auftragAnlegen(c, muenchen, jetzt - 60000);
            db.add(ausfuehren(db1).getErgebnis());
            mem.add(sp.auftragAusfuehren(mb1).getErgebnis());
            com.dan.logistikapp.db.DbContainerDienst.zollFreigeben(con, c);
            sp.zollFreigeben(c);
            db.add(ausfuehren(db1).getErgebnis());
            mem.add(sp.auftragAusfuehren(mb1).getErgebnis());
            if ("ZOLL".equals(text("SELECT status FROM log_container WHERE container_id = " + c))) {
                com.dan.logistikapp.db.DbContainerDienst.zollFreigeben(con, c);
                sp.zollFreigeben(c);
            }
            long dd = auftrag(c, muenchen, "- INTERVAL '1' MINUTE");
            long md = sp.auftragAnlegen(c, muenchen, jetzt - 60000);
            db.add(ausfuehren(dd).getErgebnis());
            mem.add(sp.auftragAusfuehren(md).getErgebnis());
        } catch (com.dan.logistikapp.dienst.DienstFehler x) {
            mem.add("FEHLER " + x.getArt());
        }
        check("Dienst der Demo und LOG_API: dieselbe Auftrags-Szene, dieselben Ergebnisse",
                db.equals(mem) && db.size() == 7, "DB " + db + (db.equals(mem) ? "" : ", Speicher " + mem));

        // Der echte Weg der App: FDAL, eigene Transaktion, festgeschrieben - und wieder weg
        com.dan.fdal.FDatabaseManager fdal = com.dan.logistikapp.db.LogDb.oeffnen();
        long fest = 0;
        try {
            com.dan.logistikapp.db.DbContainerDienst dienst = new com.dan.logistikapp.db.DbContainerDienst(fdal);
            int x = zahl("SELECT MIN(container_id) FROM log_container");
            String m0 = dienst.standMarke();
            fest = dienst.auftragAnlegen(x, hamburg, System.currentTimeMillis() + 365L * 24 * 3600 * 1000);
            boolean inListe = false;
            for (com.dan.logistikapp.model.Auftrag au : dienst.auftraege()) {
                inListe |= au.getAuftragId() == fest && au.istOffen() && au.getRang() >= 1;
            }
            String m1 = dienst.standMarke();
            dienst.auftragStornieren(fest);
            boolean storniert = false;
            for (com.dan.logistikapp.model.Auftrag au : dienst.auftraege()) {
                storniert |= au.getAuftragId() == fest && au.getStatus() == com.dan.logistikapp.model.Auftrag.Status.STORNIERT;
            }
            check("Weg der App ueber FDAL: planen, in der Liste sehen, Standmarke aendert sich, stornieren",
                    fest > 0 && inListe && storniert && m0 != null && !m0.equals(m1), "Auftrag " + fest + ", Marke "
                    + m0 + " -> " + m1);
        } catch (com.dan.logistikapp.dienst.DienstFehler x) {
            check("Weg der App ueber FDAL: planen, in der Liste sehen, Standmarke aendert sich, stornieren", false,
                    x.getArt() + ": " + x.getMessage());
        } finally {
            fdal.close();
            if (fest > 0) {
                Connection weg = sitzung();
                try {
                    Statement d = weg.createStatement();
                    d.executeUpdate("DELETE FROM log_auftrag WHERE auftrag_id = " + fest);
                    d.close();
                    weg.commit();
                } finally {
                    weg.close();
                }
            }
        }
        Connection frisch = sitzung();
        try {
            Statement s2 = frisch.createStatement();
            ResultSet r2 = s2.executeQuery("SELECT COUNT(*) FROM log_auftrag WHERE auftrag_id = " + fest);
            r2.next();
            check("Danach ist der festgeschriebene Test-Auftrag wieder weg", r2.getInt(1) == 0, null);
            r2.close();
            s2.close();
        } finally {
            frisch.close();
        }
    }

    // ================================================================ O Nebenlaeufigkeit

    private static Connection sitzung() throws SQLException {
        Connection c = DriverManager.getConnection(URL, USER, PWD);
        c.setAutoCommit(false);
        return c;
    }

    /** Sperrt die Zeile ohne zu warten; false, wenn eine andere Sitzung sie haelt. */
    private static boolean frei(Connection c, String tabelle, String spalte, long id) {
        try {
            Statement st = c.createStatement();
            try {
                st.executeQuery("SELECT 1 FROM " + tabelle + " WHERE " + spalte + " = " + id + " FOR UPDATE NOWAIT")
                        .close();
                return true;
            } finally {
                st.close();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private static void nebenlaeufig() throws SQLException {
        Connection s2 = sitzung();
        Connection s3 = sitzung();
        long auftragFest = 0;
        try {
            // Ziel und zwei Container, die gerade niemand haelt (etwa eine offene App)
            int ziel = ortId("Hamburg");
            List<Integer> kandidaten = new ArrayList<Integer>();
            Statement st = s3.createStatement();
            ResultSet rs = st.executeQuery("SELECT c.container_id FROM log_container c JOIN log_ort_v o "
                    + "ON o.ort_id = c.ort_id WHERE c.status = 'BEREIT' AND c.ort_id <> " + ziel
                    + " AND o.zone_typ IN ('INLAND', 'EU') AND c.groesse_fuss = 20 ORDER BY c.container_id");
            while (rs.next()) {
                kandidaten.add(rs.getInt(1));
            }
            rs.close();
            st.close();
            int x = -1;
            int y = -1;
            for (int k : kandidaten) {
                if (frei(s3, "log_container", "container_id", k)) {
                    if (x < 0) {
                        x = k;
                    } else {
                        y = k;
                        break;
                    }
                }
            }
            boolean zielFrei = frei(s3, "log_ort", "ort_id", ziel);
            s3.rollback();
            if (x < 0 || y < 0 || !zielFrei) {
                check("Zwei freie Container und eine freie Zielstadt gefunden", false,
                        "x=" + x + ", y=" + y + ", Ziel frei " + zielFrei + " - laeuft gerade eine App?");
                return;
            }

            // Sitzung 2 faehrt X nach Hamburg und haelt damit die Zielstadt
            com.dan.logistikapp.db.DbContainerDienst.verschieben(s2, x, ziel);
            final Connection s3f = s3;
            final int yf = y;
            final int zf = ziel;
            final String[] ergebnis = new String[1];
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        com.dan.logistikapp.db.DbContainerDienst.verschieben(s3f, yf, zf);
                        ergebnis[0] = "gefahren";
                    } catch (SQLException e) {
                        ergebnis[0] = "ORA-" + e.getErrorCode();
                    }
                }
            }, "Sitzung 3");
            t.setDaemon(true);
            long t0 = System.currentTimeMillis();
            t.start();
            try {
                t.join(1500);
                boolean wartet = t.isAlive();
                s2.rollback();
                t.join(10000);
                long ms = System.currentTimeMillis() - t0;
                check("Zweite Fahrt in dieselbe Stadt wartet, bis die erste fertig ist (Sperre auf LOG_ORT)",
                        wartet && !t.isAlive() && "gefahren".equals(ergebnis[0]),
                        (wartet ? "wartete" : "wartete NICHT") + ", danach " + ergebnis[0] + " nach " + ms + " ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            s3.rollback();

            // SKIP LOCKED: Sitzung 2 haelt einen Auftrag, Sitzung 3 bekommt sofort GESPERRT.
            // Dafuer muss der Auftrag festgeschrieben sein; Termin in einem Jahr,
            // damit ihn nie jemand faehrt - und am Ende wird er wieder geloescht.
            CallableStatement cs = s2.prepareCall(
                    "BEGIN log_api.auftrag_anlegen(?, ?, SYSTIMESTAMP + INTERVAL '365' DAY, ?); END;");
            cs.setInt(1, x);
            cs.setInt(2, ziel);
            cs.registerOutParameter(3, Types.NUMERIC);
            cs.execute();
            auftragFest = cs.getLong(3);
            cs.close();
            s2.commit();
            frei(s2, "log_auftrag", "auftrag_id", auftragFest);
            long t1 = System.currentTimeMillis();
            com.dan.logistikapp.db.DbContainerDienst.Ausfuehrung e =
                    com.dan.logistikapp.db.DbContainerDienst.auftragAusfuehren(s3, auftragFest, "JOB");
            long ms = System.currentTimeMillis() - t1;
            check("Auftrag, den eine andere Sitzung haelt: sofort GESPERRT, ohne zu warten",
                    "GESPERRT".equals(e.getErgebnis()) && ms < 1000, e + " nach " + ms + " ms");
            s3.rollback();
        } finally {
            try {
                s2.rollback();
                if (auftragFest > 0) {
                    Statement d = s2.createStatement();
                    d.executeUpdate("DELETE FROM log_auftrag WHERE auftrag_id = " + auftragFest);
                    d.close();
                    s2.commit();
                }
            } finally {
                s2.close();
                s3.rollback();
                s3.close();
            }
        }
        check("Danach kein Test-Auftrag uebrig", auftragFest == 0
                || zahl("SELECT COUNT(*) FROM log_auftrag WHERE auftrag_id = " + auftragFest) == 0, null);
    }

    // ================================================================ Hilfen

    private static int ortId(String name) throws SQLException {
        return Integer.parseInt(text1("SELECT ort_id FROM log_ort WHERE name = ?", name));
    }

    private static void check(String name, boolean gruen, String detail) {
        if (gruen) {
            ok++;
        } else {
            rot++;
        }
        say((gruen ? "   [ok]     " : "   [ROT]    ") + name
                + (detail == null ? "" : "   - " + detail));
    }

    /**
     * Gruen NUR, wenn genau dieser Fehler kommt. Kein Fehler oder ein
     * anderer Fehler ist rot. Die Anweisung laeuft hinter einem Savepoint,
     * damit ein unerwartetes Gelingen die folgenden Pruefungen nicht
     * verfaelscht.
     */
    private static void erwarteFehler(String name, int ora, String sql) throws SQLException {
        java.sql.Savepoint sp = con.setSavepoint();
        Statement st = con.createStatement();
        try {
            st.execute(sql);
            check(name, false, "kein Fehler - erwartet war ORA-" + ora);
        } catch (SQLException e) {
            check(name, e.getErrorCode() == ora,
                    "ORA-" + e.getErrorCode() + (e.getErrorCode() == ora ? "" : " " + einzeilig(e.getMessage())));
        } finally {
            st.close();
            con.rollback(sp);
        }
    }

    /**
     * Platz fuer die Testfahrten. Die Pruefung faehrt Container quer durch
     * Europa; ist eine Stadt in DEMO wirklich voll (Fahrten aus der App oder
     * vom Job), lehnt LOG_API zu Recht mit -20026 ab und der Abschnitt
     * bricht ab. Deshalb bekommt jede Stadt mit weniger als 6 freien TEU in
     * DIESER Transaktion mehr Kapazitaet - das Rollback am Ende nimmt es
     * zurueck. Die Kapazitaetspruefungen in N setzen ihre Grenzen selbst.
     */
    private static void platzSchaffen() throws SQLException {
        String knapp = text("SELECT LISTAGG(name || ' ' || belegt_teu || '/' || kapazitaet_teu, ', ') "
                + "WITHIN GROUP (ORDER BY ort_id) FROM log_ort_v WHERE frei_teu < 6");
        if (knapp == null) {
            return;
        }
        exec("UPDATE log_ort o SET kapazitaet_teu = (SELECT v.belegt_teu FROM log_ort_v v "
                + "WHERE v.ort_id = o.ort_id) + 6 WHERE kapazitaet_teu IS NOT NULL AND o.ort_id IN "
                + "(SELECT ort_id FROM log_ort_v WHERE frei_teu < 6)");
        say("   Knapp (DEMO samt Testfahrten): " + knapp + " - +6 TEU, nur bis zum Rollback");
    }

    private static void exec(String sql) throws SQLException {
        Statement st = con.createStatement();
        try {
            st.execute(sql);
        } finally {
            st.close();
        }
    }

    private static int zahl(String sql) throws SQLException {
        String s = text(sql);
        return s == null ? Integer.MIN_VALUE : (int) Math.round(Double.parseDouble(s));
    }

    private static String text(String sql) throws SQLException {
        Statement st = con.createStatement();
        try {
            ResultSet rs = st.executeQuery(sql);
            String s = rs.next() ? rs.getString(1) : null;
            rs.close();
            return s;
        } finally {
            st.close();
        }
    }

    private static String text1(String sql, String p) throws SQLException {
        PreparedStatement ps = con.prepareStatement(sql);
        try {
            ps.setString(1, p);
            ResultSet rs = ps.executeQuery();
            String s = rs.next() ? rs.getString(1) : null;
            rs.close();
            return s;
        } finally {
            ps.close();
        }
    }

    private static void abschnitt(String titel) {
        say("");
        say(titel);
        say("---------------------------------------------------------");
    }

    /** Ein Abschnitt, der scheitert, kostet eine rote Zeile - nicht den Rest des Laufs. */
    private interface Teil {
        void run() throws SQLException;
    }

    private static void abschnitt(String titel, Teil teil) {
        abschnitt(titel);
        try {
            teil.run();
        } catch (SQLException e) {
            check("Abschnitt lief bis zum Ende", false, ursachen(e));
        } catch (RuntimeException e) {
            check("Abschnitt lief bis zum Ende", false, ursachen(e));
        }
    }

    /** Die ganze Ursachenkette - die erste Meldung allein verschweigt meist die ORA-Nummer. */
    static String ursachen(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (sb.length() > 0) {
                sb.append("  <-  ");
            }
            sb.append(c.getClass().getSimpleName());
            if (c instanceof SQLException) {
                sb.append(" ORA-").append(((SQLException) c).getErrorCode());
            }
            sb.append(": ").append(einzeilig(c.getMessage()));
            if (c.getCause() == c) {
                break;
            }
        }
        return sb.toString();
    }

    private static String einzeilig(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').replaceAll(" +", " ").trim();
    }

    private static void say(String s) {
        System.out.println(s);
        if (log != null) {
            log.println(s);
        }
    }
}
