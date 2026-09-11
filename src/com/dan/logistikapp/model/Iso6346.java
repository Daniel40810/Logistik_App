package com.dan.logistikapp.model;

/**
 * Container-Kennung nach ISO 6346.
 *
 * <p>Aufbau: drei Buchstaben Eigent&uuml;mer, ein Buchstabe Kategorie
 * ({@code U} Frachtcontainer, {@code J} abnehmbare Ausr&uuml;stung,
 * {@code Z} Chassis), sechs Ziffern Seriennummer, eine Pr&uuml;fziffer.
 * Beispiel: {@code DANU 100001 5}.</p>
 *
 * <p>Die Datenbank rechnet dieselbe Pr&uuml;fziffer in
 * {@code LOG_API.pruefziffer}. Beide Seiten sind absichtlich
 * <em>verschieden</em> gebaut &ndash; hier &uuml;ber eine Werte-Tabelle,
 * dort &uuml;ber eine Formel &ndash;, damit der Abgleich im Pr&uuml;fprogramm
 * nicht denselben Fehler zweimal best&auml;tigt.</p>
 *
 * <p>Java 8, keine Abh&auml;ngigkeiten.</p>
 *
 * @author Dan
 */
public final class Iso6346 {

    /** Eigent&uuml;mer DAN, Kategorie U. */
    public static final String PRAEFIX = "DANU";

    /** Buchstabenwerte A..Z: ab 10 aufsteigend, Vielfache von 11 ausgelassen. */
    private static final int[] BUCHSTABE = new int[26];

    static {
        int wert = 10;
        for (int i = 0; i < 26; i++) {
            if (wert % 11 == 0) {
                wert++;
            }
            BUCHSTABE[i] = wert++;
        }
    }

    private Iso6346() {
    }

    /**
     * Zahlenwert eines Zeichens, oder -1 wenn es in einer Kennung nichts
     * zu suchen hat.
     */
    public static int zeichenwert(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return BUCHSTABE[c - 'A'];
        }
        return -1;
    }

    /**
     * Pr&uuml;fziffer zu den ersten zehn Zeichen, oder -1 wenn die Basis
     * nicht aus genau zehn g&uuml;ltigen Zeichen besteht.
     */
    public static int pruefziffer(String basis) {
        if (basis == null || basis.length() != 10) {
            return -1;
        }
        long summe = 0;
        for (int i = 0; i < 10; i++) {
            int w = zeichenwert(basis.charAt(i));
            if (w < 0) {
                return -1;
            }
            summe += (long) w << i;
        }
        return (int) (summe % 11 % 10);
    }

    /** Kompakte Form: ohne Leerzeichen, in Grossbuchstaben. */
    public static String kompakt(String kennung) {
        return kennung == null ? null : kennung.replace(" ", "").toUpperCase();
    }

    /** Format und Pr&uuml;fziffer stimmen. Leerzeichen sind erlaubt. */
    public static boolean gueltig(String kennung) {
        String k = kompakt(kennung);
        if (k == null || !k.matches("[A-Z]{3}[UJZ][0-9]{7}")) {
            return false;
        }
        return pruefziffer(k.substring(0, 10)) == k.charAt(10) - '0';
    }

    /**
     * Neue Kennung aus Pr&auml;fix (vier Buchstaben) und Seriennummer.
     *
     * @throws IllegalArgumentException bei ung&uuml;ltigem Pr&auml;fix oder
     *         einer Serie ausserhalb 0..999999
     */
    public static String neueKennung(String praefix, int serie) {
        if (praefix == null || !praefix.matches("[A-Z]{3}[UJZ]")) {
            throw new IllegalArgumentException("Praefix ungueltig: " + praefix);
        }
        if (serie < 0 || serie > 999999) {
            throw new IllegalArgumentException("Serie ausserhalb 0..999999: " + serie);
        }
        String basis = praefix + String.format("%06d", serie);
        return basis + pruefziffer(basis);
    }

    /** Wie auf der Containert&uuml;r: {@code DANU 100001 5}. */
    public static String anzeige(String kennung) {
        String k = kompakt(kennung);
        if (k == null || k.length() != 11) {
            return kennung;
        }
        return k.substring(0, 4) + ' ' + k.substring(4, 10) + ' ' + k.charAt(10);
    }
}
