package com.dan.logistikapp.db;

import com.dan.fdal.FParams;
import com.dan.fdal.FRowMapper;
import com.dan.fdal.FSql;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.Ware;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Container aus LOG_CONTAINER_V. Nur lesend - geschrieben wird ab Phase 4
 * ausschlie&szlig;lich &uuml;ber LOG_API.
 *
 * @author Dan
 */
public final class ContainerRepository {

    public static final String SQL_ALLE =
            "SELECT container_id, kennung, groesse_fuss, typ_code, ware_code, ware, farbe_hex, "
          + "       ort_id, ort, status "
          + "  FROM log_container_v ORDER BY container_id";

    public static final FRowMapper<ContainerInfo> MAPPER = new FRowMapper<ContainerInfo>() {
        @Override
        public ContainerInfo map(ResultSet rs) throws SQLException {
            return new ContainerInfo(rs.getInt("CONTAINER_ID"), rs.getString("KENNUNG"),
                    rs.getInt("GROESSE_FUSS"), rs.getString("TYP_CODE"), rs.getString("WARE_CODE"),
                    rs.getString("WARE"), Ware.farbe(rs.getString("FARBE_HEX")),
                    rs.getInt("ORT_ID"), rs.getString("ORT"), rs.getString("STATUS"));
        }
    };

    private final FSql sql;

    public ContainerRepository(FSql sql) {
        this.sql = sql;
    }

    public List<ContainerInfo> alle() {
        return sql.queryList(SQL_ALLE, FParams.NONE, MAPPER);
    }
}
