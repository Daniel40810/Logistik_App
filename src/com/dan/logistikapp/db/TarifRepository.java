package com.dan.logistikapp.db;

import com.dan.fdal.FParams;
import com.dan.fdal.FRowMapper;
import com.dan.fdal.FSql;
import com.dan.logistikapp.model.Tarif;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tarife aus LOG_TARIF und die Frachts&auml;tze aus LOG_WARE.
 *
 * @author Dan
 */
public final class TarifRepository {

    public static final String SQL_TARIFE = "SELECT schluessel, wert FROM log_tarif";
    public static final String SQL_SAETZE = "SELECT code, fracht_eur_teu_km FROM log_ware "
            + "WHERE fracht_eur_teu_km IS NOT NULL";

    private static final FRowMapper<Object[]> PAAR = new FRowMapper<Object[]>() {
        @Override
        public Object[] map(ResultSet rs) throws SQLException {
            return new Object[] {rs.getString(1), rs.getBigDecimal(2)};
        }
    };

    private final FSql sql;

    public TarifRepository(FSql sql) {
        this.sql = sql;
    }

    public Tarif lade() {
        return new Tarif(karte(sql.queryList(SQL_TARIFE, FParams.NONE, PAAR)),
                karte(sql.queryList(SQL_SAETZE, FParams.NONE, PAAR)));
    }

    /** Dasselbe &uuml;ber eine Verbindung - f&uuml;r den Durchstich in seiner Transaktion. */
    public static Tarif lade(Connection c) throws SQLException {
        return new Tarif(lies(c, SQL_TARIFE), lies(c, SQL_SAETZE));
    }

    private static Map<String, BigDecimal> lies(Connection c, String q) throws SQLException {
        Map<String, BigDecimal> m = new HashMap<String, BigDecimal>();
        Statement st = c.createStatement();
        try {
            ResultSet rs = st.executeQuery(q);
            while (rs.next()) {
                m.put(rs.getString(1), rs.getBigDecimal(2));
            }
            rs.close();
        } finally {
            st.close();
        }
        return m;
    }

    private static Map<String, BigDecimal> karte(List<Object[]> l) {
        Map<String, BigDecimal> m = new HashMap<String, BigDecimal>();
        for (Object[] p : l) {
            m.put((String) p[0], (BigDecimal) p[1]);
        }
        return m;
    }
}
