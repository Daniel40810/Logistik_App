package com.dan.logistikapp.geo;

/**
 * Entfernungen und Routen auf der Erde.
 *
 * <p>{@link #entfernungM} rechnet nach Vincenty auf dem WGS84-Ellipsoid - so
 * wie Oracle mit SDO_GEOM.SDO_DISTANCE bei SRID 8307. Die Karte zeigt damit
 * beim Ziehen dieselbe Zahl, die sp&auml;ter in LOG_BEWEGUNG steht.</p>
 *
 * <p>{@link #grosskreis} liefert Zwischenpunkte f&uuml;r die Linie auf der Karte.
 * Daf&uuml;r reicht die Kugel: der Unterschied zur Ellipsoid-Geod&auml;te bleibt
 * unter einem Bildpunkt.</p>
 *
 * @author Dan
 */
public final class Geodaesie {

    private static final double A = 6378137.0;
    private static final double F = 1 / 298.257223563;
    private static final double B = A * (1 - F);

    private Geodaesie() {
    }

    /**
     * K&uuml;rzeste Entfernung zwischen zwei Punkten auf dem WGS84-Ellipsoid in
     * Metern (Vincenty, inverse Aufgabe). Bei fast gegen&uuml;berliegenden
     * Punkten, wo Vincenty nicht konvergiert, f&auml;llt sie auf die Kugel zur&uuml;ck -
     * in Europa kommt das nicht vor.
     */
    public static double entfernungM(double lon1, double lat1, double lon2, double lat2) {
        double l = Math.toRadians(lon2 - lon1);
        double u1 = Math.atan((1 - F) * Math.tan(Math.toRadians(lat1)));
        double u2 = Math.atan((1 - F) * Math.tan(Math.toRadians(lat2)));
        double sinU1 = Math.sin(u1);
        double cosU1 = Math.cos(u1);
        double sinU2 = Math.sin(u2);
        double cosU2 = Math.cos(u2);
        double lambda = l;
        double sinSigma = 0;
        double cosSigma = 0;
        double sigma = 0;
        double cos2Alpha = 0;
        double cos2SigmaM = 0;
        for (int i = 0; i < 200; i++) {
            double sinL = Math.sin(lambda);
            double cosL = Math.cos(lambda);
            double t1 = cosU2 * sinL;
            double t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosL;
            sinSigma = Math.sqrt(t1 * t1 + t2 * t2);
            if (sinSigma == 0) {
                return 0;
            }
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosL;
            sigma = Math.atan2(sinSigma, cosSigma);
            double sinAlpha = cosU1 * cosU2 * sinL / sinSigma;
            cos2Alpha = 1 - sinAlpha * sinAlpha;
            cos2SigmaM = (cos2Alpha != 0) ? cosSigma - 2 * sinU1 * sinU2 / cos2Alpha : 0;
            double c = F / 16 * cos2Alpha * (4 + F * (4 - 3 * cos2Alpha));
            double vorher = lambda;
            lambda = l + (1 - c) * F * sinAlpha
                    * (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)));
            if (Math.abs(lambda - vorher) < 1e-12) {
                double uSq = cos2Alpha * (A * A - B * B) / (B * B);
                double aa = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)));
                double bb = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)));
                double dSigma = bb * sinSigma * (cos2SigmaM + bb / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)
                        - bb / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)));
                return B * aa * (sigma - dSigma);
            }
        }
        return kugelM(lon1, lat1, lon2, lat2);
    }

    /** Entfernung auf der Kugel mit mittlerem Erdradius (Haversine). */
    public static double kugelM(double lon1, double lat1, double lon2, double lat2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * 6371008.8 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /**
     * Punkte auf dem Gro&szlig;kreis von (lon1,lat1) nach (lon2,lat2), Anfang und
     * Ende eingeschlossen.
     *
     * @return n+1 Paare {L&auml;nge, Breite}
     */
    public static double[][] grosskreis(double lon1, double lat1, double lon2, double lat2, int n) {
        double[] a = einheit(lon1, lat1);
        double[] b = einheit(lon2, lat2);
        double dot = Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
        double w = Math.acos(dot);
        double[][] out = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            double[] p;
            if (w < 1e-12) {
                p = a;
            } else {
                double s = Math.sin(w);
                double fa = Math.sin((1 - t) * w) / s;
                double fb = Math.sin(t * w) / s;
                p = new double[] {fa * a[0] + fb * b[0], fa * a[1] + fb * b[1], fa * a[2] + fb * b[2]};
            }
            out[i] = new double[] {Math.toDegrees(Math.atan2(p[1], p[0])),
                Math.toDegrees(Math.atan2(p[2], Math.sqrt(p[0] * p[0] + p[1] * p[1])))};
        }
        return out;
    }

    private static double[] einheit(double lon, double lat) {
        double la = Math.toRadians(lat);
        double lo = Math.toRadians(lon);
        return new double[] {Math.cos(la) * Math.cos(lo), Math.cos(la) * Math.sin(lo), Math.sin(la)};
    }
}
