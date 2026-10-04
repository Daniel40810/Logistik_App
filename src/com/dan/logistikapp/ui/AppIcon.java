package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.KartenFarben;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Das Programmsymbol, gemalt statt geladen: der dunkle Kartengrund mit
 * Inland, EU-Ausland und Nicht-EU in den Zonenfarben, dar&uuml;ber eine
 * gestrichelte Fahrt von Stadt zu Stadt und vorn ein Container in
 * Warenfarbe. Rundum die Goldkante wie bei den anderen Projekten.
 *
 * <p>Alle &uuml;blichen Gr&ouml;&szlig;en f&uuml;r Taskleiste, Titel und Alt+Tab;
 * unter 32 Pixel bleiben nur die kr&auml;ftigen Fl&auml;chen stehen.</p>
 *
 * @author Dan
 */
public final class AppIcon {

    public static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};

    private static final Color GOLD = new Color(0xECC064);
    private static final Color WARE = new Color(0xE2A33C);
    private static final Color WARE_HELL = new Color(0xF2C169);

    private AppIcon() {
    }

    public static List<Image> images() {
        List<Image> l = new ArrayList<Image>();
        for (int s : SIZES) {
            l.add(paint(s));
        }
        return l;
    }

    /** Setzt das Symbol am Fenster und, wo unterst&uuml;tzt, in der Taskleiste. */
    public static void install(java.awt.Window w) {
        List<Image> l = images();
        w.setIconImages(l);
        try {
            Class<?> tb = Class.forName("java.awt.Taskbar");
            Object t = tb.getMethod("getTaskbar").invoke(null);
            tb.getMethod("setIconImage", Image.class).invoke(t, l.get(l.size() - 1));
        } catch (Exception ignored) {
            // Java 8 kennt die Taskbar noch nicht - Windows nimmt die Fenstersymbole
        } catch (Error ignored) {
            // dasselbe, wenn die Klasse da ist, die Plattform sie aber nicht kann
        }
    }

    /** Malt das Symbol in der Kantenl&auml;nge s (quadratisch, mit Alpha). */
    public static BufferedImage paint(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(s / 256.0, s / 256.0);
        boolean small = s <= 32;      // grobe Fassung: nur Flaechen, keine Feinheiten
        boolean tiny = s <= 20;
        RoundRectangle2D tile = new RoundRectangle2D.Double(6, 6, 244, 244, 58, 58);

        // Meer wie auf der Karte, oben dunkel
        g.setPaint(new GradientPaint(0, 6, KartenFarben.MEER_OBEN, 0, 250, KartenFarben.MEER_UNTEN));
        g.fill(tile);
        g.setClip(tile);

        if (!small) {
            // Gradnetz
            g.setColor(new Color(120, 170, 190, 30));
            g.setStroke(new BasicStroke(1.4f));
            for (int i = 1; i < 5; i++) {
                g.draw(new Line2D.Double(6, 6 + i * 48, 250, 6 + i * 48));
                g.draw(new Line2D.Double(6 + i * 48, 6, 6 + i * 48, 250));
            }
        }

        // Nicht-EU oben links, EU-Ausland rechts, Inland in der Mitte - wie die drei Zonen der Karte
        g.setColor(new Color(0x4A3350));
        Path2D drittland = new Path2D.Double();
        drittland.moveTo(6, 96);
        drittland.curveTo(34, 60, 60, 44, 96, 40);
        drittland.lineTo(96, 6);
        drittland.lineTo(6, 6);
        drittland.closePath();
        g.fill(drittland);

        g.setColor(new Color(0x294A78));
        Path2D eu = new Path2D.Double();
        eu.moveTo(96, 250);
        eu.curveTo(132, 214, 150, 170, 162, 120);
        eu.curveTo(176, 66, 206, 30, 250, 20);
        eu.lineTo(250, 250);
        eu.closePath();
        g.fill(eu);

        g.setColor(new Color(0x2A6F73));
        Path2D inland = new Path2D.Double();
        inland.moveTo(46, 196);
        inland.curveTo(54, 148, 74, 112, 110, 92);
        inland.curveTo(146, 72, 168, 96, 160, 140);
        inland.curveTo(152, 184, 120, 216, 80, 220);
        inland.curveTo(58, 222, 44, 214, 46, 196);
        inland.closePath();
        g.fill(inland);

        if (!small) {
            // Kuestensaum
            g.setColor(new Color(0x2C, 0x63, 0x75, 170));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(inland);
            g.draw(eu);
        }

        // Fahrt von der Inlandstadt zur Stadt im Drittland: Grosskreis, gestrichelt
        double ax = 104;
        double ay = 150;
        double bx = 196;
        double by = 72;
        if (!tiny) {
            g.setStroke(new BasicStroke(small ? 6f : 4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                    small ? new float[] {9f, 9f} : new float[] {12f, 10f}, 0f));
            g.setColor(new Color(0x7F, 0xD3, 0xD6, 235));
            g.draw(new QuadCurve2D.Double(ax, ay, 150, 86, bx, by));
        }

        // Staedte: Inland als Kreis, Drittland als Raute (wie die Marken auf der Karte)
        marke(g, ax, ay, new Color(0x45B7C1), false, small);
        marke(g, bx, by, new Color(0xE08AB0), true, small);

        // Container vorn: Weizen-Gelb, leicht von oben gesehen
        double cx = 150;
        double cy = 206;
        double w = 132;
        double h = 54;
        double t = 20;                 // Tiefe der Deckflaeche
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new Ellipse2D.Double(cx - w / 2 - 6, cy + h / 2 - 10, w + 12, 26));
        Path2D deckel = new Path2D.Double();
        deckel.moveTo(cx - w / 2, cy - h / 2);
        deckel.lineTo(cx - w / 2 + t, cy - h / 2 - t);
        deckel.lineTo(cx + w / 2 + t, cy - h / 2 - t);
        deckel.lineTo(cx + w / 2, cy - h / 2);
        deckel.closePath();
        g.setColor(WARE_HELL);
        g.fill(deckel);
        g.setPaint(new GradientPaint((float) (cx - w / 2), 0, WARE_HELL, (float) (cx + w / 2), 0, WARE));
        g.fill(new Rectangle2D.Double(cx - w / 2, cy - h / 2, w, h));
        if (!small) {
            // Sicken und Tueren hinten rechts
            g.setColor(new Color(0x8A, 0x5A, 0x16, 150));
            g.setStroke(new BasicStroke(3f));
            for (int i = 1; i < 7; i++) {
                double x = cx - w / 2 + i * (w / 7.0);
                g.draw(new Line2D.Double(x, cy - h / 2 + 6, x, cy + h / 2 - 6));
            }
            g.setColor(new Color(0x7A, 0x4E, 0x12, 190));
            g.setStroke(new BasicStroke(3.4f));
            g.draw(new Rectangle2D.Double(cx - w / 2, cy - h / 2, w, h));
        }
        g.setColor(new Color(0x5E, 0x3C, 0x0C, 160));
        g.fill(new Rectangle2D.Double(cx - w / 2, cy + h / 2 - 7, w, 7));

        g.setClip(null);
        // Goldkante wie bei den anderen Projekten
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(small ? 7f : 5f));
        g.draw(new RoundRectangle2D.Double(8.5, 8.5, 239, 239, 55, 55));
        g.dispose();
        return img;
    }

    /** Stadtmarke: Kreis f&uuml;rs Inland, Raute f&uuml;rs Drittland - mit dunklem Halo. */
    private static void marke(Graphics2D g, double x, double y, Color c, boolean raute, boolean small) {
        double r = small ? 16 : 13;
        java.awt.Shape form;
        if (raute) {
            Path2D p = new Path2D.Double();
            p.moveTo(x, y - r);
            p.lineTo(x + r, y);
            p.lineTo(x, y + r);
            p.lineTo(x - r, y);
            p.closePath();
            form = p;
        } else {
            form = new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
        }
        g.setColor(new Color(0x0A, 0x1B, 0x26, 230));
        g.setStroke(new BasicStroke(5f));
        g.draw(form);
        g.setColor(c);
        g.fill(form);
        if (!small) {
            g.setColor(KartenFarben.MEER_OBEN);
            g.fill(raute ? new Ellipse2D.Double(x - 4, y - 4, 8, 8) : new Ellipse2D.Double(x - 5, y - 5, 10, 10));
        }
    }
}
