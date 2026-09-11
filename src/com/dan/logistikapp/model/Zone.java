package com.dan.logistikapp.model;

/**
 * Zone einer Stadt oder eines Landes. Abgeleitet in der Datenbank
 * (LOG_LAND.ZONE_TYP), hier nur gelesen.
 *
 * @author Dan
 */
public enum Zone {

    INLAND("Inland"),
    EU("EU-Ausland"),
    DRITTLAND("Nicht-EU");

    private final String anzeige;

    Zone(String anzeige) {
        this.anzeige = anzeige;
    }

    /** So, wie es in der Oberfl&auml;che steht. */
    public String anzeige() {
        return anzeige;
    }

    /** Aus dem Datenbankwert; unbekannte Werte sind ein Fehler, kein Stillschweigen. */
    public static Zone vonCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Zone fehlt");
        }
        return Zone.valueOf(code.trim());
    }
}
