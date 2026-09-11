package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.ContainerEbene;
import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.model.Bewegung;
import com.dan.logistikapp.model.Zone;

import java.awt.BasicStroke;
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
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JComponent;

/**
 * Seitenleiste "Fahrten": die j&uuml;ngsten Fahrten aller Container - oder,
 * nach "Historie zeigen", alle Fahrten eines Containers mit denselben
 * Nummern wie seine Spur auf der Karte.
 *
 * @author Dan
 */
public final class FahrtenTafel extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Was die Tafel ausl&ouml;sen kann. */
    public interface Aktion {
        /** Eine Zeile wurde angeklickt; im Historie-Modus ist {@code alle} die ganze Historie. */
        void zeigeFahrt(Bewegung b, List<Bewegung> alle, boolean historie);

        /** Zur&uuml;ck von der Historie zur Liste aller Fahrten. */
        void alleFahrten();
    }

    private static final int KOPF = 64;
    private static final int ZEILE = 60;

    private static final Color GRUND = new Color(0x0C1A23);
    private static final Color ZEILE_HOVER = new Color(0x13262F);
    private static final Color ZEILE_WAHL = new Color(0x183341);
    private static final Color LINIE = new Color(0x1E3440);

    private List<Bewegung> fahrten = Collections.emptyList();
    private String historieVon;
    private Map<Integer, Color> farben = new HashMap<Integer, Color>();
    private Aktion aktion;
    private int hover = -1;
    private int gewaehlt = -1;
    private boolean hoverAus;

    private final Font fKopf = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private final Font fKennung = new Font(Font.MONOSPACED, Font.BOLD, 12);
    private final Font fKlein = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private final Font fNr = new Font(Font.SANS_SERIF, Font.BOLD, 10);

    public FahrtenTafel() {
        setOpaque(true);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int z = zeileBei(e.getY());
                boolean a = historieVon != null && ausKnopf().contains(e.getPoint());
                if (z != hover || a != hoverAus) {
                    hover = z;
                    hoverAus = a;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                hoverAus = false;
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

    /** Warenfarbe je Container - die Historie selbst kennt nur die Kennung. */
    public void setFarben(Map<Integer, Color> f) {
        this.farben = f == null ? new HashMap<Integer, Color>() : new HashMap<Integer, Color>(f);
        repaint();
    }

    /** Die j&uuml;ngsten Fahrten, neueste zuerst. Beendet den Historie-Modus. */
    public void setLetzte(List<Bewegung> neu) {
        historieVon = null;
        setzeListe(neu);
    }

    /** Alle Fahrten eines Containers, &auml;lteste zuerst, nummeriert wie die Spur. */
    public void setHistorie(String kennungAnzeige, List<Bewegung> neu) {
        historieVon = kennungAnzeige;
        setzeListe(neu);
    }

    private void setzeListe(List<Bewegung> neu) {
        this.fahrten = neu == null ? Collections.<Bewegung>emptyList() : new ArrayList<Bewegung>(neu);
        gewaehlt = -1;
        revalidate();
        repaint();
    }

    public boolean istHistorie() {
        return historieVon != null;
    }

    public String getHistorieVon() {
        return historieVon;
    }

    public List<Bewegung> getFahrten() {
        return Collections.unmodifiableList(fahrten);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(ZollTafel.BREITE, KOPF + Math.max(1, fahrten.size()) * ZEILE + 16);
    }

    /** F&uuml;r Tests und Maus. */
    public void klick(int x, int y) {
        if (aktion == null) {
            return;
        }
        if (historieVon != null && ausKnopf().contains(x, y)) {
            aktion.alleFahrten();
            return;
        }
        int z = zeileBei(y);
        if (z >= 0) {
            gewaehlt = z;
            repaint();
            aktion.zeigeFahrt(fahrten.get(z), getFahrten(), historieVon != null);
        }
    }

    /** Mitte einer Zeile (f&uuml;r Tests). */
    public int zeileMitte(int i) {
        return KOPF + i * ZEILE + ZEILE / 2;
    }

    /** "alle Fahrten" im Kopf, nur im Historie-Modus. */
    public Rectangle ausKnopf() {
        return new Rectangle(getBreite() - 104, 16, 90, 22);
    }

    private int getBreite() {
        return getWidth() > 0 ? getWidth() : ZollTafel.BREITE;
    }

    private int zeileBei(int y) {
        int z = (y - KOPF) / ZEILE;
        return (y >= KOPF && z < fahrten.size()) ? z : -1;
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

            g.setFont(fKopf);
            g.setColor(KartenFarben.TEXT_LEISE);
            g.drawString(historieVon == null ? "FAHRTEN" : "HISTORIE", 16, 28);
            g.setFont(fKlein);
            g.setColor(new Color(0x6F8791));
            String unter;
            if (historieVon != null) {
                long m = 0;
                java.math.BigDecimal eur = java.math.BigDecimal.ZERO;
                for (Bewegung b : fahrten) {
                    m += b.getDistanzM();
                    eur = eur.add(b.getKostenEur());
                }
                unter = historieVon + String.format(Locale.GERMANY, " · %d Fahrten · %,.0f km", fahrten.size(),
                        m / 1000.0) + (eur.signum() > 0 ? String.format(Locale.GERMANY, " · %,.2f €", eur) : "");
            } else {
                unter = fahrten.isEmpty() ? "Noch keine Fahrt" : "Die jüngsten zuerst · Rechtsklick: Historie";
            }
            g.drawString(unter, 16, 48);
            if (historieVon != null) {
                Rectangle r = ausKnopf();
                RoundRectangle2D.Double k = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 8, 8);
                g.setColor(hoverAus ? KartenFarben.TEXT_LEISE : LINIE);
                g.setStroke(new BasicStroke(1.2f));
                g.draw(k);
                g.setFont(fKopf);
                FontMetrics fm = g.getFontMetrics();
                g.setColor(KartenFarben.TEXT);
                String t = "alle Fahrten";
                g.drawString(t, r.x + (r.width - fm.stringWidth(t)) / 2, r.y + 15);
            }
            g.setColor(LINIE);
            g.fillRect(12, KOPF - 1, w - 24, 1);

            for (int i = 0; i < fahrten.size(); i++) {
                zeichneZeile(g, fahrten.get(i), i, w);
            }
        } finally {
            g.dispose();
        }
    }

    private void zeichneZeile(Graphics2D g, Bewegung b, int i, int w) {
        int y = KOPF + i * ZEILE;
        if (i == gewaehlt) {
            g.setColor(ZEILE_WAHL);
            g.fillRect(1, y, w - 1, ZEILE);
        } else if (i == hover) {
            g.setColor(ZEILE_HOVER);
            g.fillRect(1, y, w - 1, ZEILE);
        }
        int x0 = 16;
        if (historieVon != null) {
            // Nummer wie auf der Spur
            Ellipse2D.Double k = new Ellipse2D.Double(14, y + 9, 18, 18);
            g.setColor(akzent(b));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(k);
            g.setFont(fNr);
            FontMetrics fm = g.getFontMetrics();
            String n = String.valueOf(i + 1);
            g.setColor(KartenFarben.TEXT);
            g.drawString(n, 23 - fm.stringWidth(n) / 2f, y + 22);
            x0 = 40;
        } else {
            Color c = farben.get(b.getContainerId());
            g.setColor(c == null ? KartenFarben.TEXT_LEISE : c);
            g.fill(new RoundRectangle2D.Double(16, y + 12, 16, 10, 3, 3));
            g.setColor(new Color(0, 0, 0, 110));
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(16, y + 12, 16, 10, 3, 3));
            x0 = 40;
        }
        g.setFont(fKennung);
        g.setColor(KartenFarben.TEXT);
        g.drawString(historieVon != null ? b.getVonOrt() + " → " + b.getNachOrt() : b.getKennungAnzeige(), x0, y + 22);

        String zeit = zeit(b.getZeitpunktMs());
        g.setFont(fKlein);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(KartenFarben.TEXT_LEISE);
        g.drawString(zeit, w - 14 - fm.stringWidth(zeit), y + 22);

        String z2 = (historieVon != null ? "" : b.getVonOrt() + " → " + b.getNachOrt() + " · ")
                + String.format(Locale.GERMANY, "%,.0f km", b.getDistanzM() / 1000.0);
        // Betrag rechts: Fracht + Zoll, dazu Standgeld, sobald freigegeben
        String betrag = b.getKostenEur().signum() > 0 ? String.format(Locale.GERMANY, "%,.2f €", b.getKostenEur()) : null;
        int bw = betrag == null ? 0 : fm.stringWidth(betrag) + 10;
        g.setColor(KartenFarben.TEXT_LEISE);
        g.drawString(ZollTafel.passend(fm, z2, w - x0 - 14 - bw), x0, y + 39);
        if (betrag != null) {
            g.setColor(KartenFarben.TEXT);
            g.drawString(betrag, w - 14 - fm.stringWidth(betrag), y + 39);
        }
        if (b.getStandgeldEur() != null && b.getStandgeldEur().signum() > 0) {
            String sg = String.format(Locale.GERMANY, "Standgeld %,.2f €", b.getStandgeldEur());
            g.setColor(ContainerEbene.ZOLL_FARBE);
            g.drawString(sg, w - 14 - fm.stringWidth(sg), y + 54);
        }
        String z3 = zone(b.getVonZone()) + " → " + zone(b.getNachZone()) + (b.isZoll() ? " · Zoll" : "");
        g.setColor(akzent(b));
        g.drawString(z3, x0, y + 54);
        g.setColor(LINIE);
        g.fillRect(12, y + ZEILE - 1, w - 24, 1);
    }

    private static Color akzent(Bewegung b) {
        if (b.isZoll()) {
            return ContainerEbene.ZOLL_FARBE;
        }
        try {
            return KartenFarben.hell(KartenFarben.marke(Zone.vonCode(b.getNachZone())));
        } catch (IllegalArgumentException e) {
            return KartenFarben.TEXT_LEISE;
        }
    }

    private static String zone(String code) {
        try {
            return Zone.vonCode(code).anzeige();
        } catch (IllegalArgumentException e) {
            return String.valueOf(code);
        }
    }

    /** Heute nur die Uhrzeit, sonst Datum und Uhrzeit. */
    static String zeit(long ms) {
        if (ms <= 0) {
            return "";
        }
        SimpleDateFormat tag = new SimpleDateFormat("yyyyMMdd", Locale.GERMANY);
        Date d = new Date(ms);
        boolean heute = tag.format(d).equals(tag.format(new Date()));
        return new SimpleDateFormat(heute ? "HH:mm:ss" : "dd.MM. HH:mm", Locale.GERMANY).format(d);
    }
}
