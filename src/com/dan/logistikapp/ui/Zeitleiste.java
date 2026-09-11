package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.ContainerEbene;
import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.karte.KartenPanel;
import com.dan.logistikapp.model.Zeitreise;

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
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Zeitleiste unter der Karte: ein Strich je Fahrt, Freigabe und geplantem
 * Auftrag, ein Schieber, Abspielen im Zeitraffer, „jetzt“.
 *
 * <p>Die Achse ist gestaucht: L&uuml;cken ohne Ereignis, die l&auml;nger als
 * 20 Minuten sind, schrumpfen auf eine feste Breite mit Bruchzeichen. Sonst
 * dr&auml;ngten sich die Fahrten eines Nachmittags an den Rand, weil der Aufbau
 * der Datenbank Tage zur&uuml;ckliegt. Beim Abspielen wird Leerlauf ebenso
 * &uuml;bersprungen.</p>
 *
 * <p>Zeit {@code null} hei&szlig;t: live, der heutige Stand.</p>
 *
 * @author Dan
 */
public final class Zeitleiste extends JComponent {

    private static final long serialVersionUID = 1L;

    public static final int HOEHE = 70;
    /** Zeitraffer: Sekunden Weltzeit je Sekunde. */
    public static final int[] TEMPO = {60, 600, 3600};
    /** L&uuml;cken dar&uuml;ber werden gestaucht. */
    static final long LUECKE_MS = 20 * 60 * 1000L;
    static final int LUECKE_PX = 26;
    /** So lange (echte Zeit) f&auml;hrt ein Container bei der Wiedergabe. */
    static final long FAHRT_ECHT_MS = 1300;

    private static final Color GRUND = new Color(0x0A1B26);
    private static final Color LINIE = new Color(0x1E3440);
    private static final Color FAHRT = new Color(0x8FA7B0);

    /** Meldet jeden neuen Zeitpunkt; {@code null} = zur&uuml;ck zu jetzt. */
    public interface Beobachter {
        void zeit(Long ms, long fahrtDauerMs);
    }

    /** Ein St&uuml;ck der Achse: Zeit [t0, t1] liegt auf [x0, x1]. */
    private static final class Stueck {
        final long t0;
        final long t1;
        double x0;
        double x1;
        final boolean gestaucht;

        Stueck(long t0, long t1, boolean gestaucht) {
            this.t0 = t0;
            this.t1 = t1;
            this.gestaucht = gestaucht;
        }
    }

    private Zeitreise daten;
    private Long zeit;
    private boolean spielt;
    private int tempo;
    /** Aktueller Zeitraffer; au&szlig;er den drei Stufen setzt die Demo einen eigenen. */
    private int tempoWert = TEMPO[0];
    private boolean gesperrt;
    private Beobachter beobachter;
    private final List<Stueck> achse = new ArrayList<Stueck>();
    private List<Long> wechsel = new ArrayList<Long>();
    private long anfang;
    private long ende;
    private int hoverX = -1;
    private boolean zieht;
    private int achseFuer = -1;
    private long letzterTakt;

    private final Timer takt = new Timer(40, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            long jetzt = System.currentTimeMillis();
            long d = letzterTakt == 0 ? 40 : Math.min(200, jetzt - letzterTakt);
            letzterTakt = jetzt;
            tick(d);
        }
    });

    private final Font fKlein = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private final Font fFett = new Font(Font.SANS_SERIF, Font.BOLD, 11);

    public Zeitleiste() {
        setOpaque(true);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (gesperrt || daten == null) {
                    return;
                }
                if (spur().contains(e.getX(), e.getY()) || nahAmSchieber(e.getX(), e.getY())) {
                    zieht = true;
                    pause();
                    ziehe(e.getX());
                } else {
                    klick(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (zieht) {
                    ziehe(e.getX());
                }
                hoverX = e.getX();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                zieht = false;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                hoverX = spur().contains(e.getX(), e.getY()) ? e.getX() : -1;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverX = -1;
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public void setBeobachter(Beobachter b) {
        this.beobachter = b;
    }

    /** Neue Historie. Eine laufende Reise bleibt, wo sie ist. */
    public void setDaten(Zeitreise z) {
        this.daten = z;
        this.wechsel = z == null ? new ArrayList<Long>() : z.wechsel();
        achseFuer = -1;
        repaint();
    }

    public Zeitreise getDaten() {
        return daten;
    }

    /** W&auml;hrend der Demo: keine Reise. */
    public void setGesperrt(boolean g) {
        if (g && zeit != null) {
            jetzt();
        }
        pause();
        this.gesperrt = g;
        repaint();
    }

    public boolean isGesperrt() {
        return gesperrt;
    }

    /** {@code null} = live. */
    public Long getZeit() {
        return zeit;
    }

    public boolean spielt() {
        return spielt;
    }

    public int getTempo() {
        return tempoWert;
    }

    /** Eigener Zeitraffer (die Demo spielt ihre eigenen Minuten mit x8 ab). */
    public void setTempoWert(int v) {
        tempoWert = Math.max(1, v);
        repaint();
    }

    /** Springt zu einem Zeitpunkt; {@code null} = jetzt. */
    public void setZeit(Long ms) {
        if (gesperrt || (daten == null && ms != null)) {
            return;
        }
        if (ms != null) {
            baueAchse();
            ms = Math.max(anfang, Math.min(ende, ms));
        }
        Long alt = zeit;
        zeit = ms;
        repaint();
        if (beobachter != null && !(alt == null && ms == null)) {
            beobachter.zeit(ms, spielt ? fahrtDauer() : 0);
        }
    }

    public void jetzt() {
        pause();
        setZeit(null);
    }

    public void spielen() {
        if (gesperrt || daten == null) {
            return;
        }
        baueAchse();
        if (zeit == null || zeit >= ende) {
            // von vorn: die ganze Historie im Zeitraffer
            spielt = true;
            setZeit(anfang);
        }
        spielt = true;
        letzterTakt = 0;
        takt.start();
        repaint();
    }

    public void pause() {
        spielt = false;
        takt.stop();
        repaint();
    }

    public void tempoWeiter() {
        tempo = (tempo + 1) % TEMPO.length;
        tempoWert = TEMPO[tempo];
        repaint();
    }

    /** Zum n&auml;chsten (+1) oder vorigen (-1) Ereignis - und zwar kurz danach. */
    public void schritt(int richtung) {
        if (gesperrt || daten == null) {
            return;
        }
        pause();
        baueAchse();
        long t = zeit == null ? daten.getJetztMs() : zeit;
        Long ziel = null;
        if (richtung > 0) {
            for (long w : wechsel) {
                if (w + 1 > t) {
                    ziel = w + 1;
                    break;
                }
            }
        } else {
            for (int i = wechsel.size() - 1; i >= 0; i--) {
                if (wechsel.get(i) + 1 < t) {
                    ziel = wechsel.get(i) + 1;
                    break;
                }
            }
            if (ziel == null) {
                ziel = anfang;
            }
        }
        if (ziel != null) {
            setZeit(ziel);
        }
    }

    /**
     * Ein Takt der Wiedergabe mit {@code echtMs} verstrichener echter Zeit.
     * Lange Pausen ohne Ereignis werden &uuml;bersprungen - aber erst, wenn die
     * letzte Fahrt angekommen ist.
     */
    public void tick(long echtMs) {
        if (!spielt || daten == null || zeit == null) {
            return;
        }
        long v = tempoWert;
        long t = zeit + echtMs * v;
        Long naechster = null;
        Long voriger = null;
        for (long w : wechsel) {
            if (w > zeit) {
                naechster = w;
                break;
            }
            voriger = w;
        }
        boolean angekommen = voriger == null || t - voriger >= fahrtDauer();
        if (naechster != null && angekommen && naechster - t > v * 2500) {
            t = naechster - v * 700;
        }
        long jetzt = daten.getJetztMs();
        if (naechster == null && angekommen && t < jetzt) {
            // nichts mehr bis jetzt: Wiedergabe endet im heutigen Stand
            jetzt();
            return;
        }
        if (t >= ende) {
            spielt = false;
            takt.stop();
            if (daten.getLetzteMs() <= jetzt) {
                // keine Prognose: das Ende der Achse ist nur Rand - zurueck zu jetzt
                setZeit(null);
                return;
            }
            setZeit(ende);
            return;
        }
        setZeit(t);
    }

    long fahrtDauer() {
        return FAHRT_ECHT_MS * tempoWert;
    }

    // ------------------------------------------------------------ Achse

    private void baueAchse() {
        Rectangle s = spur();
        int key = s.width * 31 + (daten == null ? 0 : daten.hashCode());
        if (key == achseFuer && !achse.isEmpty()) {
            return;
        }
        achseFuer = key;
        achse.clear();
        if (daten == null) {
            return;
        }
        long jetzt = daten.getJetztMs();
        long erste = Math.min(daten.getErsteMs(), jetzt);
        long letzte = Math.max(daten.getLetzteMs(), jetzt);
        // Rand links und rechts: ein Dreissigstel der Spanne, 5 s bis 10 min - bei der
        // Demo (anderthalb Minuten) waeren feste 60 s fast die halbe Leiste
        long spanne = letzte - erste;
        long rand = spanne <= 0 ? 60000L : Math.max(5000L, Math.min(LUECKE_MS / 2, spanne / 30));
        anfang = erste - rand;
        ende = letzte + (letzte > jetzt ? rand : Math.max(5000L, rand / 4));
        TreeSet<Long> p = new TreeSet<Long>(wechsel);
        p.add(anfang);
        p.add(jetzt);
        p.add(ende);
        List<Long> pl = new ArrayList<Long>(p.subSet(anfang, true, ende, true));
        long linear = 0;
        int gestaucht = 0;
        for (int i = 0; i + 1 < pl.size(); i++) {
            long g = pl.get(i + 1) - pl.get(i);
            boolean st = g > LUECKE_MS;
            achse.add(new Stueck(pl.get(i), pl.get(i + 1), st));
            if (st) {
                gestaucht++;
            } else {
                linear += g;
            }
        }
        double frei = Math.max(40, s.width - gestaucht * LUECKE_PX);
        double proMs = linear > 0 ? frei / linear : 0;
        double x = s.x;
        for (Stueck st : achse) {
            st.x0 = x;
            double b = st.gestaucht ? LUECKE_PX : linear > 0 ? (st.t1 - st.t0) * proMs : frei / achse.size();
            x += b;
            st.x1 = x;
        }
    }

    /** Zeitpunkt &rarr; Bildpunkt auf der Spur. */
    public double zeitZuX(long t) {
        baueAchse();
        if (achse.isEmpty()) {
            return spur().x;
        }
        if (t <= anfang) {
            return achse.get(0).x0;
        }
        for (Stueck s : achse) {
            if (t <= s.t1) {
                double f = s.t1 == s.t0 ? 0 : (t - s.t0) / (double) (s.t1 - s.t0);
                return s.x0 + f * (s.x1 - s.x0);
            }
        }
        return achse.get(achse.size() - 1).x1;
    }

    /** Bildpunkt &rarr; Zeitpunkt (Umkehrung von {@link #zeitZuX}). */
    public long xZuZeit(double x) {
        baueAchse();
        if (achse.isEmpty()) {
            return daten == null ? 0 : daten.getJetztMs();
        }
        if (x <= achse.get(0).x0) {
            return anfang;
        }
        for (Stueck s : achse) {
            if (x <= s.x1) {
                double f = s.x1 == s.x0 ? 0 : (x - s.x0) / (s.x1 - s.x0);
                return s.t0 + Math.round(f * (s.t1 - s.t0));
            }
        }
        return ende;
    }

    public long getAnfangMs() {
        baueAchse();
        return anfang;
    }

    public long getEndeMs() {
        baueAchse();
        return ende;
    }

    /** Wie viele gestauchte L&uuml;cken die Achse hat (f&uuml;r Tests). */
    public int getGestaucht() {
        baueAchse();
        int n = 0;
        for (Stueck s : achse) {
            n += s.gestaucht ? 1 : 0;
        }
        return n;
    }

    /**
     * Schieber an diese Stelle. Nah an „jetzt“ rastet er dort ein (live),
     * nah an einem Ereignis kurz dahinter.
     */
    public void ziehe(int x) {
        if (daten == null) {
            return;
        }
        double xj = zeitZuX(daten.getJetztMs());
        if (Math.abs(x - xj) <= 5 && daten.getLetzteMs() <= daten.getJetztMs()) {
            setZeit(null);
            return;
        }
        long t = xZuZeit(x);
        for (long w : wechsel) {
            if (Math.abs(zeitZuX(w) - x) <= 4) {
                t = w + 1;
                break;
            }
        }
        if (Math.abs(x - xj) <= 5) {
            t = daten.getJetztMs();
        }
        setZeit(t);
    }

    // ------------------------------------------------------------ Knoepfe

    public Rectangle knopfSpielen() {
        return new Rectangle(14, 16, 34, 34);
    }

    public Rectangle knopfZurueck() {
        return new Rectangle(54, 21, 24, 24);
    }

    public Rectangle knopfVor() {
        return new Rectangle(80, 21, 24, 24);
    }

    public Rectangle knopfTempo() {
        return new Rectangle(110, 21, 50, 24);
    }

    public Rectangle knopfJetzt() {
        return new Rectangle(breite() - 76, 21, 62, 24);
    }

    /** Die Spur mit den Strichen. */
    public Rectangle spur() {
        int x0 = 182;
        return new Rectangle(x0, 16, Math.max(80, breite() - 76 - 22 - x0), 30);
    }

    private int breite() {
        return getWidth() > 0 ? getWidth() : 1200;
    }

    private boolean nahAmSchieber(int x, int y) {
        if (zeit == null || daten == null) {
            return false;
        }
        return Math.abs(x - zeitZuX(zeit)) <= 8 && Math.abs(y - 31) <= 12;
    }

    /** F&uuml;r Tests und Maus. */
    public void klick(int x, int y) {
        if (gesperrt) {
            return;
        }
        if (knopfSpielen().contains(x, y)) {
            if (spielt) {
                pause();
            } else {
                spielen();
            }
        } else if (knopfZurueck().contains(x, y)) {
            schritt(-1);
        } else if (knopfVor().contains(x, y)) {
            schritt(1);
        } else if (knopfTempo().contains(x, y)) {
            tempoWeiter();
        } else if (knopfJetzt().contains(x, y)) {
            jetzt();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(600, HOEHE);
    }

    // ------------------------------------------------------------ Zeichnen

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setColor(GRUND);
            g.fillRect(0, 0, w, h);
            g.setColor(LINIE);
            g.fillRect(0, 0, w, 1);
            boolean reise = zeit != null;
            boolean prognose = reise && daten != null && daten.istPrognose(zeit);
            Color akzent = prognose ? KartenPanel.PROGNOSE_FARBE : KartenPanel.ZEIT_FARBE;
            float aus = gesperrt || daten == null ? 0.35f : 1f;

            // Knoepfe
            Rectangle k = knopfSpielen();
            g.setColor(new Color(akzent.getRed(), akzent.getGreen(), akzent.getBlue(), (int) (255 * aus)));
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new Ellipse2D.Double(k.x, k.y, k.width, k.height));
            double cx = k.getCenterX();
            double cy = k.getCenterY();
            if (spielt) {
                g.fill(new RoundRectangle2D.Double(cx - 6, cy - 7, 4, 14, 2, 2));
                g.fill(new RoundRectangle2D.Double(cx + 2, cy - 7, 4, 14, 2, 2));
            } else {
                Path2D.Double d = new Path2D.Double();
                d.moveTo(cx - 4, cy - 8);
                d.lineTo(cx + 8, cy);
                d.lineTo(cx - 4, cy + 8);
                d.closePath();
                g.fill(d);
            }
            zeichneSchritt(g, knopfZurueck(), -1, aus);
            zeichneSchritt(g, knopfVor(), 1, aus);
            Rectangle tk = knopfTempo();
            g.setColor(LINIE);
            g.fill(new RoundRectangle2D.Double(tk.x, tk.y, tk.width, tk.height, 12, 12));
            g.setFont(fFett);
            g.setColor(KartenFarben.TEXT);
            String ts = "×" + tempoWert;
            g.drawString(ts, tk.x + (tk.width - g.getFontMetrics().stringWidth(ts)) / 2f, tk.y + 16);
            g.setFont(fKlein);
            g.setColor(KartenFarben.TEXT_LEISE);
            g.drawString(tempoWert == 60 ? "1 min/s" : tempoWert == 600 ? "10 min/s" : tempoWert == 3600 ? "1 h/s"
                    : tempoWert + " s/s", tk.x + 2, tk.y + 38);

            Rectangle jk = knopfJetzt();
            g.setColor(reise ? akzent : LINIE);
            g.fill(new RoundRectangle2D.Double(jk.x, jk.y, jk.width, jk.height, 12, 12));
            g.setFont(fFett);
            g.setColor(reise ? GRUND : KartenFarben.TEXT_LEISE);
            g.drawString("Jetzt", jk.x + (jk.width - g.getFontMetrics().stringWidth("Jetzt")) / 2f, jk.y + 16);

            Rectangle s = spur();
            double y = s.y + 15;
            if (daten == null) {
                g.setFont(fKlein);
                g.setColor(KartenFarben.TEXT_LEISE);
                g.drawString("Zeitleiste wird geladen …", s.x, (float) y + 4);
                return;
            }
            baueAchse();
            // Grundlinie, gestauchte Luecken als Bruch
            for (Stueck st : achse) {
                if (st.gestaucht) {
                    zeichneBruch(g, st, y);
                } else {
                    g.setColor(new Color(0x2C, 0x4A, 0x57));
                    g.setStroke(new BasicStroke(2f));
                    g.draw(new Line2D.Double(st.x0, y, st.x1, y));
                }
            }
            double xj = zeitZuX(daten.getJetztMs());
            if (reise) {
                double xz = zeitZuX(zeit);
                g.setColor(new Color(akzent.getRed(), akzent.getGreen(), akzent.getBlue(), 90));
                g.fill(new Line2D.Double(Math.min(xz, xj), y - 2, Math.max(xz, xj), y + 2).getBounds2D());
            }
            // Striche
            for (Zeitreise.Ereignis e : daten.getEreignisse()) {
                double x = zeitZuX(e.getMs());
                switch (e.getArt()) {
                    case FAHRT:
                        g.setColor(FAHRT);
                        g.setStroke(new BasicStroke(1.4f));
                        g.draw(new Line2D.Double(x, y - 7, x, y + 7));
                        break;
                    case ZOLLFAHRT:
                        g.setColor(ContainerEbene.ZOLL_FARBE);
                        g.setStroke(new BasicStroke(1.8f));
                        g.draw(new Line2D.Double(x, y - 9, x, y + 7));
                        break;
                    case FREIGABE:
                        g.setColor(ContainerEbene.ZOLL_FARBE);
                        g.fill(new Ellipse2D.Double(x - 2, y + 9, 4, 4));
                        break;
                    default:
                        Path2D.Double r = new Path2D.Double();
                        r.moveTo(x, y - 7);
                        r.lineTo(x + 5, y);
                        r.lineTo(x, y + 7);
                        r.lineTo(x - 5, y);
                        r.closePath();
                        g.setColor(GRUND);
                        g.fill(r);
                        g.setColor(KartenPanel.PROGNOSE_FARBE);
                        g.setStroke(new BasicStroke(1.4f));
                        g.draw(r);
                        break;
                }
            }
            // jetzt
            g.setColor(KartenFarben.TEXT);
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new Line2D.Double(xj, s.y - 2, xj, s.y + s.height));
            g.setFont(fKlein);
            g.drawString("jetzt", (float) xj - 12, (float) s.y + s.height + 13);
            // Beschriftung links und rechts
            g.setColor(KartenFarben.TEXT_LEISE);
            g.drawString(datum(anfang), s.x, s.y + s.height + 13);
            if (ende > daten.getJetztMs() + 60000) {
                String e = datum(ende);
                g.drawString(e, s.x + s.width - g.getFontMetrics().stringWidth(e), s.y + s.height + 13);
            }
            // Schieber
            long t = reise ? zeit : daten.getJetztMs();
            double xs = zeitZuX(t);
            g.setColor(reise ? akzent : new Color(0x6F8791));
            g.fill(new Ellipse2D.Double(xs - 7, y - 7, 14, 14));
            g.setColor(GRUND);
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Double(xs - 7, y - 7, 14, 14));
            g.setFont(fFett);
            FontMetrics fm = g.getFontMetrics();
            String oben = reise ? uhr(t) : "live";
            float ox = (float) Math.max(s.x, Math.min(s.x + s.width - fm.stringWidth(oben), xs - fm.stringWidth(oben) / 2.0));
            g.setColor(reise ? akzent : KartenFarben.TEXT_LEISE);
            g.drawString(oben, ox, s.y - 3);
            if (!reise && !gesperrt) {
                g.setFont(fKlein);
                g.setColor(KartenFarben.TEXT_LEISE);
                String tip = daten.getFahrtenAnzahl() + " Fahrten · Schieber ziehen: der Stand von damals"
                        + (daten.getLetzteMs() > daten.getJetztMs() ? " · rechts von jetzt: Prognose" : "");
                float tx = (float) Math.min(s.x + s.width / 2.0 - g.getFontMetrics().stringWidth(tip) / 2.0,
                        xj - 30 - g.getFontMetrics().stringWidth(tip));
                g.drawString(tip, Math.max(s.x + 100, tx), s.y + s.height + 13);
            } else if (gesperrt) {
                g.setFont(fKlein);
                g.setColor(ContainerEbene.ZOLL_FARBE);
                g.drawString("Während der Demo ruht die Zeitreise", s.x + 110, s.y + s.height + 13);
            }
            // Ereignis unter der Maus
            if (hoverX >= 0) {
                Zeitreise.Ereignis nah = null;
                double best = 6;
                for (Zeitreise.Ereignis e : daten.getEreignisse()) {
                    double d = Math.abs(zeitZuX(e.getMs()) - hoverX);
                    if (d < best) {
                        best = d;
                        nah = e;
                    }
                }
                String txt = nah != null ? uhr(nah.getMs()) + "  " + nah.getText() : uhr(xZuZeit(hoverX));
                g.setFont(fKlein);
                fm = g.getFontMetrics();
                double bw = fm.stringWidth(txt) + 14;
                double bx = Math.max(4, Math.min(w - bw - 4, hoverX - bw / 2));
                g.setColor(KartenFarben.TAFEL);
                g.fill(new RoundRectangle2D.Double(bx, 1, bw, 15, 6, 6));
                g.setColor(KartenFarben.TEXT);
                g.drawString(txt, (float) bx + 7, 12);
            }
        } finally {
            g.dispose();
        }
    }

    private void zeichneSchritt(Graphics2D g, Rectangle r, int richtung, float aus) {
        g.setColor(new Color(0xB9, 0xCB, 0xD1, (int) (255 * aus)));
        double cx = r.getCenterX();
        double cy = r.getCenterY();
        Path2D.Double d = new Path2D.Double();
        d.moveTo(cx - 4 * richtung, cy - 6);
        d.lineTo(cx + 4 * richtung, cy);
        d.lineTo(cx - 4 * richtung, cy + 6);
        d.closePath();
        g.fill(d);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Line2D.Double(cx + 6 * richtung, cy - 6, cx + 6 * richtung, cy + 6));
    }

    private void zeichneBruch(Graphics2D g, Stueck st, double y) {
        g.setColor(new Color(0x2C, 0x4A, 0x57));
        g.setStroke(new BasicStroke(1.4f));
        double m = (st.x0 + st.x1) / 2;
        g.draw(new Line2D.Double(st.x0, y, m - 5, y));
        g.draw(new Line2D.Double(m + 5, y, st.x1, y));
        g.draw(new Line2D.Double(m - 7, y + 5, m - 3, y - 5));
        g.draw(new Line2D.Double(m + 3, y + 5, m + 7, y - 5));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        String t = dauer(st.t1 - st.t0);
        g.setColor(new Color(0x6F8791));
        g.drawString(t, (float) (m - g.getFontMetrics().stringWidth(t) / 2.0), (float) y + 16);
    }

    /** "3 h", "2 T", "45 min". */
    static String dauer(long ms) {
        long min = ms / 60000;
        if (min < 90) {
            return min + " min";
        }
        long h = Math.round(min / 60.0);
        return h < 48 ? h + " h" : Math.round(h / 24.0) + " T";
    }

    /** Uhrzeit, mit Datum, wenn nicht heute. */
    static String uhr(long ms) {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(ms);
        boolean heute = a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        return new SimpleDateFormat(heute ? "HH:mm:ss" : "dd.MM. HH:mm:ss", Locale.GERMANY).format(new Date(ms));
    }

    static String datum(long ms) {
        return new SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(new Date(ms));
    }
}
