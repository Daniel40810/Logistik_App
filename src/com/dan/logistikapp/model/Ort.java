package com.dan.logistikapp.model;

/**
 * Eine Stadt auf der Karte, gelesen aus LOG_ORT_V.
 *
 * @author Dan
 */
public final class Ort {

    private final int ortId;
    private final String name;
    private final String iso2;
    private final String land;
    private final Zone zone;
    private final double laenge;
    private final double breite;
    private final Integer kapazitaetTeu;

    public Ort(int ortId, String name, String iso2, String land, Zone zone,
            double laenge, double breite) {
        this(ortId, name, iso2, land, zone, laenge, breite, null);
    }

    /** @param kapazitaetTeu Stellplatz in TEU, {@code null} = unbegrenzt */
    public Ort(int ortId, String name, String iso2, String land, Zone zone,
            double laenge, double breite, Integer kapazitaetTeu) {
        this.kapazitaetTeu = kapazitaetTeu;
        this.ortId = ortId;
        this.name = name;
        this.iso2 = iso2;
        this.land = land;
        this.zone = zone;
        this.laenge = laenge;
        this.breite = breite;
    }

    /** Stellplatz in TEU, {@code null} = unbegrenzt (Runde 2). */
    public Integer getKapazitaetTeu() {
        return kapazitaetTeu;
    }

    public int getOrtId() {
        return ortId;
    }

    public String getName() {
        return name;
    }

    public String getIso2() {
        return iso2;
    }

    public String getLand() {
        return land;
    }

    public Zone getZone() {
        return zone;
    }

    /** Geographische L&auml;nge in Grad, Ost positiv. */
    public double getLaenge() {
        return laenge;
    }

    /** Geographische Breite in Grad, Nord positiv. */
    public double getBreite() {
        return breite;
    }

    @Override
    public String toString() {
        return name + " (" + iso2 + ", " + zone + ")";
    }
}
