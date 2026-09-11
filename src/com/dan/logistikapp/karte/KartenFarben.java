package com.dan.logistikapp.karte;

import com.dan.logistikapp.model.Zone;

import java.awt.Color;

/**
 * Farben der Karte: dunkles Meer, L&auml;nder ged&auml;mpft nach Zone, St&auml;dte
 * kr&auml;ftig. Die Container kommen sp&auml;ter mit ihren Warenfarben dazu und
 * m&uuml;ssen sich davon abheben - deshalb ist der Grund zur&uuml;ckhaltend.
 *
 * @author Dan
 */
public final class KartenFarben {

    public static final Color MEER_OBEN = new Color(0x0A1B26);
    public static final Color MEER_UNTEN = new Color(0x0F2A38);
    public static final Color GRADNETZ = new Color(120, 170, 190, 26);
    public static final Color KUESTE = new Color(0x2C, 0x63, 0x75, 150);
    public static final Color GRENZE = new Color(0xA9, 0xC6, 0xCF, 90);
    public static final Color GRENZE_INLAND = new Color(0x7F, 0xD3, 0xD6, 220);
    public static final Color TEXT = new Color(0xE6EEF1);
    public static final Color TEXT_LEISE = new Color(0x8FA7B0);
    public static final Color HALO = new Color(0x0A, 0x1B, 0x26, 230);
    public static final Color TAFEL = new Color(0x0A, 0x1B, 0x26, 205);
    public static final Color TAFEL_RAND = new Color(0x2C, 0x4A, 0x57);

    private KartenFarben() {
    }

    /** Fl&auml;chenfarbe eines Landes. */
    public static Color flaeche(Zone z) {
        switch (z) {
            case INLAND:
                return new Color(0x2A6F73);
            case EU:
                return new Color(0x294A78);
            default:
                return new Color(0x4A3350);
        }
    }

    /** Farbe des Stadtpunkts - kr&auml;ftiger als die Fl&auml;che. */
    public static Color marke(Zone z) {
        switch (z) {
            case INLAND:
                return new Color(0x45B7C1);
            case EU:
                return new Color(0x7FA6F0);
            default:
                return new Color(0xE08AB0);
        }
    }

    /** Aufgehellt f&uuml;r das Land unter der Maus. */
    public static Color hell(Color c) {
        return new Color(Math.min(255, c.getRed() + 28), Math.min(255, c.getGreen() + 28),
                Math.min(255, c.getBlue() + 28));
    }
}
