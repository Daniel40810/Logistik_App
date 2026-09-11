package com.dan.logistikapp.model;

/** Eingaben zum Anlegen einer neuen Stadt. */
public final class OrtAnlage {

    private final String name;
    private final String iso2;
    private final double laenge;
    private final double breite;
    private final Integer kapazitaetTeu;

    public OrtAnlage(String name, String iso2, double laenge, double breite, Integer kapazitaetTeu) {
        this.name = name;
        this.iso2 = iso2;
        this.laenge = laenge;
        this.breite = breite;
        this.kapazitaetTeu = kapazitaetTeu;
    }

    public String getName() {
        return name;
    }

    public String getIso2() {
        return iso2;
    }

    public double getLaenge() {
        return laenge;
    }

    public double getBreite() {
        return breite;
    }

    public Integer getKapazitaetTeu() {
        return kapazitaetTeu;
    }
}
