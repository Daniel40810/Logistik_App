package com.dan.logistikapp.karte;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Der Ausschnitt: welcher Punkt (Meter, EPSG:3035) liegt in der Bildmitte,
 * und wie viele Bildpunkte hat ein Meter. Die y-Achse wird gedreht, weil
 * der Hochwert nach Norden, der Bildschirm aber nach unten w&auml;chst.
 *
 * @author Dan
 */
public final class KartenAnsicht {

    /** Engster Ausschnitt: 50 m je Bildpunkt. */
    public static final double MAX_MASSSTAB = 1 / 50.0;
    /** Weitester Ausschnitt: 20 km je Bildpunkt. */
    public static final double MIN_MASSSTAB = 1 / 20000.0;

    private double massstab = 1 / 5000.0;
    private double mitteX = 4321000;
    private double mitteY = 3210000;
    private int breite = 1;
    private int hoehe = 1;

    public void setGroesse(int breite, int hoehe) {
        this.breite = Math.max(1, breite);
        this.hoehe = Math.max(1, hoehe);
    }

    public int getBreite() {
        return breite;
    }

    public int getHoehe() {
        return hoehe;
    }

    /** Bildpunkte je Meter. */
    public double getMassstab() {
        return massstab;
    }

    public double getMitteX() {
        return mitteX;
    }

    public double getMitteY() {
        return mitteY;
    }

    /** Stellt einen zuvor gespeicherten Ausschnitt wieder her. */
    public void setAnsicht(double massstab, double mitteX, double mitteY) {
        if (!Double.isNaN(massstab) && !Double.isInfinite(massstab)
                && !Double.isNaN(mitteX) && !Double.isInfinite(mitteX)
                && !Double.isNaN(mitteY) && !Double.isInfinite(mitteY)) {
            this.massstab = begrenze(massstab);
            this.mitteX = mitteX;
            this.mitteY = mitteY;
        }
    }

    public AffineTransform transform() {
        AffineTransform at = new AffineTransform();
        at.translate(breite / 2.0, hoehe / 2.0);
        at.scale(massstab, -massstab);
        at.translate(-mitteX, -mitteY);
        return at;
    }

    public Point2D.Double zuBildschirm(double x, double y) {
        return new Point2D.Double(breite / 2.0 + (x - mitteX) * massstab,
                hoehe / 2.0 - (y - mitteY) * massstab);
    }

    public Point2D.Double zuWelt(double px, double py) {
        return new Point2D.Double(mitteX + (px - breite / 2.0) / massstab,
                mitteY - (py - hoehe / 2.0) / massstab);
    }

    /** Sichtbarer Ausschnitt in Metern. */
    public Rectangle2D weltRahmen() {
        double w = breite / massstab;
        double h = hoehe / massstab;
        return new Rectangle2D.Double(mitteX - w / 2, mitteY - h / 2, w, h);
    }

    /** Zoomt so, dass der Weltpunkt unter (px, py) liegen bleibt. */
    public void zoomUm(double px, double py, double faktor) {
        Point2D.Double w = zuWelt(px, py);
        massstab = begrenze(massstab * faktor);
        mitteX = w.x - (px - breite / 2.0) / massstab;
        mitteY = w.y + (py - hoehe / 2.0) / massstab;
    }

    public void verschieben(double dxPx, double dyPx) {
        mitteX -= dxPx / massstab;
        mitteY += dyPx / massstab;
    }

    /** Passt einen Weltausschnitt mit Rand (Anteil je Seite) ins Bild. */
    public void einpassen(Rectangle2D welt, double rand) {
        double w = Math.max(welt.getWidth(), 1) * (1 + 2 * rand);
        double h = Math.max(welt.getHeight(), 1) * (1 + 2 * rand);
        massstab = begrenze(Math.min(breite / w, hoehe / h));
        mitteX = welt.getCenterX();
        mitteY = welt.getCenterY();
    }

    private static double begrenze(double m) {
        return Math.max(MIN_MASSSTAB, Math.min(MAX_MASSSTAB, m));
    }
}
