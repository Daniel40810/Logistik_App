package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.ContainerEbene;
import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.model.Auftrag;

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
import java.awt.geom.RoundRectangle2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * Seitenleiste "Auftr&auml;ge": oben die offenen mit Countdown, Grund bei
 * Blockade und Knopf zum Stornieren; darunter die zuletzt abgeschlossenen
 * mit dem, der sie gefahren hat (App oder Job).
 *
 * @author Dan
 */
public final class AuftragsTafel extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Was die Tafel ausl&ouml;sen kann. */
    public interface Aktion {
        void stornieren(long auftragId);

        void zeigen(int containerId);
    }

    private static final int KOPF = 64;
    private static final int OFFEN = 64;
    private static final int FERTIG = 44;
    private static final int GRUPPE = 30;

    private static final Color GRUND = new Color(0x0C1A23);
    private static final Color ZEILE_HOVER = new Color(0x13262F);
    private static final Color LINIE = new Color(0x1E3440);
    private static final Color ROT = new Color(0xE0524A);

    private List<Auftrag> offen = Collections.emptyList();
    private List<Auftrag> fertig = Collections.emptyList();
    private Aktion aktion;
    private int hover = -1;
    private boolean hoverKnopf;

    private final Font fKopf = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private final Font fKennung = new Font(Font.MONOSPACED, Font.BOLD, 12);
    private final Font fKlein = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private final Font fZeit = new Font(Font.MONOSPACED, Font.BOLD, 12);
    private final Font fGruppe = new Font(Font.SANS_SERIF, Font.BOLD, 10);

    public AuftragsTafel() {
        setOpaque(true);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int z = zeileBei(e.getY());
                boolean k = z >= 0 && z < offen.size() && stornoKnopf(z).contains(e.getPoint());
                if (z != hover || k != hoverKnopf) {
                    hover = z;
                    hoverKnopf = k;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                hoverKnopf = false;
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

    public void setAuftraege(List<Auftrag> alle) {
        List<Auftrag> o = new ArrayList<Auftrag>();
        List<Auftrag> f = new ArrayList<Auftrag>();
        if (alle != null) {
            for (Auftrag a : alle) {
                (a.istOffen() ? o : f).add(a);
            }
        }
        offen = o;
        fertig = f;
        revalidate();
        repaint();
    }

    public List<Auftrag> getOffen() {
        return Collections.unmodifiableList(offen);
    }

    public List<Auftrag> getFertig() {
        return Collections.unmodifiableList(fertig);
    }

    /** Blockiert: offen, mindestens ein Versuch, Grund steht dran. */
    public int blockiert() {
        int n = 0;
        for (Auftrag a : offen) {
            n += a.getGrund() != null && a.getVersuche() > 0 ? 1 : 0;
        }
        return n;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(ZollTafel.BREITE, KOPF + Math.max(1, offen.size()) * OFFEN + GRUPPE
                + Math.max(1, fertig.size()) * FERTIG + 16);
    }

    // ------------------------------------------------------------ Klicks

    /** F&uuml;r Tests und Maus. */
    public void klick(int x, int y) {
        if (aktion == null) {
            return;
        }
        int z = zeileBei(y);
        if (z < 0) {
            return;
        }
        if (z < offen.size()) {
            if (stornoKnopf(z).contains(x, y)) {
                aktion.stornieren(offen.get(z).getAuftragId());
            } else {
                aktion.zeigen(offen.get(z).getContainerId());
            }
        } else {
            aktion.zeigen(fertig.get(z - offen.size()).getContainerId());
        }
    }

    /** Stornieren-Knopf einer offenen Zeile. */
    public Rectangle stornoKnopf(int zeile) {
        int y = KOPF + zeile * OFFEN;
        return new Rectangle(getBreite() - 96, y + 34, 82, 22);
    }

    /** Mitte einer Zeile: erst die offenen, dann die abgeschlossenen (f&uuml;r Tests). */
    public int zeileMitte(int i) {
        return i < offen.size() ? KOPF + i * OFFEN + OFFEN / 2 : startFertig() + (i - offen.size()) * FERTIG + FERTIG / 2;
    }

    private int startFertig() {
        return KOPF + Math.max(1, offen.size()) * OFFEN + GRUPPE;
    }

    private int zeileBei(int y) {
        if (y >= KOPF && y < KOPF + offen.size() * OFFEN) {
            return (y - KOPF) / OFFEN;
        }
        int f = startFertig();
        if (y >= f && y < f + fertig.size() * FERTIG) {
            return offen.size() + (y - f) / FERTIG;
        }
        return -1;
    }

    private int getBreite() {
        return getWidth() > 0 ? getWidth() : ZollTafel.BREITE;
    }

    // ------------------------------------------------------------ Zeichnen

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getBreite();
            g.setColor(GRUND);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(LINIE);
            g.fillRect(0, 0, 1, getHeight());

            g.setFont(fKopf);
            g.setColor(KartenFarben.TEXT_LEISE);
            g.drawString("AUFTRÄGE", 16, 28);
            g.setFont(fKlein);
            g.setColor(new Color(0x6F8791));
            int b = blockiert();
            g.drawString(offen.isEmpty() ? "Rechtsklick auf einen Container: Auftrag planen"
                    : offen.size() + " offen" + (b > 0 ? " · " + b + " blockiert" : "")
                    + " · fällige fährt die Karte selbst", 16, 48);
            g.setColor(LINIE);
            g.fillRect(12, KOPF - 1, w - 24, 1);

            long jetzt = System.currentTimeMillis();
            if (offen.isEmpty()) {
                g.setFont(fKlein);
                g.setColor(KartenFarben.TEXT_LEISE);
                g.drawString("Kein Auftrag geplant.  Shift + Ziehen plant statt zu fahren.", 16, KOPF + 34);
            }
            for (int i = 0; i < offen.size(); i++) {
                zeichneOffen(g, offen.get(i), i, w, jetzt);
            }
            int y = startFertig() - 10;
            g.setFont(fGruppe);
            g.setColor(KartenFarben.TEXT_LEISE);
            g.drawString("ABGESCHLOSSEN", 16, y);
            if (fertig.isEmpty()) {
                g.setFont(fKlein);
                g.drawString("Noch keiner.", 16, y + 28);
            }
            for (int i = 0; i < fertig.size(); i++) {
                zeichneFertig(g, fertig.get(i), offen.size() + i, startFertig() + i * FERTIG, w);
            }
        } finally {
            g.dispose();
        }
    }

    private void chip(Graphics2D g, Color c, int y) {
        g.setColor(c);
        g.fill(new RoundRectangle2D.Double(16, y, 16, 10, 3, 3));
        g.setColor(new Color(0, 0, 0, 110));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(16, y, 16, 10, 3, 3));
    }

    private void zeichneOffen(Graphics2D g, Auftrag a, int i, int w, long jetzt) {
        int y = KOPF + i * OFFEN;
        if (i == hover && !hoverKnopf) {
            g.setColor(ZEILE_HOVER);
            g.fillRect(1, y, w - 1, OFFEN);
        }
        boolean blockiert = a.getGrund() != null && a.getVersuche() > 0;
        chip(g, a.getFarbe(), y + 14);
        g.setFont(fKennung);
        g.setColor(KartenFarben.TEXT);
        g.drawString(a.getKennungAnzeige(), 42, y + 24);
        // Countdown rechts oben
        String t = blockiert ? "blockiert" : a.getRang() > 1 ? "wartet" : countdown(a.getFaelligMs() - jetzt);
        g.setFont(fZeit);
        g.setColor(blockiert ? ContainerEbene.ZOLL_FARBE : a.getFaelligMs() <= jetzt ? KartenFarben.GRENZE_INLAND
                : KartenFarben.TEXT);
        g.drawString(t, w - 14 - g.getFontMetrics().stringWidth(t), y + 24);
        Rectangle k = stornoKnopf(i);
        g.setFont(fKlein);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(KartenFarben.TEXT_LEISE);
        g.drawString(ZollTafel.passend(fm, a.getOrtJetzt() + " → " + a.getNachOrt() + " · "
                + zeit(a.getFaelligMs()), k.x - 22), 16, y + 42);
        String z3;
        Color c3;
        if (blockiert) {
            z3 = a.getGrund() + (a.getVersuche() > 1 ? " (" + a.getVersuche() + " Versuche)" : "");
            c3 = ContainerEbene.ZOLL_FARBE;
        } else if (a.getRang() > 1) {
            z3 = "nach seinem früheren Auftrag";
            c3 = KartenFarben.TEXT_LEISE;
        } else {
            z3 = "#" + a.getAuftragId() + (a.getFaelligMs() <= jetzt ? " · fällig" : "");
            c3 = new Color(0x6F8791);
        }
        g.setColor(c3);
        g.drawString(ZollTafel.passend(fm, z3, k.x - 22), 16, y + 57);
        knopf(g, k, "Stornieren", i == hover && hoverKnopf);
        g.setColor(LINIE);
        g.fillRect(12, y + OFFEN - 1, w - 24, 1);
    }

    private void zeichneFertig(Graphics2D g, Auftrag a, int index, int y, int w) {
        if (index == hover) {
            g.setColor(ZEILE_HOVER);
            g.fillRect(1, y, w - 1, FERTIG);
        }
        chip(g, a.getFarbe(), y + 12);
        g.setFont(fKennung);
        g.setColor(KartenFarben.TEXT_LEISE);
        g.drawString(a.getKennungAnzeige() + " → " + a.getNachOrt(), 42, y + 22);
        g.setFont(fKlein);
        String st;
        Color c;
        switch (a.getStatus()) {
            case ERLEDIGT:
                st = a.getBewegungId() > 0 ? "gefahren" : "stand schon dort";
                c = KartenFarben.GRENZE_INLAND;
                break;
            case STORNIERT:
                st = "storniert";
                c = KartenFarben.TEXT_LEISE;
                break;
            default:
                st = "gescheitert" + (a.getGrund() != null ? ": " + a.getGrund() : "");
                c = ROT;
                break;
        }
        String von = a.getAusgefuehrtVon() == null ? "" : " · " + ("JOB".equals(a.getAusgefuehrtVon())
                ? "vom Job" : "von der App");
        g.setColor(c);
        g.drawString(ZollTafel.passend(g.getFontMetrics(), st + von + " · " + zeit(a.getErledigtMs()), w - 60), 42,
                y + 37);
        g.setColor(LINIE);
        g.fillRect(12, y + FERTIG - 1, w - 24, 1);
    }

    private void knopf(Graphics2D g, Rectangle r, String text, boolean hover) {
        RoundRectangle2D.Double k = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 8, 8);
        if (hover) {
            g.setColor(new Color(0x183341));
            g.fill(k);
        }
        g.setColor(hover ? KartenFarben.TEXT_LEISE : LINIE);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(k);
        g.setFont(fKopf);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(KartenFarben.TEXT);
        g.drawString(text, r.x + (r.width - fm.stringWidth(text)) / 2, r.y + 15);
    }

    /** "in 4:32", "fällig". */
    static String countdown(long ms) {
        if (ms <= 0) {
            return "fällig";
        }
        long s = (ms + 999) / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sek = s % 60;
        return h > 0 ? String.format(Locale.GERMANY, "in %d:%02d:%02d", h, m, sek)
                : String.format(Locale.GERMANY, "in %d:%02d", m, sek);
    }

    static String zeit(long ms) {
        return ms <= 0 ? "" : FahrtenTafel.zeit(ms);
    }

    static String uhr(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(ms));
    }
}
