package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.ContainerEbene;
import com.dan.logistikapp.karte.KartenFarben;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/** Kompakte Live-Kennzahlen über den Reitern. */
final class DashboardTafel extends JComponent {

    private static final long serialVersionUID = 1L;
    private int container;
    private int teu;
    private int zoll;
    private int auftraege;

    DashboardTafel() {
        setPreferredSize(new java.awt.Dimension(ZollTafel.BREITE, 78));
        setOpaque(true);
        setBackground(new Color(0x0A1B26));
    }

    void setWerte(int container, int teu, int zoll, int auftraege) {
        this.container = container;
        this.teu = teu;
        this.zoll = zoll;
        this.auftraege = auftraege;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            g.setColor(getBackground());
            g.fillRect(0, 0, w, getHeight());
            g.setColor(new Color(0x1E3440));
            g.fillRect(0, getHeight() - 1, w, 1);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            g.setColor(KartenFarben.TEXT_LEISE);
            g.drawString("ÜBERSICHT", 14, 15);
            int gap = 6;
            int cardW = (w - 28 - gap) / 2;
            karte(g, 14, 22, cardW, "Container", String.valueOf(container), KartenFarben.TEXT);
            karte(g, 14 + cardW + gap, 22, cardW, "TEU", String.valueOf(teu), new Color(0x63B9C1));
            karte(g, 14, 49, cardW, "Zollfälle", String.valueOf(zoll), zoll > 0 ? ContainerEbene.ZOLL_FARBE : KartenFarben.TEXT);
            karte(g, 14 + cardW + gap, 49, cardW, "Aufträge", String.valueOf(auftraege), new Color(0xA7B6FF));
        } finally {
            g.dispose();
        }
    }

    private static void karte(Graphics2D g, int x, int y, int w, String label, String wert, Color akzent) {
        g.setColor(new Color(0x13262F));
        g.fill(new RoundRectangle2D.Double(x, y, w, 22, 6, 6));
        g.setColor(new Color(0x1E3440));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x, y, w, 22, 6, 6));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        g.setColor(KartenFarben.TEXT_LEISE);
        g.drawString(label, x + 7, y + 14);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.setColor(akzent);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(wert, x + w - 7 - fm.stringWidth(wert), y + 15);
    }
}
