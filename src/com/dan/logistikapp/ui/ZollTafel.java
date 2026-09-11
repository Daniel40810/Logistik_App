package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.ContainerEbene;
import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.model.ZollFall;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Seitenleiste "Beim Zoll": wer wartet, an welcher Grenze, wie lange -
 * mit Freigabe je Zeile und f&uuml;r alle.
 *
 * <p>Die Wartezeit tickt jede Sekunde weiter, ausgehend von dem, was die
 * Datenbank beim Lesen gerechnet hat. Ein Klick auf eine Zeile holt den
 * Container auf der Karte ins Bild.</p>
 *
 * @author Dan
 */
public final class ZollTafel extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Was die Tafel ausl&ouml;sen kann. */
    public interface Aktion {
        void freigeben(int containerId);

        void alleFreigeben(List<Integer> ids);

        void zeigen(int containerId);
    }

    static final int BREITE = 320;
    private static final int KOPF = 64;
    private static final int ZEILE = 62;
    private static final int FUSS = 56;

    private static final Color GRUND = new Color(0x0C1A23);
    private static final Color ZEILE_HOVER = new Color(0x13262F);
    private static final Color LINIE = new Color(0x1E3440);

    private List<ZollFall> faelle = Collections.emptyList();
    private Aktion aktion;
    private com.dan.logistikapp.model.Tarif tarif;
    /** Zeitreise: der gezeigte Zeitpunkt; 0 = live. Dann gibt es nichts freizugeben. */
    private long stichtag;
    private int hoverZeile = -1;
    private boolean hoverKnopf;
    private boolean hoverAlle;
    private final Timer sekunde = new Timer(1000, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            repaint();
        }
    });

    private final Font fKopf = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private final Font fKennung = new Font(Font.MONOSPACED, Font.BOLD, 13);
    private final Font fKlein = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private final Font fZeit = new Font(Font.MONOSPACED, Font.BOLD, 12);

    public ZollTafel() {
        setOpaque(true);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int z = zeileBei(e.getY());
                boolean k = z >= 0 && knopf(z).contains(e.getPoint());
                boolean a = alleKnopf().contains(e.getPoint()) && !faelle.isEmpty();
                if (z != hoverZeile || k != hoverKnopf || a != hoverAlle) {
                    hoverZeile = z;
                    hoverKnopf = k;
                    hoverAlle = a;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverZeile = -1;
                hoverKnopf = false;
                hoverAlle = false;
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

    /** Mit Tarif l&auml;uft das Standgeld je Zeile sichtbar mit (wie LOG_ZOLL_V.standgeld_bisher). */
    public void setTarif(com.dan.logistikapp.model.Tarif t) {
        this.tarif = t;
        repaint();
    }

    /** Standgeld, das jetzt f&auml;llig w&auml;re, &uuml;ber alle Wartenden; {@code null} ohne Tarif. */
    public java.math.BigDecimal standgeldJetzt(long jetztMs) {
        if (tarif == null) {
            return null;
        }
        java.math.BigDecimal s = java.math.BigDecimal.ZERO;
        for (ZollFall f : faelle) {
            java.math.BigDecimal b = tarif.standgeld(f.wartetSek(jetztMs) * 1000L);
            if (b != null) {
                s = s.add(b);
            }
        }
        return s;
    }

    /** Zeitreise: Wartezeit und Standgeld bis zu diesem Zeitpunkt, keine Kn&ouml;pfe. 0 = live. */
    public void setStichtag(long ms) {
        this.stichtag = ms;
        repaint();
    }

    public boolean istNurAnsicht() {
        return stichtag > 0;
    }

    private long jetzt() {
        return stichtag > 0 ? stichtag : System.currentTimeMillis();
    }

    public void setFaelle(List<ZollFall> neu) {
        this.faelle = neu == null ? Collections.<ZollFall>emptyList() : new ArrayList<ZollFall>(neu);
        if (faelle.isEmpty() || stichtag > 0) {
            sekunde.stop();
        } else {
            sekunde.start();
        }
        revalidate();
        repaint();
    }

    public List<ZollFall> getFaelle() {
        return Collections.unmodifiableList(faelle);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(BREITE, KOPF + Math.max(1, faelle.size()) * ZEILE + FUSS);
    }

    // ------------------------------------------------------------ Klicks

    /** F&uuml;r Tests und Maus: was an dieser Stelle liegt, wird ausgel&ouml;st. */
    public void klick(int x, int y) {
        if (aktion == null) {
            return;
        }
        if (stichtag > 0) {
            int z = zeileBei(y);
            if (z >= 0) {
                aktion.zeigen(faelle.get(z).getContainerId());
            }
            return;
        }
        if (!faelle.isEmpty() && alleKnopf().contains(x, y)) {
            List<Integer> ids = new ArrayList<Integer>();
            for (ZollFall f : faelle) {
                ids.add(f.getContainerId());
            }
            aktion.alleFreigeben(ids);
            return;
        }
        int z = zeileBei(y);
        if (z < 0) {
            return;
        }
        if (knopf(z).contains(x, y)) {
            aktion.freigeben(faelle.get(z).getContainerId());
        } else {
            aktion.zeigen(faelle.get(z).getContainerId());
        }
    }

    private int zeileBei(int y) {
        int z = (y - KOPF) / ZEILE;
        return (y >= KOPF && z < faelle.size()) ? z : -1;
    }

    /** Freigabe-Knopf einer Zeile. */
    public Rectangle knopf(int zeile) {
        int y = KOPF + zeile * ZEILE;
        return new Rectangle(getBreite() - 96, y + 32, 82, 22);
    }

    /** "Alle freigeben" unter der Liste. */
    public Rectangle alleKnopf() {
        int y = KOPF + Math.max(1, faelle.size()) * ZEILE + 12;
        return new Rectangle(14, y, getBreite() - 28, 30);
    }

    private int getBreite() {
        return getWidth() > 0 ? getWidth() : BREITE;
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

            // Kopf
            g.setFont(fKopf);
            g.setColor(KartenFarben.TEXT_LEISE);
            g.drawString("BEIM ZOLL", 16, 28);
            String n = String.valueOf(faelle.size());
            FontMetrics fm = g.getFontMetrics();
            int bw = fm.stringWidth(n) + 14;
            g.setColor(faelle.isEmpty() ? LINIE : ContainerEbene.ZOLL_FARBE);
            g.fill(new RoundRectangle2D.Double(96, 15, bw, 18, 18, 18));
            g.setColor(faelle.isEmpty() ? KartenFarben.TEXT_LEISE : new Color(0x1A1206));
            g.drawString(n, 103, 28);
            g.setFont(fKlein);
            g.setColor(new Color(0x6F8791));
            java.math.BigDecimal lauf = standgeldJetzt(jetzt());
            g.drawString(faelle.isEmpty() ? "Fahrten über eine Zollgrenze landen hier"
                    : lauf != null && stichtag > 0 ? "Damals: Standgeld bis dahin "
                    + String.format(java.util.Locale.GERMANY, "%,.2f €", lauf)
                    : lauf != null ? "Standgeld läuft: " + String.format(java.util.Locale.GERMANY, "%,.2f €", lauf)
                    + " · Klick zeigt den Container"
                    : "Klick auf eine Zeile zeigt den Container", 16, 48);
            g.setColor(LINIE);
            g.fillRect(12, KOPF - 1, w - 24, 1);

            if (faelle.isEmpty()) {
                g.setFont(fKlein);
                g.setColor(KartenFarben.TEXT_LEISE);
                g.drawString("Kein Container wartet.", 16, KOPF + 34);
            }
            long jetzt = jetzt();
            for (int i = 0; i < faelle.size(); i++) {
                zeichneZeile(g, faelle.get(i), i, w, jetzt);
            }
            if (!faelle.isEmpty() && stichtag == 0) {
                Rectangle a = alleKnopf();
                zeichneKnopf(g, a, faelle.size() > 1 ? "Alle " + faelle.size() + " freigeben" : "Freigeben", hoverAlle);
            }
        } finally {
            g.dispose();
        }
    }

    private void zeichneZeile(Graphics2D g, ZollFall f, int i, int w, long jetzt) {
        int y = KOPF + i * ZEILE;
        if (i == hoverZeile && (!hoverKnopf || stichtag > 0)) {
            g.setColor(ZEILE_HOVER);
            g.fillRect(1, y, w - 1, ZEILE);
        }
        g.setColor(f.getFarbe());
        g.fill(new RoundRectangle2D.Double(16, y + 14, 16, 10, 3, 3));
        g.setColor(new Color(0, 0, 0, 110));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(16, y + 14, 16, 10, 3, 3));
        g.setFont(fKennung);
        g.setColor(KartenFarben.TEXT);
        g.drawString(f.getKennungAnzeige(), 42, y + 24);
        g.setFont(fKlein);
        g.setColor(KartenFarben.TEXT_LEISE);
        g.drawString(passend(g.getFontMetrics(), f.getWare() + " · " + f.getVonOrt() + " → " + f.getOrt(),
                knopf(i).x - 22), 16, y + 42);
        g.setColor(ContainerEbene.ZOLL_FARBE);
        g.drawString("Grenze " + f.grenze(), 16, y + 56);
        g.setFont(fZeit);
        String t = dauer(f.wartetSek(jetzt));
        int tw = g.getFontMetrics().stringWidth(t);
        g.drawString(t, w - 14 - tw, y + 24);
        if (tarif != null) {
            // Standgeld je angefangene Viertelstunde - springt, wenn eine neue beginnt
            java.math.BigDecimal sg = tarif.standgeld(f.wartetSek(jetzt) * 1000L);
            if (sg != null) {
                String e = String.format(java.util.Locale.GERMANY, "%,.2f €", sg);
                g.setFont(fKlein);
                g.setColor(KartenFarben.TEXT_LEISE);
                g.drawString(e, w - 14 - tw - 10 - g.getFontMetrics().stringWidth(e), y + 24);
            }
        }
        if (stichtag == 0) {
            zeichneKnopf(g, knopf(i), "Freigeben", i == hoverZeile && hoverKnopf);
        }
        g.setColor(LINIE);
        g.fillRect(12, y + ZEILE - 1, w - 24, 1);
    }

    private void zeichneKnopf(Graphics2D g, Rectangle r, String text, boolean hover) {
        Color c = ContainerEbene.ZOLL_FARBE;
        RoundRectangle2D.Double k = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 8, 8);
        if (hover) {
            g.setColor(c);
            g.fill(k);
        }
        g.setColor(c);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(k);
        g.setFont(fKopf);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(hover ? new Color(0x1A1206) : c);
        g.drawString(text, r.x + (r.width - fm.stringWidth(text)) / 2, r.y + r.height / 2 + fm.getAscent() / 2 - 2);
    }

    /** 75 &rarr; "01:15", 3725 &rarr; "1:02:05". */
    /** K&uuml;rzt mit "…", bis der Text in {@code breite} Bildpunkte passt. */
    static String passend(FontMetrics fm, String s, int breite) {
        if (fm.stringWidth(s) <= breite) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && fm.stringWidth(t + "…") > breite) {
            t = t.substring(0, t.length() - 1);
        }
        return t.trim() + "…";
    }

    static String dauer(long s) {
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sek = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sek) : String.format("%02d:%02d", m, sek);
    }
}
