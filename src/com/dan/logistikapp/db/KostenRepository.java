package com.dan.logistikapp.db;

import com.dan.fdal.FParams;
import com.dan.fdal.FRowMapper;
import com.dan.fdal.FSql;
import com.dan.logistikapp.model.KostenZeile;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Kosten je Tag, Zonenpaar und Ware aus LOG_KOSTEN_V. Die Auswertung
 * (heute / gesamt, je Zone, je Ware) rechnet die Kostentafel daraus.
 *
 * @author Dan
 */
public final class KostenRepository {

    public static final String SQL_ALLE =
            "SELECT tag, von_zone, nach_zone, ware_code, ware, fahrten, zollfahrten, km, "
          + "       fracht_eur, zoll_eur, standgeld_eur "
          + "  FROM log_kosten_v ORDER BY tag, von_zone, nach_zone, ware_code";

    public static final FRowMapper<KostenZeile> MAPPER = new FRowMapper<KostenZeile>() {
        @Override
        public KostenZeile map(ResultSet rs) throws SQLException {
            Timestamp t = rs.getTimestamp("TAG");
            return new KostenZeile(t == null ? 0 : t.getTime(), rs.getString("VON_ZONE"), rs.getString("NACH_ZONE"),
                    rs.getString("WARE_CODE"), rs.getString("WARE"), rs.getInt("FAHRTEN"), rs.getInt("ZOLLFAHRTEN"),
                    rs.getDouble("KM"), rs.getBigDecimal("FRACHT_EUR"), rs.getBigDecimal("ZOLL_EUR"),
                    rs.getBigDecimal("STANDGELD_EUR"));
        }
    };

    private final FSql sql;

    public KostenRepository(FSql sql) {
        this.sql = sql;
    }

    public List<KostenZeile> alle() {
        return sql.queryList(SQL_ALLE, FParams.NONE, MAPPER);
    }
}
