package com.dan.logistikapp.db;

import com.dan.fdal.FParams;
import com.dan.fdal.FRowMapper;
import com.dan.fdal.FSql;
import com.dan.logistikapp.model.Auftrag;
import com.dan.logistikapp.model.Ware;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Auftr&auml;ge aus LOG_AUFTRAG_V: alle offenen und die zuletzt erledigten.
 * Geschrieben wird &uuml;ber LOG_API ({@link DbContainerDienst}).
 *
 * @author Dan
 */
public final class AuftragRepository {

    private static final String SPALTEN =
            "SELECT auftrag_id, container_id, kennung_anzeige, ware, farbe_hex, ort_jetzt_id, ort_jetzt, "
          + "       container_status, nach_ort_id, nach_ort, faellig_am, status, grund, versuche, "
          + "       bewegung_id, ausgefuehrt_von, erledigt_am, rang "
          + "  FROM log_auftrag_v ";

    /** Offene zuerst nach Termin, dann die j&uuml;ngsten 40 abgeschlossenen. */
    public static final String SQL_LISTE = SPALTEN
          + " WHERE status = 'OFFEN' "
          + "    OR auftrag_id IN (SELECT auftrag_id FROM (SELECT auftrag_id, "
          + "                              ROW_NUMBER() OVER (ORDER BY erledigt_am DESC, auftrag_id DESC) rn "
          + "                              FROM log_auftrag WHERE status <> 'OFFEN') WHERE rn <= 40) "
          + " ORDER BY CASE status WHEN 'OFFEN' THEN 0 ELSE 1 END, "
          + "          CASE status WHEN 'OFFEN' THEN faellig_am END, erledigt_am DESC, auftrag_id";

    /** Ein Parameter: die Auftragsnummer. */
    public static final String SQL_EINER = SPALTEN + " WHERE auftrag_id = ?";

    public static final FRowMapper<Auftrag> MAPPER = new FRowMapper<Auftrag>() {
        @Override
        public Auftrag map(ResultSet rs) throws SQLException {
            Timestamp f = rs.getTimestamp("FAELLIG_AM");
            Timestamp e = rs.getTimestamp("ERLEDIGT_AM");
            return new Auftrag(rs.getLong("AUFTRAG_ID"), rs.getInt("CONTAINER_ID"), rs.getString("KENNUNG_ANZEIGE"),
                    rs.getString("WARE"), Ware.farbe(rs.getString("FARBE_HEX")), rs.getInt("ORT_JETZT_ID"),
                    rs.getString("ORT_JETZT"), rs.getString("CONTAINER_STATUS"), rs.getInt("NACH_ORT_ID"),
                    rs.getString("NACH_ORT"), f == null ? 0 : f.getTime(),
                    Auftrag.Status.valueOf(rs.getString("STATUS")), rs.getString("GRUND"), rs.getInt("VERSUCHE"),
                    rs.getLong("BEWEGUNG_ID"), rs.getString("AUSGEFUEHRT_VON"), e == null ? 0 : e.getTime(),
                    rs.getInt("RANG"));
        }
    };

    private final FSql sql;

    public AuftragRepository(FSql sql) {
        this.sql = sql;
    }

    public List<Auftrag> liste() {
        return sql.queryList(SQL_LISTE, FParams.NONE, MAPPER);
    }
}
