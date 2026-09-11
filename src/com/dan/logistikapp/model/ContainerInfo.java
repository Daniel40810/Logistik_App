package com.dan.logistikapp.model;

import java.awt.Color;

/**
 * Ein Container, wie ihn die Karte zeigt - gelesen aus LOG_CONTAINER_V.
 *
 * @author Dan
 */
public final class ContainerInfo {

    private final int containerId;
    private final String kennung;
    private final int groesseFuss;
    private final String typCode;
    private final String wareCode;
    private final String ware;
    private final Color farbe;
    private final int ortId;
    private final String ort;
    private final String status;

    public ContainerInfo(int containerId, String kennung, int groesseFuss, String typCode,
            String wareCode, String ware, Color farbe, int ortId, String ort, String status) {
        this.containerId = containerId;
        this.kennung = kennung;
        this.groesseFuss = groesseFuss;
        this.typCode = typCode;
        this.wareCode = wareCode;
        this.ware = ware;
        this.farbe = farbe;
        this.ortId = ortId;
        this.ort = ort;
        this.status = status;
    }

    public int getContainerId() {
        return containerId;
    }

    /** Kompakt, z.B. DANU1000015. Lesbar &uuml;ber {@link Iso6346#anzeige(String)}. */
    public String getKennung() {
        return kennung;
    }

    public int getGroesseFuss() {
        return groesseFuss;
    }

    /** ISO-Typcode, z.B. 22G1 oder 22R1. */
    public String getTypCode() {
        return typCode;
    }

    /** Das R an dritter Stelle des Typcodes: K&uuml;hlcontainer. */
    public boolean isReefer() {
        return typCode != null && typCode.length() >= 3 && typCode.charAt(2) == 'R';
    }

    /** 20 Fu&szlig; = 1 TEU, 40 Fu&szlig; = 2 TEU. */
    public int getTeu() {
        return groesseFuss == 40 ? 2 : 1;
    }

    public String getWareCode() {
        return wareCode;
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

    /** BEREIT oder ZOLL. */
    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return Iso6346.anzeige(kennung) + " " + typCode + " " + ware + " @" + ort;
    }
}
