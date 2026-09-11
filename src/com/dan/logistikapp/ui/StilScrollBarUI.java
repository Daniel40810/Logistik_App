package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.KartenFarben;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Dunkle, kompakte Scrollbar passend zur Seitenleiste. */
public final class StilScrollBarUI extends BasicScrollBarUI {

    private static final Color SPUR = new Color(0x0A1B26);
    private static final Color GRIFF = new Color(0x2A5960);
    private static final Color GRIFF_AKTIV = KartenFarben.GRENZE_INLAND;

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return leereTaste();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return leereTaste();
    }

    private static JButton leereTaste() {
        JButton b = new JButton();
        b.setOpaque(false);
        b.setFocusable(false);
        b.setBorder(null);
        b.setContentAreaFilled(false);
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        return b;
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        JScrollBar b = (JScrollBar) c;
        return b.getOrientation() == JScrollBar.VERTICAL
                ? new Dimension(11, 0) : new Dimension(0, 11);
    }

    @Override
    protected void paintTrack(Graphics g0, JComponent c, Rectangle r) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setColor(SPUR);
            g.fillRect(r.x, r.y, r.width, r.height);
        } finally {
            g.dispose();
        }
    }

    @Override
    protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
        if (r.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = r.x + 2;
            int y = r.y + 2;
            int w = Math.max(4, r.width - 4);
            int h = Math.max(4, r.height - 4);
            RoundRectangle2D griff = new RoundRectangle2D.Double(x, y, w, h, 7, 7);
            g.setColor(isDragging ? GRIFF_AKTIV : GRIFF);
            g.fill(griff);
            g.setColor(new Color(0x3B7378));
            g.setStroke(new BasicStroke(0.7f));
            g.draw(griff);
        } finally {
            g.dispose();
        }
    }
}
