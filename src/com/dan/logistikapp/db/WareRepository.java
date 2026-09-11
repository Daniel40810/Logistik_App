package com.dan.logistikapp.db;

import com.dan.fdal.FParams;
import com.dan.fdal.FRowMapper;
import com.dan.fdal.FSql;
import com.dan.logistikapp.model.Ware;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Warenarten aus LOG_WARE, in der Reihenfolge der Legende.
 *
 * @author Dan
 */
public final class WareRepository {

    public static final String SQL_ALLE =
            "SELECT code, name, farbe_hex, kuehlpflichtig, sortierung FROM log_ware ORDER BY sortierung, code";

    public static final FRowMapper<Ware> MAPPER = new FRowMapper<Ware>() {
        @Override
        public Ware map(ResultSet rs) throws SQLException {
            return new Ware(rs.getString("CODE"), rs.getString("NAME"), Ware.farbe(rs.getString("FARBE_HEX")),
                    "J".equals(rs.getString("KUEHLPFLICHTIG")), rs.getInt("SORTIERUNG"));
        }
    };

    private final FSql sql;

    public WareRepository(FSql sql) {
        this.sql = sql;
    }

    public List<Ware> alle() {
        return sql.queryList(SQL_ALLE, FParams.NONE, MAPPER);
    }
}
