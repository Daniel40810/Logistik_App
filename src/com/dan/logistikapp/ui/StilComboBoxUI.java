package com.dan.logistikapp.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

/** ComboBox-Popup mit der Scrollbar der dunklen Seitenleiste. */
public final class StilComboBoxUI extends BasicComboBoxUI {

    private static final Color GRUND = new Color(0x0C1A23);
    private static final Color FELD = new Color(0x13262F);
    private static final Color LINIE = new Color(0x1E3440);

    @Override
    protected JButton createArrowButton() {
        JButton pfeil = new JButton() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                try {
                    g.setColor(FELD);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    int x = getWidth() / 2;
                    int y = getHeight() / 2;
                    g.setColor(new Color(0x78909C));
                    g.fill(new Polygon(new int[] {x - 7, x + 7, x},
                            new int[] {y - 3, y - 3, y + 5}, 3));
                } finally {
                    g.dispose();
                }
            }
        };
        pfeil.setOpaque(true);
        pfeil.setBackground(FELD);
        pfeil.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, LINIE));
        pfeil.setFocusable(false);
        return pfeil;
    }

    @Override
    public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
        g.setColor(FELD);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
        Object value = comboBox.getSelectedItem();
        ListCellRenderer renderer = comboBox.getRenderer();
        if (renderer == null) {
            super.paintCurrentValue(g, bounds, hasFocus);
            return;
        }
        Component c = renderer.getListCellRendererComponent(new JList(), value, -1, false, false);
        c.setBackground(FELD);
        c.setForeground(com.dan.logistikapp.karte.KartenFarben.TEXT);
        SwingUtilities.paintComponent(g, c, comboBox, bounds.x + 4, bounds.y,
                Math.max(0, bounds.width - 8), bounds.height);
    }

    @Override
    protected ComboPopup createPopup() {
        BasicComboPopup popup = new BasicComboPopup(comboBox) {
            private static final long serialVersionUID = 1L;

            @Override
            protected JScrollPane createScroller() {
                JScrollPane scroller = super.createScroller();
                scroller.setBorder(BorderFactory.createLineBorder(LINIE));
                scroller.setBackground(GRUND);
                scroller.getViewport().setBackground(FELD);
                if (scroller.getViewport().getView() instanceof JList<?>) {
                    scroller.getViewport().getView().setBackground(FELD);
                    scroller.getViewport().getView().setForeground(com.dan.logistikapp.karte.KartenFarben.TEXT);
                }
                if (scroller.getVerticalScrollBar() != null) {
                    scroller.getVerticalScrollBar().setUI(new StilScrollBarUI());
                    scroller.getVerticalScrollBar().setOpaque(false);
                }
                if (scroller.getHorizontalScrollBar() != null) {
                    scroller.getHorizontalScrollBar().setUI(new StilScrollBarUI());
                    scroller.getHorizontalScrollBar().setOpaque(false);
                }
                return scroller;
            }
        };
        popup.setBorder(BorderFactory.createLineBorder(LINIE));
        popup.setBackground(GRUND);
        popup.getList().setBackground(FELD);
        popup.getList().setForeground(com.dan.logistikapp.karte.KartenFarben.TEXT);
        return popup;
    }
}
