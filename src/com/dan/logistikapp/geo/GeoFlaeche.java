package com.dan.logistikapp.geo;

import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Eine (Multi-)Polygonfl&auml;che in L&auml;nge/Breite (WGS84).
 *
 * <p>Jedes Polygon ist eine Liste von Ringen, der erste ist der Au&szlig;enring.
 * Ein Ring ist ein gepacktes Feld {l0, b0, l1, b1, ...}, geschlossen
 * (erster = letzter Punkt), wie es aus dem WKT kommt.</p>
 *
 * @author Dan
 */
public final class GeoFlaeche {

    private final List<List<double[]>> polygone;

    public GeoFlaeche(List<List<double[]>> polygone) {
        this.polygone = Collections.unmodifiableList(new ArrayList<List<double[]>>(polygone));
    }

    /**
     * Aus den beiden Feldern eines SDO_GEOMETRY-Polygons (2D, gerade Kanten).
     *
     * <p>SDO_ELEM_INFO sind Tripel (Offset ab 1, ETYPE, Interpretation):
     * 1003 beginnt einen Au&szlig;enring und damit ein neues Polygon, 2003 ist ein
     * Loch im laufenden Polygon. B&ouml;gen, Rechtecke und zusammengesetzte
     * Ringe (1005/2005) kommen in den Landesfl&auml;chen nicht vor und werden
     * mit einer klaren Meldung abgewiesen statt halb gelesen.</p>
     */
    public static GeoFlaeche ausSdo(int gtype, long[] elemInfo, double[] ordinaten) {
        int dim = gtype / 1000;
        if (dim == 0) {
            dim = 2;
        }
        if (dim != 2) {
            throw new IllegalArgumentException("Nur 2D-Flaechen, nicht SDO_GTYPE " + gtype);
        }
        List<List<double[]>> polys = new ArrayList<List<double[]>>();
        List<double[]> aktuell = null;
        for (int i = 0; i + 2 < elemInfo.length; i += 3) {
            int start = (int) elemInfo[i] - 1;
            int etype = (int) elemInfo[i + 1];
            int interp = (int) elemInfo[i + 2];
            int ende = (i + 5 < elemInfo.length) ? (int) elemInfo[i + 3] - 1 : ordinaten.length;
            if (interp != 1 || (etype != 1003 && etype != 2003)) {
                throw new IllegalArgumentException("Nicht unterstuetzt: ETYPE " + etype
                        + " Interpretation " + interp);
            }
            double[] ring = new double[ende - start];
            System.arraycopy(ordinaten, start, ring, 0, ring.length);
            if (etype == 1003 || aktuell == null) {
                aktuell = new ArrayList<double[]>();
                polys.add(aktuell);
            }
            aktuell.add(ring);
        }
        return new GeoFlaeche(polys);
    }

    public List<List<double[]>> getPolygone() {
        return polygone;
    }

    /** Anzahl der St&uuml;tzpunkte, gez&auml;hlt wie SDO_UTIL.GETNUMVERTICES. */
    public int stuetzpunkte() {
        int n = 0;
        for (List<double[]> p : polygone) {
            for (double[] ring : p) {
                n += ring.length / 2;
            }
        }
        return n;
    }

    /**
     * Projiziert die Fl&auml;che nach EPSG:3035 als Pfad in Metern.
     * Hochwert w&auml;chst nach Norden - die Ansicht dreht die y-Achse.
     * Regel "gerade/ungerade", damit L&ouml;cher L&ouml;cher bleiben.
     */
    public Path2D.Double projiziert() {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        double[] p = new double[2];
        for (List<double[]> poly : polygone) {
            for (double[] ring : poly) {
                for (int i = 0; i < ring.length; i += 2) {
                    Laea3035.vor(ring[i], ring[i + 1], p);
                    if (i == 0) {
                        path.moveTo(p[0], p[1]);
                    } else {
                        path.lineTo(p[0], p[1]);
                    }
                }
                path.closePath();
            }
        }
        return path;
    }

    /**
     * Fl&auml;che in Quadratkilometern, gerechnet in der fl&auml;chentreuen
     * Projektion: Au&szlig;enringe positiv, L&ouml;cher abgezogen.
     */
    public double flaecheQkm() {
        double summe = 0;
        double[] a = new double[2];
        double[] b = new double[2];
        for (List<double[]> poly : polygone) {
            for (int k = 0; k < poly.size(); k++) {
                double[] ring = poly.get(k);
                double s = 0;
                for (int i = 0; i + 3 < ring.length; i += 2) {
                    Laea3035.vor(ring[i], ring[i + 1], a);
                    Laea3035.vor(ring[i + 2], ring[i + 3], b);
                    s += a[0] * b[1] - b[0] * a[1];
                }
                double f = Math.abs(s) / 2;
                summe += (k == 0) ? f : -f;
            }
        }
        return summe / 1e6;
    }
}
