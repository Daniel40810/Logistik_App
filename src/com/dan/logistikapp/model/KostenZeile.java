package com.dan.logistikapp.model;

import java.math.BigDecimal;

/**
 * Eine Zeile aus LOG_KOSTEN_V: Kosten je Tag, Zonenpaar und Ware.
 *
 * @author Dan
 */
public final class KostenZeile {

    private final long tagMs;
    private final String vonZone;
    private final String nachZone;
    private final String wareCode;
    private final String ware;
    private final int fahrten;
    private final int zollfahrten;
    private final double km;
    private final BigDecimal frachtEur;
    private final BigDecimal zollEur;
    private final BigDecimal standgeldEur;

    public KostenZeile(long tagMs, String vonZone, String nachZone, String wareCode, String ware, int fahrten,
            int zollfahrten, double km, BigDecimal frachtEur, BigDecimal zollEur, BigDecimal standgeldEur) {
        this.tagMs = tagMs;
        this.vonZone = vonZone;
        this.nachZone = nachZone;
        this.wareCode = wareCode;
        this.ware = ware;
        this.fahrten = fahrten;
        this.zollfahrten = zollfahrten;
        this.km = km;
        this.frachtEur = nn(frachtEur);
        this.zollEur = nn(zollEur);
        this.standgeldEur = nn(standgeldEur);
    }

    private static BigDecimal nn(BigDecimal b) {
        return b == null ? BigDecimal.ZERO : b;
    }

    /** Mitternacht des Tages (Ortszeit der Datenbank). */
    public long getTagMs() {
        return tagMs;
    }

    public String getVonZone() {
        return vonZone;
    }

    public String getNachZone() {
        return nachZone;
    }

    public String getWareCode() {
        return wareCode;
    }

    public String getWare() {
        return ware;
    }

    public int getFahrten() {
        return fahrten;
    }

    public int getZollfahrten() {
        return zollfahrten;
    }

    public double getKm() {
        return km;
    }

    public BigDecimal getFrachtEur() {
        return frachtEur;
    }

    public BigDecimal getZollEur() {
        return zollEur;
    }

    public BigDecimal getStandgeldEur() {
        return standgeldEur;
    }

    public BigDecimal getKostenEur() {
        return frachtEur.add(zollEur).add(standgeldEur);
    }

    /**
     * Fasst Fahrten zu Zeilen zusammen - dieselbe Gruppierung wie
     * LOG_KOSTEN_V. F&uuml;r den Dienst im Speicher (Demo, Tests).
     */
    public static java.util.List<KostenZeile> aus(java.util.List<Bewegung> fahrten,
            java.util.Map<Integer, String[]> wareJeContainer) {
        java.util.Map<String, Object[]> summe = new java.util.LinkedHashMap<String, Object[]>();
        for (Bewegung b : fahrten) {
            java.util.Calendar k = java.util.Calendar.getInstance();
            k.setTimeInMillis(b.getZeitpunktMs());
            k.set(java.util.Calendar.HOUR_OF_DAY, 0);
            k.set(java.util.Calendar.MINUTE, 0);
            k.set(java.util.Calendar.SECOND, 0);
            k.set(java.util.Calendar.MILLISECOND, 0);
            String[] w = wareJeContainer.get(b.getContainerId());
            String code = w == null ? "?" : w[0];
            String key = k.getTimeInMillis() + "|" + b.getVonZone() + "|" + b.getNachZone() + "|" + code;
            Object[] s = summe.get(key);
            if (s == null) {
                s = new Object[] {k.getTimeInMillis(), b.getVonZone(), b.getNachZone(), code,
                    w == null ? "?" : w[1], 0, 0, 0.0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
                summe.put(key, s);
            }
            s[5] = (Integer) s[5] + 1;
            s[6] = (Integer) s[6] + (b.isZoll() ? 1 : 0);
            s[7] = (Double) s[7] + b.getDistanzM() / 1000.0;
            s[8] = ((BigDecimal) s[8]).add(nn(b.getFrachtEur()));
            s[9] = ((BigDecimal) s[9]).add(nn(b.getZollEur()));
            s[10] = ((BigDecimal) s[10]).add(nn(b.getStandgeldEur()));
        }
        java.util.List<KostenZeile> out = new java.util.ArrayList<KostenZeile>();
        for (Object[] s : summe.values()) {
            out.add(new KostenZeile((Long) s[0], (String) s[1], (String) s[2], (String) s[3], (String) s[4],
                    (Integer) s[5], (Integer) s[6], (Double) s[7], (BigDecimal) s[8], (BigDecimal) s[9],
                    (BigDecimal) s[10]));
        }
        return out;
    }
}
