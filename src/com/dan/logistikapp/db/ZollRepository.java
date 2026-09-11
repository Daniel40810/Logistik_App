package com.dan.logistikapp.db;

import com.dan.fdal.FParams;
import com.dan.fdal.FRowMapper;
import com.dan.fdal.FSql;
import com.dan.logistikapp.model.Ware;
import com.dan.logistikapp.model.ZollFall;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Wartende beim Zoll aus LOG_ZOLL_V, die am l&auml;ngsten Wartenden zuerst.
 *
 * @author Dan
 */
public final class ZollRepository {

    public static final String SQL_ALLE =
            "SELECT container_id, kennung_anzeige, ware, farbe_hex, ort_id, ort, von_ort, von_land, nach_land, "
          + "       von_zone, nach_zone, wartet_sek "
          + "  FROM log_zoll_v ORDER BY seit, container_id";

    public static final FRowMapper<ZollFall> MAPPER = new FRowMapper<ZollFall>() {
        @Override
        public ZollFall map(ResultSet rs) throws SQLException {
            return new ZollFall(rs.getInt("CONTAINER_ID"), rs.getString("KENNUNG_ANZEIGE"), rs.getString("WARE"),
                    Ware.farbe(rs.getString("FARBE_HEX")), rs.getInt("ORT_ID"), rs.getString("ORT"),
                    rs.getString("VON_ORT"), rs.getString("VON_LAND").trim(), rs.getString("NACH_LAND").trim(),
                    rs.getString("VON_ZONE"), rs.getString("NACH_ZONE"), rs.getLong("WARTET_SEK"),
                    System.currentTimeMillis());
        }
    };

    private final FSql sql;

    public ZollRepository(FSql sql) {
        this.sql = sql;
    }

    public List<ZollFall> alle() {
        return sql.queryList(SQL_ALLE, FParams.NONE, MAPPER);
    }
}
