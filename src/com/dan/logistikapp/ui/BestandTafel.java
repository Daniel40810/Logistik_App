package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.ContainerEbene;
import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.karte.KartenPanel;
import com.dan.logistikapp.model.Bestand;
import com.dan.logistikapp.model.Zone;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;

/**
 * Seitenleiste "Bestand": jede Stadt mit Anzahl, TEU und einem Balken aus
 * den Warenfarben. Die Balkenl&auml;nge ist relativ zur vollsten Stadt, so
 * sieht man Unterschiede auf einen Blick. Gruppiert nach Zone.
 *
 * <p>Ein Klick auf eine Stadt holt sie auf der Karte ins Bild.</p>
 *
 * @author Dan
 */
public final class BestandTafel extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Was die Tafel ausl&ouml;sen kann. */
    public interface Aktion {
        void zeigeOrt(int ortId);
        void favoritOrt(int ortId);
    }

    private static final int KOPF = 64;
    private static final int GRUPPE = 28;
    private static final int ZEILE = 58;

    private static final Color GRUND = new Color(0x0C1A23);
    private static final Color ZEILE_HOVER = new Color(0x13262F);
    private static final Color LINIE = new Color(0x1E3440);
    private static final Color VOLL = new Color(0xE0524A);

    private List<Bestand.Stadt> staedte = Collections.emptyList();
    /** Oberkante je Stadtzeile, parallel zu {@link #staedte}. */
    private int[] oben = new int[0];
    private int hoehe = KOPF + 40;
    private Aktion aktion;
    private Set<Integer> favoriten = Collections.emptySet();
    private int hover = -1;

    private final Font fKopf = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private final Font fName = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private final Font fKlein = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private final Font fZahl = new Font(Font.MONOSPACED, Font.BOLD, 12);

    public BestandTafel() {
        setOpaque(true);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int z = zeileBei(e.getY());
                if (z != hover) {
                    hover = z;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                klick(e.getX(), e.getY());
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public void setAktion(Aktion a) {
        this.aktion = a;
    }

    public void setFavoriten(Set<Integer> ids) {
        this.favoriten = ids == null ? Collections.<Integer>emptySet() : ids;
        repaint();
    }

    public void setStand(List<Bestand.Stadt> neu) {
        this.staedte = neu == null ? Collections.<Bestand.Stadt>emptyList() : new ArrayList<Bestand.Stadt>(neu);
        oben = new int[staedte.size()];
        int y = KOPF;
        Zone zuletzt = null;
        for (int i = 0; i < staedte.size(); i++) {
            Zone z = staedte.get(i).getOrt().getZone();
            if (z != zuletzt) {
                y += GRUPPE;
                zuletzt = z;
            }
            oben[i] = y;
            y += ZEILE;
        }
        hoehe = y + 16;
        revalidate();
        repaint();
    }

    public List<Bestand.Stadt> getStand() {
        return Collections.unmodifiableList(staedte);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(ZollTafel.BREITE, hoehe);
    }

    /** F&uuml;r Tests und Maus. */
    public void klick(int x, int y) {
        int z = zeileBei(y);
        if (z >= 0 && aktion != null) {
            int id = staedte.get(z).getOrt().getOrtId();
            if (x >= getWidth() - 44) {
                aktion.favoritOrt(id);
            } else {
                aktion.zeigeOrt(id);
            }
        }
    }

    /** Mitte der Zeile einer Stadt (f&uuml;r Tests), oder -1. */
    public int zeileMitte(String stadt) {
        for (int i = 0; i < staedte.size(); i++) {
            if (staedte.get(i).getOrt().getName().equals(stadt)) {
                return oben[i] + ZEILE / 2;
            }
        }
        return -1;
    }

    private int zeileBei(int y) {
        for (int i = 0; i < oben.length; i++) {
            if (y >= oben[i] && y < oben[i] + ZEILE) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------ Zeichnen

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            g.setColor(GRUND);
            g.fillRect(0, 0, w, getHeight());
            g.setColor(LINIE);
            g.fillRect(0, 0, 1, getHeight());

            int anzahl = 0;
            int teu = 0;
            int zoll = 0;
            int maxTeu = 1;
            for (Bestand.Stadt s : staedte) {
                anzahl += s.getAnzahl();
                teu += s.getTeu();
                zoll += s.getBeimZoll();
                maxTeu = Math.max(maxTeu, s.getTeu());
            }
            g.setFont(fKopf);
            g.setColor(KartenFarben.TEXT_LEISE);
            g.drawString("BESTAND", 16, 28);
            g.setFont(fKlein);
            g.setColor(new Color(0x6F8791));
            g.drawString(anzahl + " Container · " + teu + " TEU" + (zoll > 0 ? " · " + zoll + " beim Zoll" : "")
                    + " · Klick zeigt die Stadt", 16, 48);
            g.setColor(LINIE);
            g.fillRect(12, KOPF - 1, w - 24, 1);

            Zone zuletzt = null;
            for (int i = 0; i < staedte.size(); i++) {
                Bestand.Stadt s = staedte.get(i);
                Zone z = s.getOrt().getZone();
                if (z != zuletzt) {
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
                    g.setColor(KartenFarben.marke(z));
                    g.drawString(z.anzeige().toUpperCase(java.util.Locale.GERMANY), 16, oben[i] - 9);
                    zuletzt = z;
                }
                zeichneZeile(g, s, i, w, maxTeu);
            }
        } finally {
            g.dispose();
        }
    }

    private void zeichneZeile(Graphics2D g, Bestand.Stadt s, int i, int w, int maxTeu) {
        int y = oben[i];
        if (i == hover) {
            g.setColor(ZEILE_HOVER);
            g.fillRect(1, y, w - 1, ZEILE);
        }
        KartenPanel.zeichneMarke(g, s.getOrt().getZone(), 24, y + 17, 5.5);
        g.setFont(fName);
        g.setColor(KartenFarben.TEXT);
        g.drawString(s.getOrt().getName(), 38, y + 22);

        Integer kap = s.getOrt().getKapazitaetTeu();
        String zahl = kap != null
                ? (s.getAnzahl() == 0 ? "leer · " : s.getAnzahl() + " · ") + s.getTeu() + "/" + kap + " TEU"
                : (s.getAnzahl() == 0 ? "leer" : s.getAnzahl() + " · " + s.getTeu() + " TEU");
        g.setFont(fZahl);
        FontMetrics fm = g.getFontMetrics();
        boolean voll = kap != null && s.getTeu() >= kap;
        g.setColor(voll ? VOLL : s.getAnzahl() == 0 ? KartenFarben.TEXT_LEISE : KartenFarben.TEXT);
        g.drawString(zahl, w - 50 - fm.stringWidth(zahl), y + 22);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        g.setColor(favoriten.contains(s.getOrt().getOrtId()) ? new Color(0xE6C15A) : KartenFarben.TEXT_LEISE);
        g.drawString(favoriten.contains(s.getOrt().getOrtId()) ? "★" : "☆", w - 31, y + 22);

        // Balken aus den Warenfarben: volle Breite = Kapazitaet; ohne
        // Kapazitaet relativ zur vollsten Stadt
        double bx = 38;
        double by = y + 30;
        double breite = w - 38 - 16;
        double bezug = kap != null ? kap : maxTeu;
        g.setColor(new Color(0x13262F));
        g.fill(new RoundRectangle2D.Double(bx, by, breite, 9, 4, 4));
        double x = bx;
        for (Bestand.Anteil a : s.getAnteile()) {
            double bw = Math.min(breite, breite * a.getTeu() / bezug);
            g.setColor(a.getFarbe());
            g.fill(new Rectangle2D.Double(x, by, Math.max(1, bw - 1), 9));
            x += bw;
        }

        // Darunter die Waren in Worten, beim Zoll in Bernstein
        g.setFont(fKlein);
        fm = g.getFontMetrics();
        StringBuilder sb = new StringBuilder();
        for (Bestand.Anteil a : s.getAnteile()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(a.getWare()).append(' ').append(a.getAnzahl());
        }
        String zollText = s.getBeimZoll() > 0 ? s.getBeimZoll() + " beim Zoll" : null;
        int platz = (int) breite - (zollText == null ? 0 : fm.stringWidth(zollText) + 10);
        g.setColor(KartenFarben.TEXT_LEISE);
        g.drawString(ZollTafel.passend(fm, sb.length() == 0 ? "kein Container" : sb.toString(), platz),
                38, y + 52);
        if (zollText != null) {
            g.setColor(ContainerEbene.ZOLL_FARBE);
            g.drawString(zollText, w - 16 - fm.stringWidth(zollText), y + 52);
        }
        g.setColor(LINIE);
        g.fillRect(12, y + ZEILE - 1, w - 24, 1);
    }
}
