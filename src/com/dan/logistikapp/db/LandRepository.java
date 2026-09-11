package com.dan.logistikapp.db;

import com.dan.fdal.FParams;
import com.dan.fdal.FRowMapper;
import com.dan.fdal.FSql;
import com.dan.logistikapp.geo.GeoFlaeche;
import com.dan.logistikapp.model.Land;
import com.dan.logistikapp.model.Zone;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Struct;
import java.util.List;

/**
 * L&auml;nder mit Fl&auml;che aus LOG_LAND_V. Nur lesend.
 *
 * <p>Die Fl&auml;che kommt als SDO_GEOMETRY-Objekt ({@link Struct}), nicht als
 * WKT: TO_WKTGEOMETRY liefert tempor&auml;re CLOBs, und damit brachen sp&auml;tere
 * Abfragen auf derselben Verbindung ab. Ein Struct braucht keinen LOB.</p>
 *
 * @author Dan
 */
public final class LandRepository {

    static final String SQL_ALLE =
            "SELECT iso2, name, zone_typ, eu_mitglied, zollunion, schengen, grenze "
          + "  FROM log_land_v ORDER BY iso2";

    /** Zeile &rarr; Land. Das WKT wird hier gelesen, nicht in der Oberfl&auml;che. */
    public static final FRowMapper<Land> MAPPER = new FRowMapper<Land>() {
        @Override
        public Land map(ResultSet rs) throws SQLException {
            GeoFlaeche f = flaeche(rs.getObject("GRENZE"));
            return new Land(rs.getString("ISO2").trim(), rs.getString("NAME"),
                    Zone.vonCode(rs.getString("ZONE_TYP")),
                    "J".equals(rs.getString("EU_MITGLIED")),
                    "J".equals(rs.getString("ZOLLUNION")),
                    "J".equals(rs.getString("SCHENGEN")), f);
        }
    };

    /**
     * SDO_GEOMETRY &rarr; Fl&auml;che. Attribute in Typreihenfolge: GTYPE, SRID,
     * POINT, ELEM_INFO, ORDINATES.
     */
    static GeoFlaeche flaeche(Object o) throws SQLException {
        if (o == null) {
            return null;
        }
        if (!(o instanceof Struct)) {
            throw new SQLException("GRENZE ist kein SDO_GEOMETRY-Struct, sondern " + o.getClass().getName());
        }
        Object[] a = ((Struct) o).getAttributes();
        int gtype = ((Number) a[0]).intValue();
        long[] elem = zahlen((Array) a[3]);
        double[] ord = kommazahlen((Array) a[4]);
        return GeoFlaeche.ausSdo(gtype, elem, ord);
    }

    private static long[] zahlen(Array arr) throws SQLException {
        Object[] w = (Object[]) arr.getArray();
        long[] out = new long[w.length];
        for (int i = 0; i < w.length; i++) {
            out[i] = ((Number) w[i]).longValue();
        }
        arr.free();
        return out;
    }

    private static double[] kommazahlen(Array arr) throws SQLException {
        Object[] w = (Object[]) arr.getArray();
        double[] out = new double[w.length];
        for (int i = 0; i < w.length; i++) {
            out[i] = ((Number) w[i]).doubleValue();
        }
        arr.free();
        return out;
    }

    private final FSql sql;

    public LandRepository(FSql sql) {
        this.sql = sql;
    }

    public List<Land> alle() {
        return sql.queryList(SQL_ALLE, FParams.NONE, MAPPER);
    }
}
