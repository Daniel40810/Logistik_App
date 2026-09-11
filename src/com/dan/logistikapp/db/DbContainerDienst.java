package com.dan.logistikapp.db;

import com.dan.fdal.FDataAccessException;
import com.dan.fdal.FDatabaseManager;
import com.dan.fdal.FSession;
import com.dan.fdal.FUnitOfWork;
import com.dan.logistikapp.dienst.ContainerDienst;
import com.dan.logistikapp.dienst.DienstFehler;
import com.dan.logistikapp.dienst.Fahrt;
import com.dan.logistikapp.model.Bewegung;
import com.dan.logistikapp.model.ContainerAnlage;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.Ort;
import com.dan.logistikapp.model.OrtAnlage;
import com.dan.logistikapp.model.ZollFall;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * Schreibt &uuml;ber LOG_API - und nur dar&uuml;ber. Jede Verschiebung ist eine
 * eigene Transaktion ({@link FDatabaseManager#inTransaction}).
 *
 * <p>Die eigentlichen Aufrufe stehen als statische Methoden mit einer
 * {@link Connection}: so ruft der Durchstich genau diesen Code in seiner
 * eigenen, am Ende zur&uuml;ckgerollten Transaktion.</p>
 *
 * @author Dan
 */
public final class DbContainerDienst implements ContainerDienst {

    private final FDatabaseManager db;
    private final ContainerRepository container;
    private final ZollRepository zoll;
    private final BewegungRepository bewegung;
    private final KostenRepository kosten;
    private final AuftragRepository auftrag;
    private final OrtRepository orte;

    public DbContainerDienst(FDatabaseManager db) {
        this.db = db;
        this.container = new ContainerRepository(db);
        this.zoll = new ZollRepository(db);
        this.bewegung = new BewegungRepository(db);
        this.kosten = new KostenRepository(db);
        this.auftrag = new AuftragRepository(db);
        this.orte = new OrtRepository(db);
    }

    @Override
    public ContainerInfo containerAnlegen(final ContainerAnlage anlage) throws DienstFehler {
        final ContainerInfo[] ergebnis = new ContainerInfo[1];
        try {
            db.inTransaction(new FUnitOfWork() {
                @Override
                public void run(FSession s) throws Exception {
                    int id = containerAnlegen(s.connection(), anlage);
                    ergebnis[0] = containerLesen(s.connection(), id);
                }
            });
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
        return ergebnis[0];
    }

    @Override
    public Ort ortAnlegen(final OrtAnlage anlage) throws DienstFehler {
        final Ort[] ergebnis = new Ort[1];
        try {
            db.inTransaction(new FUnitOfWork() {
                @Override
                public void run(FSession s) throws Exception {
                    int id = ortAnlegen(s.connection(), anlage);
                    ergebnis[0] = ortLesen(s.connection(), id);
                }
            });
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
        return ergebnis[0];
    }

    @Override
    public List<Ort> orte() throws DienstFehler {
        try {
            return orte.alle();
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
    }

    @Override
    public Fahrt verschieben(final int containerId, final int nachOrtId) throws DienstFehler {
        final Fahrt[] ergebnis = new Fahrt[1];
        try {
            db.inTransaction(new FUnitOfWork() {
                @Override
                public void run(FSession s) throws Exception {
                    ergebnis[0] = verschieben(s.connection(), containerId, nachOrtId);
                }
            });
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
        return ergebnis[0];
    }

    @Override
    public void zollFreigeben(final int containerId) throws DienstFehler {
        try {
            db.inTransaction(new FUnitOfWork() {
                @Override
                public void run(FSession s) throws Exception {
                    zollFreigeben(s.connection(), containerId);
                }
            });
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
    }

    @Override
    public List<ContainerInfo> container() throws DienstFehler {
        try {
            return container.alle();
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
    }

    @Override
    public List<ZollFall> zollFaelle() throws DienstFehler {
        try {
            return zoll.alle();
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
    }

    @Override
    public List<Bewegung> letzteBewegungen(int max) throws DienstFehler {
        try {
            return bewegung.letzte(max);
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
    }

    @Override
    public List<Bewegung> alleBewegungen() throws DienstFehler {
        try {
            return bewegung.alle();
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
    }

    @Override
    public List<Bewegung> historie(int containerId) throws DienstFehler {
        try {
            return bewegung.historie(containerId);
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
    }

    @Override
    public List<com.dan.logistikapp.model.KostenZeile> kosten() throws DienstFehler {
        try {
            return kosten.alle();
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
    }

    @Override
    public List<com.dan.logistikapp.model.Auftrag> auftraege() throws DienstFehler {
        try {
            return auftrag.liste();
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
    }

    @Override
    public long auftragAnlegen(final int containerId, final int nachOrtId, final long faelligMs) throws DienstFehler {
        final long[] id = new long[1];
        try {
            db.inTransaction(new FUnitOfWork() {
                @Override
                public void run(FSession s) throws Exception {
                    id[0] = auftragAnlegen(s.connection(), containerId, nachOrtId, new java.sql.Timestamp(faelligMs));
                }
            });
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
        return id[0];
    }

    @Override
    public void auftragStornieren(final long auftragId) throws DienstFehler {
        try {
            db.inTransaction(new FUnitOfWork() {
                @Override
                public void run(FSession s) throws Exception {
                    auftragStornieren(s.connection(), auftragId);
                }
            });
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
    }

    @Override
    public com.dan.logistikapp.dienst.AuftragErgebnis auftragAusfuehren(final long auftragId) throws DienstFehler {
        final com.dan.logistikapp.dienst.AuftragErgebnis[] e = new com.dan.logistikapp.dienst.AuftragErgebnis[1];
        try {
            db.inTransaction(new FUnitOfWork() {
                @Override
                public void run(FSession s) throws Exception {
                    e[0] = auftragMitFahrt(s.connection(), auftragId, "APP");
                }
            });
        } catch (FDataAccessException x) {
            throw uebersetze(x);
        }
        return e[0];
    }

    @Override
    public String standMarke() throws DienstFehler {
        try {
            return db.queryOne("SELECT log_api.stand_marke AS m FROM dual", com.dan.fdal.FParams.NONE,
                    new com.dan.fdal.FRowMapper<String>() {
                        @Override
                        public String map(ResultSet rs) throws SQLException {
                            return rs.getString(1);
                        }
                    });
        } catch (FDataAccessException e) {
            throw uebersetze(e);
        }
    }

    // ------------------------------------------------------------ Aufrufe

    /** LOG_API.container_anlegen und den neuen Stand zur&uuml;cklesen. Kein Commit. */
    public static int containerAnlegen(Connection c, ContainerAnlage anlage) throws SQLException {
        CallableStatement cs = c.prepareCall("{call log_api.container_anlegen(?, ?, ?, ?, ?)}");
        try {
            if (anlage.getKennung() == null || anlage.getKennung().trim().isEmpty()) {
                cs.setNull(1, Types.VARCHAR);
            } else {
                cs.setString(1, anlage.getKennung());
            }
            cs.setInt(2, anlage.getGroesseFuss());
            cs.setString(3, anlage.getWareCode());
            cs.setInt(4, anlage.getOrtId());
            cs.registerOutParameter(5, Types.NUMERIC);
            cs.execute();
            return cs.getInt(5);
        } finally {
            cs.close();
        }
    }

    /** LOG_API.ort_anlegen. Kein Commit. */
    public static int ortAnlegen(Connection c, OrtAnlage anlage) throws SQLException {
        CallableStatement cs = c.prepareCall("{call log_api.ort_anlegen(?, ?, ?, ?, ?, ?)}");
        try {
            cs.setString(1, anlage.getName());
            cs.setString(2, anlage.getIso2());
            cs.setDouble(3, anlage.getLaenge());
            cs.setDouble(4, anlage.getBreite());
            if (anlage.getKapazitaetTeu() == null) {
                cs.setNull(5, Types.NUMERIC);
            } else {
                cs.setInt(5, anlage.getKapazitaetTeu());
            }
            cs.registerOutParameter(6, Types.NUMERIC);
            cs.execute();
            return cs.getInt(6);
        } finally {
            cs.close();
        }
    }

    private static ContainerInfo containerLesen(Connection c, int id) throws SQLException {
        PreparedStatement ps = c.prepareStatement(ContainerRepository.SQL_ALLE.replace(
                " ORDER BY container_id", " WHERE container_id = ?"));
        try {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            try {
                if (!rs.next()) {
                    throw new SQLException("Container " + id + " nach dem Anlegen nicht gefunden");
                }
                return ContainerRepository.MAPPER.map(rs);
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }
    }

    private static Ort ortLesen(Connection c, int id) throws SQLException {
        PreparedStatement ps = c.prepareStatement(OrtRepository.SQL_ALLE.replace(
                " ORDER BY ort_id", " WHERE ort_id = ?"));
        try {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            try {
                if (!rs.next()) {
                    throw new SQLException("Ort " + id + " nach dem Anlegen nicht gefunden");
                }
                return OrtRepository.MAPPER.map(rs);
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }
    }

    /**
     * LOG_API.auftrag_ausfuehren und, wenn gefahren wurde, die Fahrt so
     * zur&uuml;ckgelesen wie bei {@link #verschieben(Connection, int, int)}. Kein Commit.
     */
    public static com.dan.logistikapp.dienst.AuftragErgebnis auftragMitFahrt(Connection c, long auftragId, String von)
            throws SQLException {
        Ausfuehrung a = auftragAusfuehren(c, auftragId, von);
        Fahrt f = a.getBewegungId() > 0 ? fahrtLesen(c, a.getBewegungId()) : null;
        String grund = einWertLang(c, "SELECT grund FROM log_auftrag WHERE auftrag_id = ?", auftragId);
        return new com.dan.logistikapp.dienst.AuftragErgebnis(a.getErgebnis(), f,
                "ERLEDIGT".equals(a.getErgebnis()) && f != null ? null : grund);
    }

    private static String einWertLang(Connection c, String q, long p) throws SQLException {
        PreparedStatement ps = c.prepareStatement(q);
        try {
            ps.setLong(1, p);
            ResultSet rs = ps.executeQuery();
            try {
                return rs.next() ? rs.getString(1) : null;
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }
    }

    /** LOG_API.verschieben und die geschriebene Bewegung zur&uuml;cklesen. Kein Commit. */
    public static Fahrt verschieben(Connection c, int containerId, int nachOrtId) throws SQLException {
        long bew;
        CallableStatement cs = c.prepareCall("{call log_api.verschieben(?, ?, ?)}");
        try {
            cs.setInt(1, containerId);
            cs.setInt(2, nachOrtId);
            cs.registerOutParameter(3, Types.NUMERIC);
            cs.execute();
            bew = cs.getLong(3);
        } finally {
            cs.close();
        }
        return fahrtLesen(c, bew);
    }

    /** Eine geschriebene Bewegung als Fahrt: Zonen, Zoll, Meter, Fracht + Zoll. */
    public static Fahrt fahrtLesen(Connection c, long bew) throws SQLException {
        PreparedStatement ps = c.prepareStatement(
                "SELECT von_zone, nach_zone, zoll, distanz_m, fracht_eur + zoll_eur FROM log_bewegung WHERE bewegung_id = ?");
        try {
            ps.setLong(1, bew);
            ResultSet rs = ps.executeQuery();
            try {
                if (!rs.next()) {
                    throw new SQLException("Bewegung " + bew + " nach dem Schreiben nicht gefunden");
                }
                return new Fahrt(bew, rs.getString(1), rs.getString(2), "J".equals(rs.getString(3)), rs.getLong(4),
                        rs.getBigDecimal(5));
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }
    }

    /** Ergebnis von LOG_API.auftrag_ausfuehren. */
    public static final class Ausfuehrung {
        private final String ergebnis;
        private final long bewegungId;

        public Ausfuehrung(String ergebnis, long bewegungId) {
            this.ergebnis = ergebnis;
            this.bewegungId = bewegungId;
        }

        /** ERLEDIGT, BLOCKIERT, GESCHEITERT, WARTET, NICHT_FAELLIG oder GESPERRT. */
        public String getErgebnis() {
            return ergebnis;
        }

        /** Die gefahrene Bewegung, 0 ohne Fahrt. */
        public long getBewegungId() {
            return bewegungId;
        }

        @Override
        public String toString() {
            return ergebnis + (bewegungId > 0 ? " (Bewegung " + bewegungId + ")" : "");
        }
    }

    /** LOG_API.auftrag_anlegen. Kein Commit. */
    public static long auftragAnlegen(Connection c, int containerId, int nachOrtId, java.sql.Timestamp faellig)
            throws SQLException {
        CallableStatement cs = c.prepareCall("{call log_api.auftrag_anlegen(?, ?, ?, ?)}");
        try {
            cs.setInt(1, containerId);
            cs.setInt(2, nachOrtId);
            cs.setTimestamp(3, faellig);
            cs.registerOutParameter(4, Types.NUMERIC);
            cs.execute();
            return cs.getLong(4);
        } finally {
            cs.close();
        }
    }

    /** LOG_API.auftrag_stornieren. Kein Commit. */
    public static void auftragStornieren(Connection c, long auftragId) throws SQLException {
        CallableStatement cs = c.prepareCall("{call log_api.auftrag_stornieren(?)}");
        try {
            cs.setLong(1, auftragId);
            cs.execute();
        } finally {
            cs.close();
        }
    }

    /** LOG_API.auftrag_ausfuehren. Kein Commit. */
    public static Ausfuehrung auftragAusfuehren(Connection c, long auftragId, String von) throws SQLException {
        CallableStatement cs = c.prepareCall("{call log_api.auftrag_ausfuehren(?, ?, ?, ?)}");
        try {
            cs.setLong(1, auftragId);
            cs.setString(2, von);
            cs.registerOutParameter(3, Types.VARCHAR);
            cs.registerOutParameter(4, Types.NUMERIC);
            cs.execute();
            long b = cs.getLong(4);
            return new Ausfuehrung(cs.getString(3), cs.wasNull() ? 0 : b);
        } finally {
            cs.close();
        }
    }

    /** LOG_API.stand_marke - &auml;ndert sich bei jeder Fahrt, Freigabe und Auftrags&auml;nderung. */
    public static String standMarke(Connection c) throws SQLException {
        return einWert(c, "SELECT log_api.stand_marke FROM dual", null, null);
    }

    /** LOG_API.kosten_vorschau als Betrag, {@code null} wenn nicht berechenbar. */
    public static java.math.BigDecimal kostenVorschau(Connection c, int containerId, int nachOrtId)
            throws SQLException {
        String s = einWert(c, "SELECT TO_CHAR(log_api.kosten_vorschau(?, ?), 'TM9', "
                + "'NLS_NUMERIC_CHARACTERS=''.,''') FROM dual", containerId, nachOrtId);
        return s == null ? null : new java.math.BigDecimal(s);
    }

    private static String einWert(Connection c, String q, Integer a, Integer b) throws SQLException {
        PreparedStatement ps = c.prepareStatement(q);
        try {
            if (a != null) {
                ps.setInt(1, a);
                ps.setInt(2, b);
            }
            ResultSet rs = ps.executeQuery();
            try {
                return rs.next() ? rs.getString(1) : null;
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }
    }

    /** LOG_API.zoll_freigeben. Kein Commit. */
    public static void zollFreigeben(Connection c, int containerId) throws SQLException {
        CallableStatement cs = c.prepareCall("{call log_api.zoll_freigeben(?)}");
        try {
            cs.setInt(1, containerId);
            cs.execute();
        } finally {
            cs.close();
        }
    }

    /**
     * Sucht in der Ursachenkette die SQLException und macht aus ihrer Nummer
     * eine {@link DienstFehler.Art}. Die Meldung ist der Text aus
     * RAISE_APPLICATION_ERROR, ohne ORA-Vorspann und Aufrufstapel.
     */
    public static DienstFehler uebersetze(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException) {
                SQLException e = (SQLException) c;
                return new DienstFehler(DienstFehler.Art.ausOra(e.getErrorCode()), meldung(e.getMessage()), t);
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return new DienstFehler(DienstFehler.Art.DATENBANK, String.valueOf(t.getMessage()), t);
    }

    static String meldung(String ora) {
        if (ora == null) {
            return "";
        }
        String s = ora;
        int nl = s.indexOf('\n');
        if (nl > 0) {
            s = s.substring(0, nl);
        }
        if (s.startsWith("ORA-") && s.indexOf(": ") > 0) {
            s = s.substring(s.indexOf(": ") + 2);
        }
        return s.trim();
    }
}
