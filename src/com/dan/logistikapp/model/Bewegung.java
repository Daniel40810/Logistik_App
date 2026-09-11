package com.dan.logistikapp.model;

/**
 * Eine Fahrt aus der Historie - gelesen aus LOG_BEWEGUNG_V.
 *
 * <p>Die Zonen sind der Schnappschuss zum Zeitpunkt der Fahrt, nicht die
 * heutige Zugeh&ouml;rigkeit der L&auml;nder.</p>
 *
 * @author Dan
 */
public final class Bewegung {

    private final long bewegungId;
    private final int containerId;
    private final String kennungAnzeige;
    private final int vonOrtId;
    private final String vonOrt;
    private final int nachOrtId;
    private final String nachOrt;
    private final String vonZone;
    private final String nachZone;
    private final boolean zoll;
    private final long distanzM;
    private final long zeitpunktMs;
    private java.math.BigDecimal frachtEur;
    private java.math.BigDecimal zollEur;
    private java.math.BigDecimal standgeldEur;
    private long freigegebenAmMs;
    private String ausgeloest = "HAND";

    public Bewegung(long bewegungId, int containerId, String kennungAnzeige, int vonOrtId, String vonOrt,
            int nachOrtId, String nachOrt, String vonZone, String nachZone, boolean zoll, long distanzM,
            long zeitpunktMs) {
        this.bewegungId = bewegungId;
        this.containerId = containerId;
        this.kennungAnzeige = kennungAnzeige;
        this.vonOrtId = vonOrtId;
        this.vonOrt = vonOrt;
        this.nachOrtId = nachOrtId;
        this.nachOrt = nachOrt;
        this.vonZone = vonZone;
        this.nachZone = nachZone;
        this.zoll = zoll;
        this.distanzM = distanzM;
        this.zeitpunktMs = zeitpunktMs;
    }

    public long getBewegungId() {
        return bewegungId;
    }

    public int getContainerId() {
        return containerId;
    }

    public String getKennungAnzeige() {
        return kennungAnzeige;
    }

    public int getVonOrtId() {
        return vonOrtId;
    }

    public String getVonOrt() {
        return vonOrt;
    }

    public int getNachOrtId() {
        return nachOrtId;
    }

    public String getNachOrt() {
        return nachOrt;
    }

    public String getVonZone() {
        return vonZone;
    }

    public String getNachZone() {
        return nachZone;
    }

    public boolean isZonenwechsel() {
        return vonZone != null && !vonZone.equals(nachZone);
    }

    public boolean isZoll() {
        return zoll;
    }

    public long getDistanzM() {
        return distanzM;
    }

    public long getZeitpunktMs() {
        return zeitpunktMs;
    }

    /**
     * Kosten und Herkunft (Runde 2), gesetzt beim Lesen aus LOG_BEWEGUNG_V.
     * Betr&auml;ge d&uuml;rfen {@code null} sein (Standgeld, solange er wartet).
     */
    public Bewegung mitKosten(java.math.BigDecimal fracht, java.math.BigDecimal zoll,
            java.math.BigDecimal standgeld, long freigegebenAm, String ausgeloestVon) {
        this.frachtEur = fracht;
        this.zollEur = zoll;
        this.standgeldEur = standgeld;
        this.freigegebenAmMs = freigegebenAm;
        this.ausgeloest = ausgeloestVon == null ? "HAND" : ausgeloestVon;
        return this;
    }

    public java.math.BigDecimal getFrachtEur() {
        return frachtEur;
    }

    public java.math.BigDecimal getZollEur() {
        return zollEur;
    }

    public java.math.BigDecimal getStandgeldEur() {
        return standgeldEur;
    }

    /** Summe der bekannten Betr&auml;ge, 0 wenn keiner bekannt ist. */
    public java.math.BigDecimal getKostenEur() {
        java.math.BigDecimal s = java.math.BigDecimal.ZERO;
        for (java.math.BigDecimal b : new java.math.BigDecimal[] {frachtEur, zollEur, standgeldEur}) {
            if (b != null) {
                s = s.add(b);
            }
        }
        return s;
    }

    /** Zeitpunkt der Zollfreigabe, 0 ohne (keine Zollfahrt oder wartet noch). */
    public long getFreigegebenAmMs() {
        return freigegebenAmMs;
    }

    /** HAND, AUFTRAG oder JOB. */
    public String getAusgeloest() {
        return ausgeloest;
    }

    @Override
    public String toString() {
        return bewegungId + ": " + kennungAnzeige + " " + vonOrt + " -> " + nachOrt;
    }
}
