package com.dan.logistikapp.model;

import com.dan.logistikapp.geo.GeoFlaeche;

/**
 * Ein Land mit seinen Zugeh&ouml;rigkeiten und seiner Fl&auml;che (WGS84).
 *
 * @author Dan
 */
public final class Land {

    private final String iso2;
    private final String name;
    private final Zone zone;
    private final boolean euMitglied;
    private final boolean zollunion;
    private final boolean schengen;
    private final GeoFlaeche grenze;

    public Land(String iso2, String name, Zone zone, boolean euMitglied,
            boolean zollunion, boolean schengen, GeoFlaeche grenze) {
        this.iso2 = iso2;
        this.name = name;
        this.zone = zone;
        this.euMitglied = euMitglied;
        this.zollunion = zollunion;
        this.schengen = schengen;
        this.grenze = grenze;
    }

    public String getIso2() {
        return iso2;
    }

    public String getName() {
        return name;
    }

    public Zone getZone() {
        return zone;
    }

    public boolean isEuMitglied() {
        return euMitglied;
    }

    public boolean isZollunion() {
        return zollunion;
    }

    public boolean isSchengen() {
        return schengen;
    }

    /** Kann {@code null} sein, wenn f&uuml;r das Land keine Fl&auml;che geladen ist. */
    public GeoFlaeche getGrenze() {
        return grenze;
    }

    @Override
    public String toString() {
        return iso2 + " " + name + " (" + zone + ")";
    }
}
