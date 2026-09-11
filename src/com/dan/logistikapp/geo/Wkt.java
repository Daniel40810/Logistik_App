package com.dan.logistikapp.geo;

import java.util.ArrayList;
import java.util.List;

/**
 * Liest POLYGON und MULTIPOLYGON aus Well-Known Text, so wie
 * SDO_UTIL.TO_WKTGEOMETRY ihn liefert.
 *
 * <p>Absichtlich klein: andere Geometrietypen braucht die Karte nicht und
 * werden mit einer klaren Meldung abgewiesen statt halb gelesen.</p>
 *
 * @author Dan
 */
public final class Wkt {

    private final String s;
    private int pos;

    private Wkt(String s) {
        this.s = s;
    }

    /**
     * @throws IllegalArgumentException bei allem, was kein (Multi-)Polygon ist
     */
    public static GeoFlaeche lies(String wkt) {
        if (wkt == null) {
            return null;
        }
        return new Wkt(wkt).flaeche();
    }

    private GeoFlaeche flaeche() {
        String typ = wort();
        List<List<double[]>> polys = new ArrayList<List<double[]>>();
        if ("POLYGON".equals(typ)) {
            polys.add(polygon());
        } else if ("MULTIPOLYGON".equals(typ)) {
            erwarte('(');
            polys.add(polygon());
            while (naechstes() == ',') {
                pos++;
                polys.add(polygon());
            }
            erwarte(')');
        } else {
            throw new IllegalArgumentException("Nur POLYGON/MULTIPOLYGON, nicht " + typ);
        }
        return new GeoFlaeche(polys);
    }

    private List<double[]> polygon() {
        List<double[]> ringe = new ArrayList<double[]>();
        erwarte('(');
        ringe.add(ring());
        while (naechstes() == ',') {
            pos++;
            ringe.add(ring());
        }
        erwarte(')');
        return ringe;
    }

    private double[] ring() {
        erwarte('(');
        double[] buf = new double[64];
        int n = 0;
        while (true) {
            if (n + 2 > buf.length) {
                double[] g = new double[buf.length * 2];
                System.arraycopy(buf, 0, g, 0, n);
                buf = g;
            }
            buf[n++] = zahl();
            buf[n++] = zahl();
            char c = naechstes();
            if (c == ',') {
                pos++;
            } else if (c == ')') {
                pos++;
                break;
            } else {
                // dritte Koordinate (Z) wird ueberlesen
                zahl();
                c = naechstes();
                pos++;
                if (c == ')') {
                    break;
                }
                if (c != ',') {
                    throw fehler("',' oder ')' erwartet");
                }
            }
        }
        double[] ring = new double[n];
        System.arraycopy(buf, 0, ring, 0, n);
        return ring;
    }

    private String wort() {
        leer();
        int a = pos;
        while (pos < s.length() && Character.isLetter(s.charAt(pos))) {
            pos++;
        }
        return s.substring(a, pos).toUpperCase();
    }

    private double zahl() {
        leer();
        int a = pos;
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'E' || c == 'e') {
                pos++;
            } else {
                break;
            }
        }
        if (a == pos) {
            throw fehler("Zahl erwartet");
        }
        return Double.parseDouble(s.substring(a, pos));
    }

    private char naechstes() {
        leer();
        if (pos >= s.length()) {
            throw fehler("unerwartetes Ende");
        }
        return s.charAt(pos);
    }

    private void erwarte(char c) {
        if (naechstes() != c) {
            throw fehler("'" + c + "' erwartet");
        }
        pos++;
    }

    private void leer() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
    }

    private IllegalArgumentException fehler(String was) {
        int a = Math.max(0, pos - 20);
        int b = Math.min(s.length(), pos + 20);
        return new IllegalArgumentException("WKT: " + was + " bei Zeichen " + pos
                + " ..." + s.substring(a, b) + "...");
    }
}
