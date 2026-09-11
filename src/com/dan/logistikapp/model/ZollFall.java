package com.dan.logistikapp.model;

import java.awt.Color;

/**
 * Ein Container, der beim Zoll wartet - gelesen aus LOG_ZOLL_V.
 *
 * <p>Die Wartezeit rechnet die Datenbank beim Lesen; die Anwendung z&auml;hlt
 * von dort mit ihrer eigenen Uhr weiter ({@link #wartetSek(long)}).</p>
 *
 * @author Dan
 */
public final class ZollFall {

    private final int containerId;
    private final String kennungAnzeige;
    private final String ware;
    private final Color farbe;
    private final int ortId;
    private final String ort;
    private final String vonOrt;
    private final String vonLand;
    private final String nachLand;
    private final String vonZone;
    private final String nachZone;
    private final long wartetSekBeimLesen;
    private final long gelesenMs;

    public ZollFall(int containerId, String kennungAnzeige, String ware, Color farbe, int ortId, String ort,
            String vonOrt, String vonLand, String nachLand, String vonZone, String nachZone,
            long wartetSekBeimLesen, long gelesenMs) {
        this.containerId = containerId;
        this.kennungAnzeige = kennungAnzeige;
        this.ware = ware;
        this.farbe = farbe;
        this.ortId = ortId;
        this.ort = ort;
        this.vonOrt = vonOrt;
        this.vonLand = vonLand;
        this.nachLand = nachLand;
        this.vonZone = vonZone;
        this.nachZone = nachZone;
        this.wartetSekBeimLesen = wartetSekBeimLesen;
        this.gelesenMs = gelesenMs;
    }

    public int getContainerId() {
        return containerId;
    }

    public String getKennungAnzeige() {
        return kennungAnzeige;
    }

    public String getWare() {
        return ware;
    }

    public Color getFarbe() {
        return farbe;
    }

    public int getOrtId() {
        return ortId;
    }

    public String getOrt() {
        return ort;
    }

    public String getVonOrt() {
        return vonOrt;
    }

    public String getVonLand() {
        return vonLand;
    }

    public String getNachLand() {
        return nachLand;
    }

    public String getVonZone() {
        return vonZone;
    }

    public String getNachZone() {
        return nachZone;
    }

    /** Wartezeit jetzt: was die Datenbank sagte, plus die Zeit seit dem Lesen. */
    public long wartetSek(long jetztMs) {
        return wartetSekBeimLesen + Math.max(0, (jetztMs - gelesenMs) / 1000);
    }

    /** "DE → NO" */
    public String grenze() {
        return vonLand + " → " + nachLand;
    }
}
