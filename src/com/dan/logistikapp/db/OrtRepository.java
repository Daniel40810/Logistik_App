package com.dan.logistikapp.db;

import com.dan.fdal.FParams;
import com.dan.fdal.FRowMapper;
import com.dan.fdal.FSql;
import com.dan.logistikapp.model.Ort;
import com.dan.logistikapp.model.Zone;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * St&auml;dte aus LOG_ORT_V. Nur lesend.
 *
 * @author Dan
 */
public final class OrtRepository {

    public static final String SQL_ALLE =
            "SELECT ort_id, name, iso2, land, zone_typ, laenge, breite, kapazitaet_teu "
          + "  FROM log_ort_v ORDER BY ort_id";

    public static final FRowMapper<Ort> MAPPER = new FRowMapper<Ort>() {
        @Override
        public Ort map(ResultSet rs) throws SQLException {
            return new Ort(rs.getInt("ORT_ID"), rs.getString("NAME"),
                    rs.getString("ISO2").trim(), rs.getString("LAND"),
                    Zone.vonCode(rs.getString("ZONE_TYP")),
                    rs.getDouble("LAENGE"), rs.getDouble("BREITE"), kapazitaet(rs));
        }
    };

    private static Integer kapazitaet(ResultSet rs) throws SQLException {
        int k = rs.getInt("KAPAZITAET_TEU");
        return rs.wasNull() ? null : Integer.valueOf(k);
    }

    private final FSql sql;

    public OrtRepository(FSql sql) {
        this.sql = sql;
    }

    public List<Ort> alle() {
        return sql.queryList(SQL_ALLE, FParams.NONE, MAPPER);
    }
}
