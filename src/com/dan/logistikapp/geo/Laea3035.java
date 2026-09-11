package com.dan.logistikapp.geo;

/**
 * ETRS89 / LAEA Europe (EPSG:3035): fl&auml;chentreue azimutale
 * Lambert-Projektion auf dem GRS80-Ellipsoid, Mittelpunkt 52&deg; N / 10&deg; O.
 *
 * <p>Formeln nach EPSG Guidance Note 7-2 (Methode 9820). Gepr&uuml;ft am
 * Beispiel der Note (50&deg; N, 5&deg; O &rarr; E 3 962 799,45 / N 2 999 718,85)
 * und im Durchstich gegen Oracles SDO_CS.TRANSFORM.</p>
 *
 * <p>Die Klasse ist zustandslos und threadsicher.</p>
 *
 * @author Dan
 */
public final class Laea3035 {

    public static final int EPSG = 3035;

    private static final double A = 6378137.0;
    private static final double F = 1 / 298.257222101;
    private static final double E2 = 2 * F - F * F;
    private static final double E = Math.sqrt(E2);

    private static final double PHI0 = Math.toRadians(52);
    private static final double LAM0 = Math.toRadians(10);
    private static final double FE = 4321000.0;
    private static final double FN = 3210000.0;

    private static final double QP = q(Math.PI / 2);
    private static final double RQ = A * Math.sqrt(QP / 2);
    private static final double BETA0 = Math.asin(q(PHI0) / QP);
    private static final double SIN_B0 = Math.sin(BETA0);
    private static final double COS_B0 = Math.cos(BETA0);
    private static final double D = A * (Math.cos(PHI0) / Math.sqrt(1 - E2 * Math.sin(PHI0) * Math.sin(PHI0)))
            / (RQ * COS_B0);

    // Reihenkoeffizienten fuer die Rueckrechnung der Breite
    private static final double E4 = E2 * E2;
    private static final double E6 = E4 * E2;
    private static final double K2 = E2 / 3 + 31 * E4 / 180 + 517 * E6 / 5040;
    private static final double K4 = 23 * E4 / 360 + 251 * E6 / 3780;
    private static final double K6 = 761 * E6 / 45360;

    private Laea3035() {
    }

    private static double q(double phi) {
        double s = Math.sin(phi);
        return (1 - E2) * (s / (1 - E2 * s * s) - (1 / (2 * E)) * Math.log((1 - E * s) / (1 + E * s)));
    }

    /**
     * L&auml;nge/Breite in Grad &rarr; Rechtswert/Hochwert in Metern.
     *
     * @param out Feld der L&auml;nge 2 f&uuml;r das Ergebnis {E, N}; wird zur&uuml;ckgegeben
     */
    public static double[] vor(double laenge, double breite, double[] out) {
        double phi = Math.toRadians(breite);
        double dl = Math.toRadians(laenge) - LAM0;
        double beta = Math.asin(q(phi) / QP);
        double sb = Math.sin(beta);
        double cb = Math.cos(beta);
        double cdl = Math.cos(dl);
        double b = RQ * Math.sqrt(2 / (1 + SIN_B0 * sb + COS_B0 * cb * cdl));
        out[0] = FE + b * D * cb * Math.sin(dl);
        out[1] = FN + (b / D) * (COS_B0 * sb - SIN_B0 * cb * cdl);
        return out;
    }

    public static double[] vor(double laenge, double breite) {
        return vor(laenge, breite, new double[2]);
    }

    /**
     * Rechtswert/Hochwert in Metern &rarr; L&auml;nge/Breite in Grad.
     *
     * @return {L&auml;nge, Breite}
     */
    public static double[] zurueck(double ost, double nord) {
        double x = ost - FE;
        double y = nord - FN;
        double rho = Math.sqrt((x / D) * (x / D) + (D * y) * (D * y));
        if (rho < 1e-9) {
            return new double[] {Math.toDegrees(LAM0), Math.toDegrees(PHI0)};
        }
        double c = 2 * Math.asin(rho / (2 * RQ));
        double sc = Math.sin(c);
        double cc = Math.cos(c);
        double beta = Math.asin(cc * SIN_B0 + (D * y * sc * COS_B0) / rho);
        double lam = LAM0 + Math.atan2(x * sc, D * rho * COS_B0 * cc - D * D * y * SIN_B0 * sc);
        double phi = beta + K2 * Math.sin(2 * beta) + K4 * Math.sin(4 * beta) + K6 * Math.sin(6 * beta);
        return new double[] {Math.toDegrees(lam), Math.toDegrees(phi)};
    }
}
