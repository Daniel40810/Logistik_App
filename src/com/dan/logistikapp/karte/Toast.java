package com.dan.logistikapp.karte;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Kurze Meldung oben in der Karte, die nach ein paar Sekunden verblasst.
 *
 * @author Dan
 */
final class Toast {

    enum Art { ERFOLG, ZOLL, FEHLER, HINWEIS }

    private final String text;
    private final Art art;
    private final long bis;

    Toast(String text, Art art, long jetztMs, long dauerMs) {
        this.text = text;
        this.art = art;
        this.bis = jetztMs + dauerMs;
    }

    String getText() {
        return text;
    }

    Art getArt() {
        return art;
    }

    boolean vorbei(long jetzt) {
        return jetzt >= bis;
    }

    void zeichne(Graphics2D g, int breite, long jetzt) {
        long rest = bis - jetzt;
        if (rest <= 0) {
            return;
        }
        float a = (float) Math.min(1, rest / 400.0);
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, 13);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 40;
        double x = (breite - w) / 2.0;
        double y = 16;
        Color akzent;
        switch (art) {
            case ERFOLG:
                akzent = new Color(0x45B7C1);
                break;
            case ZOLL:
                akzent = ContainerEbene.ZOLL_FARBE;
                break;
            case FEHLER:
                akzent = new Color(0xE0605A);
                break;
            default:
                akzent = new Color(0x8FA7B0);
        }
        g.setColor(new Color(0x0A, 0x1B, 0x26, (int) (225 * a)));
        g.fill(new RoundRectangle2D.Double(x, y, w, 34, 10, 10));
        g.setColor(new Color(akzent.getRed(), akzent.getGreen(), akzent.getBlue(), (int) (255 * a)));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Double(x, y, w, 34, 10, 10));
        g.fill(new RoundRectangle2D.Double(x + 10, y + 11, 12, 12, 12, 12));
        g.setColor(new Color(0xE6, 0xEE, 0xF1, (int) (255 * a)));
        g.drawString(text, (float) x + 30, (float) y + 22);
    }
}
