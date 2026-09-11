package com.dan.logistikapp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Die Tarife aus LOG_TARIF und LOG_WARE - und dieselbe Rechnung wie
 * LOG_API, damit die Karte Kosten vorab zeigen kann. Der Durchstich pr&uuml;ft
 * auf den Cent, dass beide gleich rechnen.
 *
 * <p>Gerechnet wird mit {@link BigDecimal}: Oracle rechnet dezimal, ein
 * {@code double} w&uuml;rde bei ,xx5 gelegentlich anders runden.</p>
 *
 * @author Dan
 */
public final class Tarif {

    public static final String ZOLL_PAUSCHALE = "ZOLL_PAUSCHALE";
    public static final String STANDGELD_VIERTELSTUNDE = "STANDGELD_VIERTELSTUNDE";
    public static final String JOB_KARENZ_MIN = "JOB_KARENZ_MIN";

    private static final BigDecimal TAUSEND = BigDecimal.valueOf(1000);

    private final Map<String, BigDecimal> werte;
    private final Map<String, BigDecimal> frachtSatz;

    /**
     * @param werte      LOG_TARIF: Schl&uuml;ssel &rarr; Wert
     * @param frachtSatz LOG_WARE: Warencode &rarr; EUR je TEU-km
     */
    public Tarif(Map<String, BigDecimal> werte, Map<String, BigDecimal> frachtSatz) {
        this.werte = Collections.unmodifiableMap(new HashMap<String, BigDecimal>(werte));
        this.frachtSatz = Collections.unmodifiableMap(new HashMap<String, BigDecimal>(frachtSatz));
    }

    public BigDecimal wert(String schluessel) {
        return werte.get(schluessel);
    }

    public BigDecimal frachtSatz(String wareCode) {
        return frachtSatz.get(wareCode);
    }

    /** km &middot; TEU &middot; Satz, auf Cent (HALF_UP wie Oracle ROUND). {@code null} ohne Satz. */
    public BigDecimal fracht(long distanzM, int teu, String wareCode) {
        BigDecimal satz = frachtSatz.get(wareCode);
        if (satz == null) {
            return null;
        }
        return BigDecimal.valueOf(distanzM).divide(TAUSEND).multiply(BigDecimal.valueOf(teu)).multiply(satz)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Zollpauschale bei Zollfahrt, sonst 0. */
    public BigDecimal zoll(boolean zollfahrt) {
        if (!zollfahrt) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal z = werte.get(ZOLL_PAUSCHALE);
        return z == null ? null : z.setScale(2, RoundingMode.HALF_UP);
    }

    /** Je angefangene Viertelstunde der Standgeld-Satz; 0 bei keiner Wartezeit. */
    public BigDecimal standgeld(long wartezeitMs) {
        BigDecimal satz = werte.get(STANDGELD_VIERTELSTUNDE);
        if (satz == null) {
            return null;
        }
        if (wartezeitMs <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        long viertel = (wartezeitMs + 899999) / 900000;
        return satz.multiply(BigDecimal.valueOf(viertel)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Fracht plus Zoll - was LOG_API.kosten_vorschau liefert. */
    public BigDecimal vorschau(long distanzM, int teu, String wareCode, boolean zollfahrt) {
        BigDecimal f = fracht(distanzM, teu, wareCode);
        BigDecimal z = zoll(zollfahrt);
        return f == null || z == null ? null : f.add(z);
    }

    /** Wie lange ein Auftrag &uuml;berf&auml;llig sein muss, bis der Job ihn f&auml;hrt. */
    public int jobKarenzMin() {
        BigDecimal k = werte.get(JOB_KARENZ_MIN);
        return k == null ? 2 : k.intValue();
    }
}
