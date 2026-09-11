package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.Ort;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;

/** Kleine Liste der markierten Städte und Container. */
final class FavoritenTafel extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final Color GRUND = new Color(0x0A1B26);
    private static final Color LINIE = new Color(0x1E3440);
    private final List<Eintrag> eintraege = new ArrayList<Eintrag>();
    private Aktion aktion;

    interface Aktion {
        void zeigeOrt(int id);
        void zeigeContainer(int id);
    }

    private static final class Eintrag {
        final int id;
        final boolean ort;
        final String name;
        final String zusatz;

        Eintrag(int id, boolean ort, String name, String zusatz) {
            this.id = id;
            this.ort = ort;
            this.name = name;
            this.zusatz = zusatz;
        }
    }

    FavoritenTafel() {
        setOpaque(true);
        setBackground(GRUND);
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int i = (e.getY() - 27) / 30;
                if (i >= 0 && i < eintraege.size() && e.getY() >= 27 && aktion != null) {
                    Eintrag x = eintraege.get(i);
                    if (x.ort) {
                        aktion.zeigeOrt(x.id);
                    } else {
                        aktion.zeigeContainer(x.id);
                    }
                }
            }
        });
    }

    void setAktion(Aktion a) {
        aktion = a;
    }

    void setDaten(List<Ort> orte, List<ContainerInfo> container, Set<Integer> ortFavoriten,
            Set<Integer> containerFavoriten) {
        Set<Integer> oi = ortFavoriten == null ? Collections.<Integer>emptySet() : new HashSet<Integer>(ortFavoriten);
        Set<Integer> ci = containerFavoriten == null ? Collections.<Integer>emptySet() : new HashSet<Integer>(containerFavoriten);
        eintraege.clear();
        if (orte != null) {
            for (Ort o : orte) {
                if (oi.contains(o.getOrtId())) {
                    eintraege.add(new Eintrag(o.getOrtId(), true, o.getName(), "Stadt · " + o.getIso2()));
                }
            }
        }
        if (container != null) {
            for (ContainerInfo c : container) {
                if (ci.contains(c.getContainerId())) {
                    eintraege.add(new Eintrag(c.getContainerId(), false,
                            com.dan.logistikapp.model.Iso6346.anzeige(c.getKennung()),
                            "Container · " + c.getOrt()));
                }
            }
        }
        setPreferredSize(new java.awt.Dimension(ZollTafel.BREITE, eintraege.isEmpty() ? 30 : 27 + eintraege.size() * 30));
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(GRUND);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            g.setColor(KartenFarben.TEXT_LEISE);
            g.drawString("FAVORITEN", 14, 16);
            if (eintraege.isEmpty()) {
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
                g.drawString("☆ Im Bestand oder Detailfenster markieren", 14, 27);
                return;
            }
            for (int i = 0; i < eintraege.size(); i++) {
                Eintrag x = eintraege.get(i);
                int y = 27 + i * 30;
                if (i % 2 == 1) {
                    g.setColor(new Color(0x0C202B));
                    g.fillRect(1, y, getWidth() - 2, 30);
                }
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                g.setColor(new Color(0xE6C15A));
                g.drawString("★", 14, y + 18);
                g.setColor(KartenFarben.TEXT);
                g.drawString(x.name, 32, y + 15);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
                g.setColor(KartenFarben.TEXT_LEISE);
                g.drawString(x.zusatz, 32, y + 26);
                g.setColor(LINIE);
                g.fillRect(12, y + 29, getWidth() - 24, 1);
            }
        } finally {
            g.dispose();
        }
    }
}
