package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.ContainerEbene;
import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.model.KostenZeile;
import com.dan.logistikapp.model.Ware;
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
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JComponent;

/**
 * Seitenleiste "Kosten": Summe, Anteile (Fracht, Zoll, Standgeld) und die
 * Aufteilung nach Zonenpaar und Ware - f&uuml;r heute oder insgesamt.
 * Die Zahlen kommen aus LOG_KOSTEN_V; zusammengefasst wird hier.
 *
 * @author Dan
 */
public final class KostenTafel extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Eine Summe mit Beschriftung und Balkenfarbe. */
    public static final class Posten {
        private final String name;
        private final Color farbe;
        private BigDecimal eur = BigDecimal.ZERO;
        private int fahrten;
        private double km;

        Posten(String name, Color farbe) {
            this.name = name;
            this.farbe = farbe;
        }

        public String getName() {
            return name;
        }

        public BigDecimal getEur() {
            return eur;
        }

        public int getFahrten() {
            return fahrten;
        }

        public double getKm() {
            return km;
        }
    }

    static final Color FRACHT = new Color(0x7FD3D6);
    static final Color STANDGELD = new Color(0xE0524A);

    private static final int KOPF = 96;
    private static final int ZEILE = 40;
    private static final Color GRUND = new Color(0x0C1A23);
    private static final Color LINIE = new Color(0x1E3440);

    private List<KostenZeile> zeilen = Collections.emptyList();
    private Map<String, Color> warenFarbe = new HashMap<String, Color>();
    private boolean nurHeute;
    private int hoehe = KOPF + 200;

    private final Font fKopf = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private final Font fSumme = new Font(Font.MONOSPACED, Font.BOLD, 17);
    private final Font fKlein = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private final Font fZahl = new Font(Font.MONOSPACED, Font.BOLD, 12);
    private final Font fGruppe = new Font(Font.SANS_SERIF, Font.BOLD, 10);

    private long stichtag;

    public KostenTafel() {
        setOpaque(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                klick(e.getX(), e.getY());
            }
        });
    }

    public void setZeilen(List<KostenZeile> neu) {
        zeilen = neu == null ? Collections.<KostenZeile>emptyList() : new ArrayList<KostenZeile>(neu);
        neuMessen();
    }

    /** Warenfarben f&uuml;r die Balken. */
    public void setWaren(List<Ware> waren) {
        warenFarbe = new HashMap<String, Color>();
        for (Ware w : waren) {
            warenFarbe.put(w.getCode(), w.getFarbe());
        }
        repaint();
    }

    /** Zeitreise: "heute" ist der Tag dieses Zeitpunkts; 0 = wirklich heute. */
    public void setStichtag(long ms) {
        this.stichtag = ms;
        repaint();
    }

    public void setNurHeute(boolean b) {
        nurHeute = b;
        neuMessen();
    }

    public boolean isNurHeute() {
        return nurHeute;
    }

    public Rectangle knopfHeute() {
        return new Rectangle(16, 62, 70, 22);
    }

    public Rectangle knopfGesamt() {
        return new Rectangle(90, 62, 70, 22);
    }

    /** F&uuml;r Tests und Maus. */
    public void klick(int x, int y) {
        if (knopfHeute().contains(x, y)) {
            setNurHeute(true);
        } else if (knopfGesamt().contains(x, y)) {
            setNurHeute(false);
        }
    }

    private void neuMessen() {
        hoehe = KOPF + 30 + 3 * 22 + 30 + jeZone().size() * ZEILE + 42 + jeWare().size() * ZEILE + 20;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(ZollTafel.BREITE, hoehe);
    }

    // ------------------------------------------------------------ Rechnen

    private List<KostenZeile> gewaehlt() {
        if (!nurHeute) {
            return zeilen;
        }
        Calendar k = Calendar.getInstance();
        if (stichtag > 0) {
            k.setTimeInMillis(stichtag);
        }
        k.set(Calendar.HOUR_OF_DAY, 0);
        k.set(Calendar.MINUTE, 0);
        k.set(Calendar.SECOND, 0);
        k.set(Calendar.MILLISECOND, 0);
        long heute = k.getTimeInMillis();
        List<KostenZeile> out = new ArrayList<KostenZeile>();
        for (KostenZeile z : zeilen) {
            if (z.getTagMs() >= heute) {
                out.add(z);
            }
        }
        return out;
    }

    /** Summe des gew&auml;hlten Zeitraums. */
    public BigDecimal getSumme() {
        BigDecimal s = BigDecimal.ZERO;
        for (KostenZeile z : gewaehlt()) {
            s = s.add(z.getKostenEur());
        }
        return s;
    }

    /** Fracht, Zoll, Standgeld - in dieser Reihenfolge. */
    public List<Posten> anteile() {
        Posten f = new Posten("Fracht", FRACHT);
        Posten z = new Posten("Zoll", ContainerEbene.ZOLL_FARBE);
        Posten s = new Posten("Standgeld", STANDGELD);
        for (KostenZeile k : gewaehlt()) {
            f.eur = f.eur.add(k.getFrachtEur());
            z.eur = z.eur.add(k.getZollEur());
            s.eur = s.eur.add(k.getStandgeldEur());
            f.fahrten += k.getFahrten();
            z.fahrten += k.getZollfahrten();
        }
        List<Posten> out = new ArrayList<Posten>();
        out.add(f);
        out.add(z);
        out.add(s);
        return out;
    }

    /** Je Zonenpaar ("Inland → EU-Ausland"), teuerstes zuerst. */
    public List<Posten> jeZone() {
        Map<String, Posten> m = new LinkedHashMap<String, Posten>();
        for (KostenZeile k : gewaehlt()) {
            String name = zone(k.getVonZone()) + " → " + zone(k.getNachZone());
            Posten p = m.get(name);
            if (p == null) {
                p = new Posten(name, farbe(k.getNachZone()));
                m.put(name, p);
            }
            p.eur = p.eur.add(k.getKostenEur());
            p.fahrten += k.getFahrten();
            p.km += k.getKm();
        }
        return sortiert(m);
    }

    /** Je Ware, teuerste zuerst. */
    public List<Posten> jeWare() {
        Map<String, Posten> m = new LinkedHashMap<String, Posten>();
        for (KostenZeile k : gewaehlt()) {
            Posten p = m.get(k.getWareCode());
            if (p == null) {
                Color c = warenFarbe.get(k.getWareCode());
                p = new Posten(k.getWare(), c == null ? KartenFarben.TEXT_LEISE : c);
                m.put(k.getWareCode(), p);
            }
            p.eur = p.eur.add(k.getKostenEur());
            p.fahrten += k.getFahrten();
            p.km += k.getKm();
        }
        return sortiert(m);
    }

    private static List<Posten> sortiert(Map<String, Posten> m) {
        List<Posten> l = new ArrayList<Posten>(m.values());
        Collections.sort(l, new Comparator<Posten>() {
            @Override
            public int compare(Posten a, Posten b) {
                return b.eur.compareTo(a.eur);
            }
        });
        return l;
    }

    private static String zone(String code) {
        try {
            return Zone.vonCode(code).anzeige();
        } catch (IllegalArgumentException e) {
            return String.valueOf(code);
        }
    }

    private static Color farbe(String code) {
        try {
            return KartenFarben.hell(KartenFarben.marke(Zone.vonCode(code)));
        } catch (IllegalArgumentException e) {
            return KartenFarben.TEXT_LEISE;
        }
    }

    static String euro(BigDecimal b) {
        return String.format(Locale.GERMANY, "%,.2f €", b);
    }

    // ------------------------------------------------------------ Zeichnen

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth() > 0 ? getWidth() : ZollTafel.BREITE;
            g.setColor(GRUND);
            g.fillRect(0, 0, w, getHeight());
            g.setColor(LINIE);
            g.fillRect(0, 0, 1, getHeight());

            List<Posten> anteile = anteile();
            int fahrten = anteile.get(0).fahrten;
            BigDecimal summe = getSumme();
            g.setFont(fKopf);
            g.setColor(KartenFarben.TEXT_LEISE);
            g.drawString("KOSTEN", 16, 28);
            g.setFont(fSumme);
            g.setColor(KartenFarben.TEXT);
            String s = euro(summe);
            g.drawString(s, w - 16 - g.getFontMetrics().stringWidth(s), 30);
            g.setFont(fKlein);
            g.setColor(new Color(0x6F8791));
            g.drawString(fahrten + (fahrten == 1 ? " Fahrt" : " Fahrten") + (nurHeute ? " heute" : " insgesamt")
                    + " · Standgeld zählt ab der Freigabe", 16, 48);
            knopf(g, knopfHeute(), "Heute", nurHeute);
            knopf(g, knopfGesamt(), "Gesamt", !nurHeute);
            g.setColor(LINIE);
            g.fillRect(12, KOPF - 1, w - 24, 1);

            int y = KOPF + 22;
            y = gruppe(g, "ANTEILE", y);
            // gestapelter Balken
            double bx = 16;
            double bw = w - 32;
            double x = bx;
            if (summe.signum() > 0) {
                for (Posten p : anteile) {
                    double t = bw * p.eur.doubleValue() / summe.doubleValue();
                    g.setColor(p.farbe);
                    g.fill(new Rectangle2D.Double(x, y, Math.max(0, t - 1), 10));
                    x += t;
                }
            } else {
                g.setColor(new Color(0x13262F));
                g.fill(new RoundRectangle2D.Double(bx, y, bw, 10, 4, 4));
            }
            y += 26;
            for (Posten p : anteile) {
                g.setColor(p.farbe);
                g.fill(new RoundRectangle2D.Double(16, y - 9, 10, 10, 3, 3));
                g.setFont(fKlein);
                g.setColor(KartenFarben.TEXT);
                String pct = summe.signum() > 0
                        ? String.format(Locale.GERMANY, "  %.0f %%", 100 * p.eur.doubleValue() / summe.doubleValue()) : "";
                g.drawString(p.name + pct, 32, y);
                g.setFont(fZahl);
                String e = euro(p.eur);
                g.drawString(e, w - 16 - g.getFontMetrics().stringWidth(e), y);
                y += 22;
            }
            y += 8;
            y = liste(g, "NACH ZONEN", jeZone(), y, w);
            y += 20;
            liste(g, "NACH WARE", jeWare(), y, w);
        } finally {
            g.dispose();
        }
    }

    /** Relative Helligkeit 0..1 (sRGB, grob). */
    private static double hell(Color c) {
        return (0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue()) / 255.0;
    }

    private int gruppe(Graphics2D g, String titel, int y) {
        g.setFont(fGruppe);
        g.setColor(KartenFarben.TEXT_LEISE);
        g.drawString(titel, 16, y);
        return y + 12;
    }

    private int liste(Graphics2D g, String titel, List<Posten> l, int y, int w) {
        y = gruppe(g, titel, y);
        if (l.isEmpty()) {
            g.setFont(fKlein);
            g.setColor(KartenFarben.TEXT_LEISE);
            g.drawString("Noch keine Fahrt in diesem Zeitraum.", 16, y + 14);
            return y + 30;
        }
        BigDecimal max = l.get(0).eur.signum() > 0 ? l.get(0).eur : BigDecimal.ONE;
        for (Posten p : l) {
            g.setFont(fKlein);
            g.setColor(KartenFarben.TEXT);
            g.drawString(p.name, 16, y + 13);
            g.setFont(fZahl);
            String e = euro(p.eur);
            g.drawString(e, w - 16 - g.getFontMetrics().stringWidth(e), y + 13);
            double bw = (w - 32) * p.eur.doubleValue() / max.doubleValue();
            g.setColor(new Color(0x13262F));
            g.fill(new RoundRectangle2D.Double(16, y + 19, w - 32, 6, 3, 3));
            RoundRectangle2D.Double balken = new RoundRectangle2D.Double(16, y + 19, Math.max(2, bw), 6, 3, 3);
            g.setColor(p.farbe);
            g.fill(balken);
            if (hell(p.farbe) < 0.25) {
                // dunkle Waren (Kohle) auf dunklem Grund: heller Saum, sonst unsichtbar
                g.setColor(new Color(0x8F, 0xA7, 0xB0, 150));
                g.setStroke(new BasicStroke(1f));
                g.draw(balken);
            }
            g.setFont(fKlein);
            g.setColor(new Color(0x6F8791));
            g.drawString(String.format(Locale.GERMANY, "%d %s · %,.0f km", p.fahrten,
                    p.fahrten == 1 ? "Fahrt" : "Fahrten", p.km), 16, y + 36);
            y += ZEILE;
        }
        return y;
    }

    private void knopf(Graphics2D g, Rectangle r, String text, boolean an) {
        RoundRectangle2D.Double k = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 8, 8);
        if (an) {
            g.setColor(new Color(0x183341));
            g.fill(k);
        }
        g.setColor(an ? KartenFarben.GRENZE_INLAND : LINIE);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(k);
        g.setFont(fKopf);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(an ? KartenFarben.TEXT : KartenFarben.TEXT_LEISE);
        g.drawString(text, r.x + (r.width - fm.stringWidth(text)) / 2, r.y + 15);
    }
}
