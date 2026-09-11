package com.dan.logistikapp.mesh;

import java.awt.image.BufferedImage;

/**
 * Ein fertig gerendertes Containerbild samt Ankerpunkt: der Bildpunkt, auf
 * dem die Mitte der Grundfl&auml;che liegt. Diesen Punkt setzt die Karte auf
 * den Stellplatz.
 *
 * @author Dan
 */
public final class ContainerSprite {

    private final BufferedImage bild;
    private final double ankerX;
    private final double ankerY;
    private final double pixelProMeter;

    ContainerSprite(BufferedImage bild, double ankerX, double ankerY, double pixelProMeter) {
        this.bild = bild;
        this.ankerX = ankerX;
        this.ankerY = ankerY;
        this.pixelProMeter = pixelProMeter;
    }

    public BufferedImage getBild() {
        return bild;
    }

    public double getAnkerX() {
        return ankerX;
    }

    public double getAnkerY() {
        return ankerY;
    }

    /** Aufl&ouml;sung, in der das Bild gerendert wurde. */
    public double getPixelProMeter() {
        return pixelProMeter;
    }
}
