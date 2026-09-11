package com.dan.logistikapp.model;

import java.awt.Color;

/**
 * Eine Warenart mit ihrer Containerfarbe, gelesen aus LOG_WARE.
 *
 * @author Dan
 */
public final class Ware {

    private final String code;
    private final String name;
    private final Color farbe;
    private final boolean kuehlpflichtig;
    private final int sortierung;

    public Ware(String code, String name, Color farbe, boolean kuehlpflichtig, int sortierung) {
        this.code = code;
        this.name = name;
        this.farbe = farbe;
        this.kuehlpflichtig = kuehlpflichtig;
        this.sortierung = sortierung;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Color getFarbe() {
        return farbe;
    }

    public boolean isKuehlpflichtig() {
        return kuehlpflichtig;
    }

    public int getSortierung() {
        return sortierung;
    }

    /** Lesbare Darstellung f&uuml;r Auswahlfelder und Listen. */
    @Override
    public String toString() {
        return name + " (" + code + ")";
    }

    /** '#RRGGBB' &rarr; Farbe. */
    public static Color farbe(String hex) {
        return new Color(Integer.parseInt(hex.substring(1), 16));
    }
}
