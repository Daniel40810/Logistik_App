package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.ContainerEbene;
import com.dan.logistikapp.karte.KartenFarben;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

/**
 * Die rechte Leiste: Reiter (Zoll, Bestand, Fahrten, Auftr&auml;ge, Kosten) &uuml;ber einer
 * Kartei, darunter der Knopf f&uuml;r die Demo-Szene.
 *
 * @author Dan
 */
public final class SeitenLeiste extends JPanel {

    private static final long serialVersionUID = 1L;

    public static final int ZOLL = 0;
    public static final int BESTAND = 1;
    public static final int FAHRTEN = 2;
    public static final int AUFTRAEGE = 3;
    public static final int KOSTEN = 4;

    private final String[] namen;
    private static final Color GRUND = new Color(0x0C1A23);
    private static final Color LINIE = new Color(0x1E3440);

    private final CardLayout kartei = new CardLayout();
    private final JPanel karten = new JPanel(kartei);
    private final Reiter reiter = new Reiter();
    private final KartenFilter kartenFilter = new KartenFilter();
    private final DashboardTafel dashboard = new DashboardTafel();
    private final FavoritenTafel favoriten = new FavoritenTafel();
    private final DemoKnopf demo = new DemoKnopf();
    private final StadtKnopf stadt = new StadtKnopf();
    private final NeuKnopf neu = new NeuKnopf();
    private final CsvKnopf csvExport = new CsvKnopf(true);
    private final CsvKnopf csvImport = new CsvKnopf(false);
    private int aktiv = ZOLL;
    private int zollAnzahl;
    private int auftragAnzahl;
    private boolean auftragBlockiert;
    private Runnable demoAktion;
    private Runnable containerAktion;
    private Runnable stadtAktion;
    private Runnable csvExportAktion;
    private Runnable csvImportAktion;

    public SeitenLeiste(JComponent zoll, JComponent bestand, JComponent fahrten, JComponent auftraege,
            JComponent kosten) {
        this(new String[] {"Zoll", "Bestand", "Fahrten", "Aufträge", "Kosten"},
                new JComponent[] {zoll, bestand, fahrten, auftraege, kosten});
    }

    /** Beliebig viele Reiter; der erste mit dem Namen "Zoll" tr&auml;gt die Zahl. */
    public SeitenLeiste(String[] namen, JComponent[] inhalt) {
        super(new BorderLayout());
        this.namen = namen.clone();
        setBackground(GRUND);
        setPreferredSize(new Dimension(ZollTafel.BREITE, 100));
        karten.setBackground(GRUND);
        for (int i = 0; i < inhalt.length; i++) {
            JScrollPane rolle = new JScrollPane(inhalt[i], ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            rolle.setBorder(BorderFactory.createEmptyBorder());
            rolle.getViewport().setBackground(GRUND);
            rolle.getVerticalScrollBar().setUnitIncrement(16);
            rolle.getVerticalScrollBar().setUI(new StilScrollBarUI());
            rolle.getHorizontalScrollBar().setUI(new StilScrollBarUI());
            rolle.getVerticalScrollBar().setOpaque(false);
            rolle.getHorizontalScrollBar().setOpaque(false);
            karten.add(rolle, this.namen[i]);
        }
        JPanel kopf = new JPanel(new BorderLayout());
        kopf.setOpaque(false);
        kopf.add(dashboard, BorderLayout.NORTH);
        kopf.add(reiter, BorderLayout.CENTER);
        kopf.add(kartenFilter, BorderLayout.SOUTH);
        add(kopf, BorderLayout.NORTH);
        add(karten, BorderLayout.CENTER);
        JPanel aktionen = new JPanel(new BorderLayout());
        aktionen.setOpaque(false);
        JPanel neuAktionen = new JPanel(new java.awt.GridLayout(5, 1));
        neuAktionen.setOpaque(false);
        neuAktionen.add(stadt);
        neuAktionen.add(neu);
        neuAktionen.add(demo);
        neuAktionen.add(csvExport);
        neuAktionen.add(csvImport);
        aktionen.add(favoriten, BorderLayout.CENTER);
        aktionen.add(neuAktionen, BorderLayout.SOUTH);
        add(aktionen, BorderLayout.SOUTH);
    }

    public void zeige(int i) {
        aktiv = i;
        kartei.show(karten, namen[i]);
        reiter.repaint();
    }

    public int getAktiv() {
        return aktiv;
    }

    /** Zahl im Zoll-Reiter; bei 0 bleibt er ruhig. */
    public void setZollAnzahl(int n) {
        zollAnzahl = n;
        reiter.repaint();
    }

    public int getZollAnzahl() {
        return zollAnzahl;
    }

    /** Zahl offener Auftr&auml;ge im Reiter; Bernstein, wenn einer blockiert ist. */
    public void setAuftragAnzahl(int n, boolean blockiert) {
        auftragAnzahl = n;
        auftragBlockiert = blockiert;
        reiter.repaint();
    }

    public int getAuftragAnzahl() {
        return auftragAnzahl;
    }

    /** Wird beim Klick auf den Demo-Knopf gerufen. */
    public void setDemoAktion(Runnable r) {
        this.demoAktion = r;
    }

    /** Wird beim Klick auf „Neuer Container“ gerufen. */
    public void setContainerAktion(Runnable r) {
        this.containerAktion = r;
    }

    /** Wird beim Klick auf „Neue Stadt“ gerufen. */
    public void setStadtAktion(Runnable r) {
        this.stadtAktion = r;
    }

    public void setCsvAktion(Runnable export, Runnable importieren) {
        this.csvExportAktion = export;
        this.csvImportAktion = importieren;
    }

    public void setKartenFilterAktion(KartenFilter.FilterAktion a) {
        kartenFilter.setAktion(a);
    }

    public void setKartenFilterWaren(java.util.List<com.dan.logistikapp.model.Ware> waren) {
        kartenFilter.setWaren(waren);
    }

    /** Aktualisiert die vier Kennzahlen der Übersicht. */
    public void setDashboard(int container, int teu, int zoll, int auftraege) {
        dashboard.setWerte(container, teu, zoll, auftraege);
    }

    public void setFavoritenAktion(FavoritenTafel.Aktion a) {
        favoriten.setAktion(a);
    }

    public void setFavoriten(java.util.List<com.dan.logistikapp.model.Ort> orte,
            java.util.List<com.dan.logistikapp.model.ContainerInfo> container,
            java.util.Set<Integer> ortFavoriten, java.util.Set<Integer> containerFavoriten) {
        favoriten.setDaten(orte, container, ortFavoriten, containerFavoriten);
    }

    /** F&uuml;r Tests: „Neuer Container“ dr&uuml;cken. */
    public void klickNeuerContainer() {
        if (containerAktion != null) {
            containerAktion.run();
        }
    }

    /** Für Tests: „Neue Stadt“ drücken. */
    public void klickNeueStadt() {
        if (stadtAktion != null) {
            stadtAktion.run();
        }
    }

    /** Beschriftung des Demo-Knopfs und ob die Demo gerade l&auml;uft (Bernstein). */
    public void setDemoZustand(String text, boolean laeuft) {
        demo.text = text;
        demo.laeuft = laeuft;
        demo.repaint();
    }

    public String getDemoText() {
        return demo.text;
    }

    /** F&uuml;r Tests: Reiter anklicken. */
    public void klickReiter(int i) {
        Rectangle r = reiter.feld(i);
        reiter.klick(r.x + r.width / 2, r.y + r.height / 2);
    }

    /** F&uuml;r Tests: Demo-Knopf dr&uuml;cken. */
    public void klickDemo() {
        if (demoAktion != null) {
            demoAktion.run();
        }
    }

    // ------------------------------------------------------------ Reiter

    private final class Reiter extends JComponent {
        private static final long serialVersionUID = 1L;
        private int hover = -1;

        Reiter() {
            setPreferredSize(new Dimension(ZollTafel.BREITE, 42));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int h = feldBei(e.getX());
                    if (h != hover) {
                        hover = h;
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

        /** Schrift der Reiter: bei mehr als vier etwas kleiner. */
        Font schrift() {
            return new Font(Font.SANS_SERIF, Font.BOLD, namen.length > 4 ? 11 : 12);
        }

        /**
         * Felder nach Wortl&auml;nge: jedes bekommt seinen Text plus einen gleichen
         * Anteil vom Rest. So stehen "Bestand" und "Auftr&auml;ge" nicht aneinander.
         */
        Rectangle feld(int i) {
            int w = (getWidth() > 0 ? getWidth() : ZollTafel.BREITE) - 12;
            FontMetrics fm = getFontMetrics(schrift());
            int summe = 0;
            int[] tw = new int[namen.length];
            for (int k = 0; k < namen.length; k++) {
                tw[k] = fm.stringWidth(namen[k]);
                summe += tw[k];
            }
            double rest = Math.max(0, w - summe) / (double) namen.length;
            double x = 6;
            for (int k = 0; k < i; k++) {
                x += tw[k] + rest;
            }
            return new Rectangle((int) Math.round(x), 0, (int) Math.round(tw[i] + rest), 42);
        }

        int feldBei(int x) {
            for (int i = 0; i < namen.length; i++) {
                if (feld(i).contains(x, 10)) {
                    return i;
                }
            }
            return -1;
        }

        void klick(int x, int y) {
            int i = feldBei(x);
            if (i >= 0) {
                zeige(i);
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(new Color(0x0A1B26));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(LINIE);
                g.fillRect(0, 0, 1, getHeight());
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
                Font f = schrift();
                for (int i = 0; i < namen.length; i++) {
                    Rectangle r = feld(i);
                    boolean an = i == aktiv;
                    g.setFont(f);
                    FontMetrics fm = g.getFontMetrics();
                    String t = namen[i];
                    String zahl = (i == ZOLL && zollAnzahl > 0) ? String.valueOf(zollAnzahl)
                            : (i == AUFTRAEGE && namen.length > AUFTRAEGE && auftragAnzahl > 0)
                            ? String.valueOf(auftragAnzahl) : null;
                    Color plakette = i == AUFTRAEGE && !auftragBlockiert ? KartenFarben.GRENZE_INLAND
                            : ContainerEbene.ZOLL_FARBE;
                    int tx = r.x + (r.width - fm.stringWidth(t)) / 2;
                    g.setColor(an ? KartenFarben.TEXT : (i == hover ? new Color(0xB9CBD1) : KartenFarben.TEXT_LEISE));
                    g.drawString(t, tx, 27);
                    if (zahl != null) {
                        // Blase an der oberen rechten Ecke des Wortes - passt auch bei fuenf Reitern
                        // - hoch genug, dass sie den letzten Buchstaben nicht mehr anschneidet
                        Font klein = new Font(Font.SANS_SERIF, Font.BOLD, 9);
                        g.setFont(klein);
                        FontMetrics km = g.getFontMetrics();
                        int bw = Math.max(13, km.stringWidth(zahl) + 7);
                        int bx = Math.min(tx + fm.stringWidth(t) - 2, r.x + r.width - bw);
                        g.setColor(new Color(0x0A1B26));
                        g.fill(new RoundRectangle2D.Double(bx - 2, 0, bw + 4, 17, 17, 17));
                        g.setColor(plakette);
                        g.fill(new RoundRectangle2D.Double(bx, 2, bw, 13, 13, 13));
                        g.setColor(new Color(0x1A1206));
                        g.drawString(zahl, bx + (bw - km.stringWidth(zahl)) / 2f, 12);
                        g.setFont(f);
                    }
                    if (an) {
                        g.setColor(i == ZOLL && zollAnzahl > 0 ? ContainerEbene.ZOLL_FARBE : KartenFarben.GRENZE_INLAND);
                        g.fill(new RoundRectangle2D.Double(r.x + 6, getHeight() - 4, r.width - 12, 3, 3, 3));
                    }
                }
            } finally {
                g.dispose();
            }
        }
    }

    // ------------------------------------------------------------ Demo

    private final class DemoKnopf extends JComponent {
        private static final long serialVersionUID = 1L;
        private String text = "Demo abspielen";
        private boolean laeuft;
        private boolean hover;

        DemoKnopf() {
            setPreferredSize(new Dimension(ZollTafel.BREITE, 58));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    klickDemo();
                }
            };
            addMouseListener(m);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth();
                g.setColor(new Color(0x0A1B26));
                g.fillRect(0, 0, w, getHeight());
                g.setColor(LINIE);
                g.fillRect(0, 0, 1, getHeight());
                g.fillRect(0, 0, w, 1);
                Color c = laeuft ? ContainerEbene.ZOLL_FARBE : KartenFarben.GRENZE_INLAND;
                RoundRectangle2D.Double k = new RoundRectangle2D.Double(14, 13, w - 28, 32, 9, 9);
                if (hover) {
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
                    g.fill(k);
                }
                g.setColor(c);
                g.setStroke(new BasicStroke(1.3f));
                g.draw(k);
                // Symbol: Dreieck (abspielen) oder Quadrat (beenden)
                double sx = 32;
                double sy = 29;
                if (laeuft) {
                    g.fill(new java.awt.geom.Rectangle2D.Double(sx - 5, sy - 5, 10, 10));
                } else {
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(sx - 4, sy - 6);
                    p.lineTo(sx + 6, sy);
                    p.lineTo(sx - 4, sy + 6);
                    p.closePath();
                    g.fill(p);
                }
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                g.setColor(KartenFarben.TEXT);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(ZollTafel.passend(fm, text, w - 80), 48, 34);
            } finally {
                g.dispose();
            }
        }
    }

    // ------------------------------------------------------------ Neuer Container

    private final class NeuKnopf extends JComponent {
        private static final long serialVersionUID = 1L;
        private boolean hover;

        NeuKnopf() {
            setPreferredSize(new Dimension(ZollTafel.BREITE, 48));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    klickNeuerContainer();
                }
            };
            addMouseListener(m);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                g.setColor(new Color(0x0A1B26));
                g.fillRect(0, 0, w, getHeight());
                Color c = KartenFarben.GRENZE_INLAND;
                RoundRectangle2D.Double k = new RoundRectangle2D.Double(14, 8, w - 28, 30, 9, 9);
                if (hover) {
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
                    g.fill(k);
                }
                g.setColor(c);
                g.setStroke(new BasicStroke(1.3f));
                g.draw(k);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                g.setColor(KartenFarben.TEXT);
                g.drawString("+  Neuer Container", 30, 28);
            } finally {
                g.dispose();
            }
        }
    }

    // ------------------------------------------------------------ Neue Stadt

    private final class StadtKnopf extends JComponent {
        private static final long serialVersionUID = 1L;
        private boolean hover;

        StadtKnopf() {
            setPreferredSize(new Dimension(ZollTafel.BREITE, 48));
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override
                public void mouseClicked(MouseEvent e) { klickNeueStadt(); }
            };
            addMouseListener(m);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                g.setColor(new Color(0x0A1B26));
                g.fillRect(0, 0, w, getHeight());
                Color c = new Color(0x63B9C1);
                RoundRectangle2D.Double k = new RoundRectangle2D.Double(14, 8, w - 28, 30, 9, 9);
                if (hover) {
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
                    g.fill(k);
                }
                g.setColor(c);
                g.setStroke(new BasicStroke(1.3f));
                g.draw(k);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                g.setColor(KartenFarben.TEXT);
                g.drawString("+  Neue Stadt", 30, 28);
            } finally {
                g.dispose();
            }
        }
    }

    // ------------------------------------------------------------ CSV

    private final class CsvKnopf extends JComponent {
        private static final long serialVersionUID = 1L;
        private final boolean export;
        private boolean hover;

        CsvKnopf(boolean export) {
            this.export = export;
            setPreferredSize(new Dimension(ZollTafel.BREITE, 42));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    Runnable r = CsvKnopf.this.export ? csvExportAktion : csvImportAktion;
                    if (r != null) { r.run(); }
                }
            };
            addMouseListener(m);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                g.setColor(new Color(0x0A1B26));
                g.fillRect(0, 0, w, getHeight());
                Color c = new Color(0x6F8791);
                RoundRectangle2D.Double k = new RoundRectangle2D.Double(14, 6, w - 28, 27, 8, 8);
                if (hover) {
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
                    g.fill(k);
                }
                g.setColor(c);
                g.setStroke(new BasicStroke(1.1f));
                g.draw(k);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                g.setColor(KartenFarben.TEXT);
                g.drawString(export ? "⇩  CSV exportieren" : "⇧  CSV importieren", 30, 24);
            } finally {
                g.dispose();
            }
        }
    }
}
