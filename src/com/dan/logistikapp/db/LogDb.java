package com.dan.logistikapp.db;

import com.dan.fdal.FDatabaseManager;
import com.dan.fdal.FDbConfig;
import com.dan.fdal.FSqlDialect;

/**
 * Verbindung zum Schema DEMO &uuml;ber FDAL.
 *
 * <p>Die Vorgaben passen zur lokalen Instanz (PDBORCL, DEMO). Wer woanders
 * hin will, setzt die Systemeigenschaften {@code logistik.db.url},
 * {@code logistik.db.user} und {@code logistik.db.password}.</p>
 *
 * @author Dan
 */
public final class LogDb {

    public static final String URL = "jdbc:oracle:thin:@//localhost:1521/PDBORCL";
    public static final String USER = "DEMO";
    public static final String PASSWORD = "de";

    private LogDb() {
    }

    /** Neuer Verbindungsverwalter; der Aufrufer schlie&szlig;t ihn. */
    public static FDatabaseManager oeffnen() {
        FDbConfig cfg = FDbConfig.builder()
                .url(System.getProperty("logistik.db.url", URL))
                .user(System.getProperty("logistik.db.user", USER))
                .password(System.getProperty("logistik.db.password", PASSWORD))
                .driverClass("oracle.jdbc.OracleDriver")
                .dialect(FSqlDialect.ORACLE)
                .poolSize(2)
                // Bei zwei Verbindungen lieber nach 5 s mit Meldung scheitern
                // als lange warten, falls doch einmal beide belegt sind.
                .borrowTimeoutMs(5000)
                .build();
        return new FDatabaseManager(cfg);
    }
}
