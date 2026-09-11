import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Faehrt die SQL-Skripte aus db/ gegen die Oracle-Instanz und schreibt ein
 * Protokoll nach db_setup.log.
 *
 * <p>Der Sinn: Claude kann die Datenbank nicht erreichen, aber die Log-Datei
 * liegt im verbundenen Projektordner und ist damit lesbar. Ein Doppelklick auf
 * db_setup.bat genuegt fuer eine Runde.</p>
 *
 * <p>Aufruf: {@code java LogDbSetup [skript ...]} &mdash; ohne Argumente laufen
 * alle Skripte der Standardliste; sonst genau die genannten.</p>
 *
 * <p>Uebernommen aus FCurvedField (FCfDbSetup), dort ueber viele Runden
 * erprobt. Geaendert: Skriptliste, Selbsttest, Protokollkopf.</p>
 *
 * @author Dan
 */
public final class LogDbSetup {

    private static final String URL  = "jdbc:oracle:thin:@//localhost:1521/PDBORCL";
    private static final String USER = "DEMO";
    private static final String PWD  = "de";

    /**
     * Neuaufbau. 09 (Struktur Runde 2) steht vor 02, weil das Package die
     * neuen Spalten braucht; 10 rechnet danach auch die Beispielfahrten
     * aus 05 nach. Eine bestehende DEMO bekommt Runde 2 mit
     * {@code 09_runde2.sql 02_api.sql 04_sichten.sql 10_daten2.sql 11_job.sql}.
     */
    private static final String[] DEFAULT_SCRIPTS = {
        "00_drop.sql", "01_tabellen.sql", "09_runde2.sql", "02_api.sql", "03_regeln.sql",
        "04_sichten.sql", "05_daten.sql", "06_laender.sql", "07_grenzen.tsv",
        "08_karte.sql", "10_daten2.sql", "12_staedte.sql", "11_job.sql"
    };

    /** Nur einzelne Skripte: der Bestand ist dann der des Nutzers, nicht der Startbestand. */
    private static boolean teillauf;

    private static PrintWriter log;
    private static int errors;
    private static int statements;

    public static void main(String[] args) throws Exception {
        File base = new File(".").getCanonicalFile();
        File logFile = new File(base, "db_setup.log");
        log = new PrintWriter(new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(logFile), "UTF-8"), true);

        say("Logistik_App - Datenbank-Einrichtung");
        say("Zeit        : " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date()));
        say("Verzeichnis : " + base.getAbsolutePath());
        say("URL         : " + URL + "   Benutzer: " + USER);
        say("");

        List<String> scripts = new ArrayList<String>();
        if (args.length == 0) {
            scripts.addAll(Arrays.asList(DEFAULT_SCRIPTS));
        } else {
            scripts.addAll(Arrays.asList(args));
            teillauf = true;
        }

        Connection con = null;
        try {
            long t0 = System.currentTimeMillis();
            con = DriverManager.getConnection(URL, USER, PWD);
            con.setAutoCommit(true);
            say("Verbunden in " + (System.currentTimeMillis() - t0) + " ms");
            say("Datenbank   : " + con.getMetaData().getDatabaseProductVersion());
            say("");

            for (int i = 0; i < scripts.size(); i++) {
                runScript(con, new File(base, "db" + File.separator + scripts.get(i)));
            }

            say("");
            say("=========================================================");
            say("Fertig. " + statements + " Anweisungen, " + errors + " Fehler.");
            if (errors == 0) {
                selbsttest(con);
            }
        } catch (SQLException e) {
            say("VERBINDUNG FEHLGESCHLAGEN: " + e.getMessage());
            say("");
            say("Pruefe bitte: laeuft der Listener (lsnrctl status), stimmt der");
            say("Servicename PDBORCL, existiert der Benutzer DEMO?");
            errors++;
        } finally {
            if (con != null) {
                try {
                    con.close();
                } catch (SQLException ignore) {
                    // beim Schliessen ist nichts mehr zu retten
                }
            }
            log.close();
        }
        System.out.println();
        System.out.println("Protokoll: " + logFile.getAbsolutePath());
    }

    // ------------------------------------------------------------ Skripte

    private static void runScript(Connection con, File file) {
        say("---------------------------------------------------------");
        say("Skript: " + file.getName());
        say("---------------------------------------------------------");
        if (!file.isFile()) {
            say("  FEHLT: " + file.getAbsolutePath());
            errors++;
            return;
        }
        if (file.getName().endsWith(".tsv")) {
            ladeGrenzen(con, file);
            say("");
            return;
        }
        List<String> stmts;
        try {
            stmts = split(file);
        } catch (Exception e) {
            say("  Lesefehler: " + e);
            errors++;
            return;
        }
        for (int i = 0; i < stmts.size(); i++) {
            execute(con, stmts.get(i));
        }
        say("");
    }

    /**
     * Zerlegt ein Skript in einzelne Anweisungen. Regel wie in SQL*Plus:
     * ein einzelner Schraegstrich auf eigener Zeile beendet einen PL/SQL-Block,
     * ein Semikolon am Zeilenende eine gewoehnliche Anweisung. SET- und
     * PROMPT-Zeilen sind SQL*Plus-Direktiven und gehen nicht an den Server.
     */
    private static List<String> split(File file) throws Exception {
        List<String> out = new ArrayList<String>();
        BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), Charset.forName("UTF-8")));
        StringBuilder buf = new StringBuilder();
        boolean plsql = false;
        try {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (buf.length() == 0) {
                    if (t.isEmpty() || t.startsWith("--")) {
                        continue;
                    }
                    String u = t.toUpperCase();
                    if (u.startsWith("SET ") || u.startsWith("PROMPT")) {
                        if (u.startsWith("PROMPT")) {
                            say("  > " + t.substring(6).trim());
                        }
                        continue;
                    }
                    plsql = isPlSqlStart(u);
                }
                if ("/".equals(t)) {
                    if (buf.length() > 0) {
                        out.add(buf.toString());
                        buf.setLength(0);
                    }
                    plsql = false;
                    continue;
                }
                buf.append(line).append('\n');
                if (!plsql && t.endsWith(";")) {
                    String s = buf.toString().trim();
                    out.add(s.substring(0, s.length() - 1));
                    buf.setLength(0);
                }
            }
            if (buf.toString().trim().length() > 0) {
                out.add(buf.toString());
            }
        } finally {
            r.close();
        }
        return out;
    }

    private static boolean isPlSqlStart(String u) {
        if (u.startsWith("DECLARE") || u.startsWith("BEGIN")) {
            return true;
        }
        if (!u.startsWith("CREATE")) {
            return false;
        }
        return u.contains(" PACKAGE") || u.contains(" PROCEDURE")
                || u.contains(" FUNCTION") || u.contains(" TRIGGER")
                || u.contains(" TYPE");
    }

    // ------------------------------------------------------------ Grenzen

    /**
     * Laedt Landesflaechen aus einer TSV-Datei (ISO2, Vertexzahl, WKT) in
     * LOG_LAND.GRENZE.
     *
     * <p>Der WKT-Text geht als CLOB hinein: Russland allein hat ueber
     * 300 000 Zeichen, als VARCHAR2 gebunden waere bei 32 767 Schluss.
     * Ein PL/SQL-Block mit einer CLOB-Variablen sorgt dafuer, dass Oracle
     * die CLOB-Fassung von FROM_WKTGEOMETRY waehlt.</p>
     *
     * <p>Jede Flaeche wird danach geprueft. Was Oracle als ungueltig
     * meldet, wird mit RECTIFY_GEOMETRY repariert und erneut geprueft -
     * beides steht im Protokoll, nichts geschieht still.</p>
     */
    private static void ladeGrenzen(Connection con, File file) {
        String laden = "DECLARE v_wkt CLOB := ?; g SDO_GEOMETRY; BEGIN "
                + "g := SDO_UTIL.FROM_WKTGEOMETRY(v_wkt); g.sdo_srid := 8307; "
                + "UPDATE log_land SET grenze = g WHERE iso2 = ?; ? := SQL%ROWCOUNT; END;";
        String pruefen = "SELECT SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(l.grenze, 0.05), "
                + "SDO_UTIL.GETNUMVERTICES(l.grenze) FROM log_land l WHERE l.iso2 = ?";
        String reparieren = "UPDATE log_land SET grenze = SDO_UTIL.RECTIFY_GEOMETRY(grenze, 0.05) "
                + "WHERE iso2 = ?";
        int n = 0;
        int repariert = 0;
        int vertices = 0;
        long t0 = System.currentTimeMillis();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), Charset.forName("UTF-8")));
            CallableStatement cs = con.prepareCall(laden);
            PreparedStatement ps = con.prepareStatement(pruefen);
            PreparedStatement rep = con.prepareStatement(reparieren);
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }
                String[] f = line.split("\t", 3);
                if (f.length != 3) {
                    say("  Zeile ohne drei Felder uebersprungen");
                    errors++;
                    continue;
                }
                String iso = f[0].trim();
                statements++;
                try {
                    Clob clob = con.createClob();
                    clob.setString(1, f[2]);
                    cs.setClob(1, clob);
                    cs.setString(2, iso);
                    cs.registerOutParameter(3, java.sql.Types.NUMERIC);
                    cs.execute();
                    clob.free();
                    if (cs.getInt(3) != 1) {
                        say("  " + iso + ": kein Land mit diesem Code in LOG_LAND");
                        errors++;
                        continue;
                    }
                    String urteil = gueltig(ps, iso);
                    if (!urteil.startsWith("TRUE")) {
                        say("  " + iso + ": " + urteil + " -> RECTIFY_GEOMETRY");
                        rep.setString(1, iso);
                        rep.executeUpdate();
                        repariert++;
                        urteil = gueltig(ps, iso);
                        if (!urteil.startsWith("TRUE")) {
                            say("  " + iso + ": nach der Reparatur weiterhin " + urteil);
                            errors++;
                        }
                    }
                    int v = Integer.parseInt(urteil.substring(urteil.indexOf('|') + 1));
                    vertices += v;
                    n++;
                } catch (SQLException e) {
                    errors++;
                    say("  " + iso + ": FEHLER ORA-" + e.getErrorCode() + ": "
                            + einzeilig(e.getMessage()));
                }
            }
            cs.close();
            ps.close();
            rep.close();
        } catch (Exception e) {
            errors++;
            say("  Lesefehler: " + e);
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignore) {
                    // egal
                }
            }
        }
        say("  " + n + " Flaechen geladen, " + vertices + " Stuetzpunkte, "
                + repariert + " repariert, " + (System.currentTimeMillis() - t0) + " ms");
    }

    /** "TRUE|vertices" oder "ORA-Code...|vertices". */
    private static String gueltig(PreparedStatement ps, String iso) throws SQLException {
        ps.setString(1, iso);
        ResultSet rs = ps.executeQuery();
        try {
            rs.next();
            return einzeilig(rs.getString(1)) + "|" + rs.getInt(2);
        } finally {
            rs.close();
        }
    }

    // ------------------------------------------------------------ Ausfuehren

    private static void execute(Connection con, String sql) {
        String head = kopf(sql);
        Statement st = null;
        try {
            statements++;
            st = con.createStatement();
            boolean hasResult = st.execute(sql);
            if (hasResult) {
                ResultSet rs = st.getResultSet();
                say("  " + head);
                printResultSet(rs);
                rs.close();
            } else {
                int n = st.getUpdateCount();
                say("  " + head + (n > 0 ? "   (" + n + " Zeilen)" : "   ok"));
            }
            warnungen(con, head);
        } catch (SQLException e) {
            errors++;
            say("  " + head);
            say("     FEHLER ORA-" + e.getErrorCode() + ": "
                    + einzeilig(e.getMessage()));
            say("     Anweisung: " + einzeilig(sql.length() > 400
                    ? sql.substring(0, 400) + " ..." : sql));
        } finally {
            if (st != null) {
                try {
                    st.close();
                } catch (SQLException ignore) {
                    // egal
                }
            }
        }
    }

    /**
     * Nach CREATE von PL/SQL-Objekten meldet Oracle keinen Fehler, sondern
     * legt das Objekt mit Kompilierfehlern an. Die stehen in USER_ERRORS und
     * waeren sonst unsichtbar.
     */
    private static void warnungen(Connection con, String head) {
        String u = head.toUpperCase();
        if (!u.startsWith("CREATE")) {
            return;
        }
        Statement st = null;
        ResultSet rs = null;
        try {
            st = con.createStatement();
            rs = st.executeQuery(
                    "SELECT name, type, line, position, text FROM user_errors "
                  + " WHERE attribute = 'ERROR' ORDER BY name, sequence");
            boolean any = false;
            while (rs.next()) {
                if (!any) {
                    say("     KOMPILIERFEHLER:");
                    any = true;
                    errors++;
                }
                say("       " + rs.getString(2) + " " + rs.getString(1)
                        + " Zeile " + rs.getInt(3) + ": "
                        + einzeilig(rs.getString(5)));
            }
        } catch (SQLException ignore) {
            // Wenn selbst das scheitert, hilft es nicht weiter
        } finally {
            close(rs);
            close(st);
        }
    }

    private static void printResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        StringBuilder sb = new StringBuilder("     ");
        for (int i = 1; i <= cols; i++) {
            sb.append(pad(md.getColumnLabel(i), 20)).append(' ');
        }
        say(sb.toString());
        sb = new StringBuilder("     ");
        for (int i = 1; i <= cols; i++) {
            sb.append(pad("--------------------", 20)).append(' ');
        }
        say(sb.toString());
        int n = 0;
        while (rs.next() && n < 60) {
            sb = new StringBuilder("     ");
            for (int i = 1; i <= cols; i++) {
                Object v = rs.getObject(i);
                String s = (v == null) ? "" : String.valueOf(v);
                sb.append(pad(einzeilig(s), 20)).append(' ');
            }
            say(sb.toString());
            n++;
        }
        if (n == 60) {
            say("     ... (gekuerzt)");
        }
    }

    // ------------------------------------------------------------ Selbsttest

    private static void selbsttest(Connection con) {
        // Nach einem neuen Package-Kopf sind abhaengige Objekte (Trigger,
        // Sichten) ungueltig, bis sie jemand benutzt. Hier gleich neu
        // uebersetzen - sonst zaehlt der Test sie als Fehler.
        execute(con, "BEGIN\n"
                + "  FOR o IN (SELECT object_name, object_type FROM user_objects\n"
                + "             WHERE status <> 'VALID' AND object_name LIKE 'LOG\\_%' ESCAPE '\\'\n"
                + "               AND object_type IN ('VIEW', 'TRIGGER', 'PACKAGE', 'PACKAGE BODY')) LOOP\n"
                + "    BEGIN\n"
                + "      EXECUTE IMMEDIATE CASE o.object_type\n"
                + "        WHEN 'PACKAGE BODY' THEN 'ALTER PACKAGE ' || o.object_name || ' COMPILE BODY'\n"
                + "        ELSE 'ALTER ' || o.object_type || ' ' || o.object_name || ' COMPILE' END;\n"
                + "    EXCEPTION WHEN OTHERS THEN NULL;\n"
                + "    END;\n"
                + "  END LOOP;\n"
                + "END;");
        say("");
        say("Selbsttest");
        say("---------------------------------------------------------");
        pruefe(con, "Laender", "SELECT COUNT(*) FROM log_land", 63);
        pruefe(con, "Laender mit Grenze", "SELECT COUNT(grenze) FROM log_land", 63);
        pruefe(con, "Ungueltige Grenzen", "SELECT COUNT(*) FROM log_land l "
                + "WHERE SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(l.grenze, 0.05) <> 'TRUE'", 0);
        pruefe(con, "Staedte", "SELECT COUNT(*) FROM log_ort", 10);
        pruefe(con, "Waren", "SELECT COUNT(*) FROM log_ware", 6);
        // Nach einem Teillauf ist das der Bestand des Nutzers: nur Auskunft
        pruefe(con, "Container", "SELECT COUNT(*) FROM log_container", 18);
        pruefe(con, "Bewegungen", "SELECT COUNT(*) FROM log_bewegung", teillauf ? -1 : 3);
        pruefe(con, "Beim Zoll", "SELECT COUNT(*) FROM log_container "
                + "WHERE status = 'ZOLL'", teillauf ? -1 : 0);
        pruefe(con, "Ungueltige LOG-Objekte", "SELECT COUNT(*) FROM user_objects "
                + "WHERE status <> 'VALID' AND object_name LIKE 'LOG\\_%' ESCAPE '\\'", 0);
        pruefe(con, "Spatial-Index Staedte", "SELECT COUNT(*) FROM user_indexes "
                + "WHERE index_name = 'LOG_ORT_SX'", 1);
        pruefe(con, "Spatial-Index Laender", "SELECT COUNT(*) FROM user_indexes "
                + "WHERE index_name = 'LOG_LAND_SX'", 1);
        say("   -- Runde 2");
        pruefe(con, "Tarife", "SELECT COUNT(*) FROM log_tarif", 3);
        pruefe(con, "Staedte mit Kapazitaet", "SELECT COUNT(kapazitaet_teu) FROM log_ort", 10);
        pruefe(con, "Staedte ueber Kapazitaet", "SELECT COUNT(*) FROM log_ort_v "
                + "WHERE frei_teu < 0", 0);
        pruefe(con, "Waren mit Frachtsatz", "SELECT COUNT(fracht_eur_teu_km) FROM log_ware", 6);
        pruefe(con, "Fahrten ohne Kosten", "SELECT COUNT(*) FROM log_bewegung "
                + "WHERE fracht_eur IS NULL OR zoll_eur IS NULL", 0);
        pruefe(con, "Zollfahrt ohne Freigabe", "SELECT COUNT(*) FROM log_bewegung b "
                + "JOIN log_container c ON c.container_id = b.container_id "
                + "WHERE b.zoll = 'J' AND b.freigegeben_am IS NULL AND (c.status = 'BEREIT' "
                + "OR b.bewegung_id < (SELECT MAX(x.bewegung_id) FROM log_bewegung x "
                + "WHERE x.container_id = b.container_id))", 0);
        pruefe(con, "Auftraege", "SELECT COUNT(*) FROM log_auftrag", teillauf ? -1 : 0);
        pruefe(con, "Job LOG_AUFTRAG_JOB aktiv", "SELECT COUNT(*) FROM user_scheduler_jobs "
                + "WHERE job_name = 'LOG_AUFTRAG_JOB' AND enabled = 'TRUE'", 1);
    }

    private static void pruefe(Connection con, String was, String sql, int erwartet) {
        Statement st = null;
        ResultSet rs = null;
        try {
            st = con.createStatement();
            rs = st.executeQuery(sql);
            rs.next();
            int n = rs.getInt(1);
            String urteil;
            if (erwartet < 0) {
                urteil = "";
            } else if (n == erwartet) {
                urteil = "  [ok]";
            } else {
                urteil = "  [ERWARTET " + erwartet + "]";
                errors++;
            }
            say("   " + pad(was, 24) + " " + pad(String.valueOf(n), 6) + urteil);
        } catch (SQLException e) {
            say("   " + pad(was, 24) + " FEHLER: " + einzeilig(e.getMessage()));
            errors++;
        } finally {
            close(rs);
            close(st);
        }
    }

    // ------------------------------------------------------------ Hilfen

    private static void close(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException ignore) {
                // egal
            }
        }
    }

    private static void close(Statement st) {
        if (st != null) {
            try {
                st.close();
            } catch (SQLException ignore) {
                // egal
            }
        }
    }

    private static String kopf(String sql) {
        String s = einzeilig(sql.trim());
        return s.length() > 90 ? s.substring(0, 90) + " ..." : s;
    }

    private static String einzeilig(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\n', ' ').replace('\r', ' ').replaceAll(" +", " ").trim();
    }

    private static String pad(String s, int n) {
        if (s == null) {
            s = "";
        }
        if (s.length() >= n) {
            return s.substring(0, n);
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static void say(String s) {
        System.out.println(s);
        if (log != null) {
            log.println(s);
        }
    }

    private LogDbSetup() {
    }
}
