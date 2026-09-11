package com.dan.logistikapp.db;

import com.dan.fdal.FParams;
import com.dan.fdal.FRowMapper;
import com.dan.fdal.FSql;
import com.dan.logistikapp.model.Bewegung;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Die Historie aus LOG_BEWEGUNG_V: die j&uuml;ngsten Fahrten f&uuml;r die Liste,
 * alle Fahrten eines Containers f&uuml;r seine Spur auf der Karte.
 *
 * <p>Die Reihenfolge kommt aus der Bewegungsnummer, nicht aus dem
 * Zeitpunkt: zwei Fahrten in derselben Millisekunde bleiben so eindeutig
 * geordnet.</p>
 *
 * @author Dan
 */
public final class BewegungRepository {

    private static final String SPALTEN =
            "SELECT bewegung_id, container_id, kennung_anzeige, von_ort_id, von_ort, nach_ort_id, nach_ort, "
          + "       von_zone, nach_zone, zoll, distanz_m, zeitpunkt, "
          + "       fracht_eur, zoll_eur, standgeld_eur, freigegeben_am, ausgeloest "
          + "  FROM log_bewegung_v ";

    /** Ein Parameter: h&ouml;chstens so viele Zeilen. */
    public static final String SQL_LETZTE = SPALTEN + "ORDER BY bewegung_id DESC FETCH FIRST ? ROWS ONLY";

    /** Ein Parameter: die Container-Nummer. */
    public static final String SQL_HISTORIE = SPALTEN + "WHERE container_id = ? ORDER BY bewegung_id";

    /** Alle Fahrten, die &auml;lteste zuerst - die Zeitreise braucht die ganze Historie. */
    public static final String SQL_ALLE = SPALTEN + "ORDER BY zeitpunkt, bewegung_id";

    public static final FRowMapper<Bewegung> MAPPER = new FRowMapper<Bewegung>() {
        @Override
        public Bewegung map(ResultSet rs) throws SQLException {
            Timestamp t = rs.getTimestamp("ZEITPUNKT");
            Timestamp f = rs.getTimestamp("FREIGEGEBEN_AM");
            return new Bewegung(rs.getLong("BEWEGUNG_ID"), rs.getInt("CONTAINER_ID"), rs.getString("KENNUNG_ANZEIGE"),
                    rs.getInt("VON_ORT_ID"), rs.getString("VON_ORT"), rs.getInt("NACH_ORT_ID"),
                    rs.getString("NACH_ORT"), rs.getString("VON_ZONE"), rs.getString("NACH_ZONE"),
                    "J".equals(rs.getString("ZOLL")), rs.getLong("DISTANZ_M"), t == null ? 0 : t.getTime())
                    .mitKosten(rs.getBigDecimal("FRACHT_EUR"), rs.getBigDecimal("ZOLL_EUR"),
                            rs.getBigDecimal("STANDGELD_EUR"), f == null ? 0 : f.getTime(), rs.getString("AUSGELOEST"));
        }
    };

    private final FSql sql;

    public BewegungRepository(FSql sql) {
        this.sql = sql;
    }

    public List<Bewegung> letzte(final int max) {
        return sql.queryList(SQL_LETZTE, zahl(max), MAPPER);
    }

    public List<Bewegung> alle() {
        return sql.queryList(SQL_ALLE, FParams.NONE, MAPPER);
    }

    public List<Bewegung> historie(final int containerId) {
        return sql.queryList(SQL_HISTORIE, zahl(containerId), MAPPER);
    }

    private static FParams zahl(final int n) {
        return new FParams() {
            @Override
            public void bind(PreparedStatement ps) throws SQLException {
                ps.setInt(1, n);
            }
        };
    }
}
