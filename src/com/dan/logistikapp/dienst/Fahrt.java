package com.dan.logistikapp.dienst;

/**
 * Ergebnis einer Verschiebung, so wie LOG_API sie in LOG_BEWEGUNG
 * festgehalten hat.
 *
 * @author Dan
 */
public final class Fahrt {

    private final long bewegungId;
    private final String vonZone;
    private final String nachZone;
    private final boolean zoll;
    private final long distanzM;
    private final java.math.BigDecimal kostenEur;

    public Fahrt(long bewegungId, String vonZone, String nachZone, boolean zoll, long distanzM) {
        this(bewegungId, vonZone, nachZone, zoll, distanzM, null);
    }

    /** @param kostenEur Fracht + Zoll, wie LOG_API sie festgeschrieben hat; {@code null} unbekannt */
    public Fahrt(long bewegungId, String vonZone, String nachZone, boolean zoll, long distanzM,
            java.math.BigDecimal kostenEur) {
        this.kostenEur = kostenEur;
        this.bewegungId = bewegungId;
        this.vonZone = vonZone;
        this.nachZone = nachZone;
        this.zoll = zoll;
        this.distanzM = distanzM;
    }

    public long getBewegungId() {
        return bewegungId;
    }

    public String getVonZone() {
        return vonZone;
    }

    public String getNachZone() {
        return nachZone;
    }

    /** Die Fahrt hat eine Zollgrenze gekreuzt - der Container steht jetzt beim Zoll. */
    public boolean isZoll() {
        return zoll;
    }

    public long getDistanzM() {
        return distanzM;
    }

    /** Fracht + Zoll dieser Fahrt, {@code null} wenn unbekannt. Standgeld kommt erst mit der Freigabe. */
    public java.math.BigDecimal getKostenEur() {
        return kostenEur;
    }
}
