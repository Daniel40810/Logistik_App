package com.dan.logistikapp.karte;

import com.dan.logistikapp.geo.Laea3035;
import com.dan.logistikapp.model.Land;
import com.dan.logistikapp.model.Regeln;

import java.util.ArrayList;
import java.util.List;

/**
 * Wo eine Route eine Grenze &uuml;berquert, die z&auml;hlt: eine Zonengrenze
 * (Inland/EU/Drittland) oder eine Zollgrenze.
 *
 * <p>Die Route wird abgetastet; &uuml;ber dem Meer z&auml;hlt das zuletzt
 * &uuml;berflogene Land weiter, damit Hamburg &rarr; Oslo nicht an jeder
 * Schere zweimal meldet. Wo das Land wechselt, wird der Punkt durch
 * Halbieren auf wenige hundert Meter genau bestimmt.</p>
 *
 * <p>Die Datenbank entscheidet &uuml;ber Zoll nur nach Start- und Zielland.
 * Hier werden auch Grenzen dazwischen gezeigt (Transit) - als Hinweis,
 * nicht als Regel.</p>
 *
 * @author Dan
 */
public final class Grenzen {

    /** Ein &Uuml;bergang auf der Route. */
    public static final class Uebergang {
        private final double anteil;
        private final double laenge;
        private final double breite;
        private final Land von;
        private final Land nach;
        private final boolean zoll;
        private final boolean zonenwechsel;

        Uebergang(double anteil, double laenge, double breite, Land von, Land nach) {
            this.anteil = anteil;
            this.laenge = laenge;
            this.breite = breite;
            this.von = von;
            this.nach = nach;
            this.zoll = Regeln.zollNoetig(von, nach);
            this.zonenwechsel = von.getZone() != nach.getZone();
        }

        /** Wo auf der Route, 0 = Start, 1 = Ziel. */
        public double getAnteil() {
            return anteil;
        }

        public double getLaenge() {
            return laenge;
        }

        public double getBreite() {
            return breite;
        }

        public Land getVon() {
            return von;
        }

        public Land getNach() {
            return nach;
        }

        /** Zollgrenze: verschiedene L&auml;nder, eines au&szlig;erhalb der Zollunion. */
        public boolean isZoll() {
            return zoll;
        }

        public boolean isZonenwechsel() {
            return zonenwechsel;
        }
    }

    private Grenzen() {
    }

    /**
     * @param route Punkte {L&auml;nge, Breite}, gleichm&auml;&szlig;ig &uuml;ber den Bogen verteilt
     * @return nur &Uuml;berg&auml;nge mit Zonenwechsel oder Zoll, in Fahrtrichtung
     */
    public static List<Uebergang> finde(KartenModell m, double[][] route) {
        List<Uebergang> out = new ArrayList<Uebergang>();
        Land zuletzt = null;
        for (int i = 0; i < route.length; i++) {
            Land l = landBei(m, route[i][0], route[i][1]);
            if (l == null) {
                continue;
            }
            if (zuletzt != null && !l.getIso2().equals(zuletzt.getIso2())) {
                double[] punkt = halbiere(m, route, i - 1, i, l);
                double anteil = (i - 1 + punkt[2]) / (route.length - 1);
                Uebergang u = new Uebergang(anteil, punkt[0], punkt[1], zuletzt, l);
                if (u.zoll || u.zonenwechsel) {
                    out.add(u);
                }
            }
            zuletzt = l;
        }
        return out;
    }

    private static Land landBei(KartenModell m, double lon, double lat) {
        double[] p = Laea3035.vor(lon, lat);
        KartenModell.LandForm f = m.landBei(p[0], p[1]);
        return f == null ? null : f.getLand();
    }

    /** Sucht zwischen Punkt a und b den Eintritt ins Land {@code nach}; liefert {lon, lat, t}. */
    private static double[] halbiere(KartenModell m, double[][] r, int a, int b, Land nach) {
        double lo = 0;
        double hi = 1;
        for (int k = 0; k < 18; k++) {
            double mitte = (lo + hi) / 2;
            double lon = r[a][0] + (r[b][0] - r[a][0]) * mitte;
            double lat = r[a][1] + (r[b][1] - r[a][1]) * mitte;
            Land l = landBei(m, lon, lat);
            if (l != null && l.getIso2().equals(nach.getIso2())) {
                hi = mitte;
            } else {
                lo = mitte;
            }
        }
        return new double[] {r[a][0] + (r[b][0] - r[a][0]) * hi, r[a][1] + (r[b][1] - r[a][1]) * hi, hi};
    }
}
