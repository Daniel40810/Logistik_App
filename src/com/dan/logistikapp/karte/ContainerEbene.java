package com.dan.logistikapp.karte;

import com.dan.logistikapp.mesh.ContainerMesh;
import com.dan.logistikapp.mesh.ContainerRenderer;
import com.dan.logistikapp.mesh.ContainerSprite;
import com.dan.logistikapp.mesh.SpriteCache;
import com.dan.logistikapp.model.ContainerInfo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Die Container auf der Karte: je Stadt ein kleiner Stellplatz unter dem
 * Stadtpunkt, Reihen zu h&ouml;chstens drei TEU, hinten beginnend, damit vordere
 * Container die hinteren &uuml;berdecken.
 *
 * <p>Die Bilder kommen aus dem {@link SpriteCache}. Gezeichnet wird hier nur
 * noch skaliert und versetzt - das Rendern mit RayPhong passiert einmal je
 * Warenfarbe und Bauart.</p>
 *
 * @author Dan
 */
public final class ContainerEbene {

    /** Entscheidet, welche Container auf der Karte sichtbar sind. */
    public interface Filter {
        boolean zeigt(ContainerInfo container);
    }

    /** Bildpunkte je Meter bei Faktor 1: ein 20-Fu&szlig;-Container ist 26 Punkte lang. */
    public static final double BASIS_PPM = 26 / ContainerMesh.LAENGE_20;
    /** Reihenbreite eines Stellplatzes in TEU. */
    public static final int REIHE_TEU = 3;

    private static final double EL = Math.toRadians(ContainerRenderer.ERHEBUNG_GRAD);

    /** Abgelegt in doppelter Aufl&ouml;sung, damit Hineinzoomen scharf bleibt. */
    private final SpriteCache cache = new SpriteCache(2 * BASIS_PPM);
    private final List<Platz> plaetze = new ArrayList<Platz>();

    /** Wo ein Container beim letzten Zeichnen stand. */
    public static final class Platz {
        private final ContainerInfo container;
        private final double ankerX;
        private final double ankerY;
        private final Rectangle2D bild;

        Platz(ContainerInfo container, double ankerX, double ankerY, Rectangle2D bild) {
            this.container = container;
            this.ankerX = ankerX;
            this.ankerY = ankerY;
            this.bild = bild;
        }

        public ContainerInfo getContainer() {
            return container;
        }

        /** Mitte der Grundfl&auml;che in Bildpunkten. */
        public double getAnkerX() {
            return ankerX;
        }

        public double getAnkerY() {
            return ankerY;
        }

        public Rectangle2D getBild() {
            return bild;
        }
    }

    public SpriteCache getCache() {
        return cache;
    }

    /** Rendert alle ben&ouml;tigten Bilder vorab - gedacht f&uuml;r einen Hintergrundfaden. */
    public void vorwaermen(Collection<ContainerInfo> alle) {
        for (ContainerInfo c : alle) {
            cache.get(c.getFarbe(), c.getGroesseFuss(), c.isReefer(), 0);
        }
    }

    /** Ergebnis des letzten Zeichnens, von hinten nach vorn. */
    public List<Platz> getPlaetze() {
        return plaetze;
    }

    /**
     * Legt die Stellpl&auml;tze an und zeichnet.
     *
     * @param faktor Gr&ouml;&szlig;e der Container relativ zur Europa-Ansicht
     */
    public void zeichne(Graphics2D g2, KartenModell modell, KartenAnsicht ansicht,
            double faktor, ContainerInfo hover) {
        zeichne(g2, modell, ansicht, faktor, hover, -1, -1);
    }

    /**
     * Wie oben; zus&auml;tzlich bleibt der Platz des Containers {@code ausgeblendet}
     * frei (er ist gerade unterwegs und wird woanders gezeichnet), und der
     * Platz von {@code geist} zeigt nur einen Umriss - dahin federt er zur&uuml;ck.
     */
    public void zeichne(Graphics2D g2, KartenModell modell, KartenAnsicht ansicht,
            double faktor, ContainerInfo hover, int ausgeblendet, int geist) {
        zeichne(g2, modell, ansicht, faktor, hover, ausgeblendet, geist, null);
    }

    /** Wie oben, mit optionalem Kartenfilter. */
    public void zeichne(Graphics2D g2, KartenModell modell, KartenAnsicht ansicht,
            double faktor, ContainerInfo hover, int ausgeblendet, int geist, Filter filter) {
        plaetze.clear();
        Map<Integer, List<ContainerInfo>> jeOrt = new LinkedHashMap<Integer, List<ContainerInfo>>();
        Rectangle2D sicht = ansicht.weltRahmen();
        // Vor dem Gruppieren verwerfen: bei einem Nahzoom müssen tausende
        // Container außerhalb des Ausschnitts weder sortiert noch gezeichnet werden.
        Set<Integer> sichtbareOrte = new HashSet<Integer>();
        for (KartenModell.OrtPunkt p : modell.getPunkte()) {
            if (sicht.contains(p.getX(), p.getY())) {
                sichtbareOrte.add(p.getOrt().getOrtId());
            }
        }
        for (ContainerInfo c : modell.getContainer()) {
            if (filter != null && !filter.zeigt(c)) {
                continue;
            }
            if (!sichtbareOrte.contains(c.getOrtId())) {
                continue;
            }
            List<ContainerInfo> l = jeOrt.get(c.getOrtId());
            if (l == null) {
                l = new ArrayList<ContainerInfo>();
                jeOrt.put(c.getOrtId(), l);
            }
            l.add(c);
        }
        double ppm = BASIS_PPM * faktor;
        double luecke = 2.5 * faktor;
        double tiefe = ContainerMesh.BREITE * Math.sin(EL) * ppm;
        double hoch = ContainerMesh.HOEHE * Math.cos(EL) * ppm;
        double reihenAbstand = tiefe + hoch + 3 * faktor;

        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        try {
            for (Map.Entry<Integer, List<ContainerInfo>> e : jeOrt.entrySet()) {
                KartenModell.OrtPunkt p = modell.punkt(e.getKey());
                if (p == null) {
                    continue;
                }
                Point2D.Double s = ansicht.zuBildschirm(p.getX(), p.getY());
                List<List<ContainerInfo>> reihen = reihen(e.getValue());
                double y0 = s.y + 11 + tiefe / 2 + hoch;
                for (int r = 0; r < reihen.size(); r++) {
                    List<ContainerInfo> reihe = reihen.get(r);
                    double breite = -luecke;
                    for (ContainerInfo c : reihe) {
                        breite += ContainerMesh.laenge(c.getGroesseFuss()) * ppm + luecke;
                    }
                    double x = s.x - breite / 2;
                    double ay = y0 + r * reihenAbstand;
                    for (ContainerInfo c : reihe) {
                        double len = ContainerMesh.laenge(c.getGroesseFuss()) * ppm;
                        double ax = x + len / 2;
                        int id = c.getContainerId();
                        if (id == ausgeblendet || id == geist) {
                            Rectangle2D k = koerper(ax, ay, len, tiefe, ppm);
                            if (id == geist) {
                                g.setColor(new Color(0xE6, 0xEE, 0xF1, 110));
                                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                                        10f, new float[] {3f, 3f}, 0f));
                                g.draw(new RoundRectangle2D.Double(k.getX(), k.getY(), k.getWidth(), k.getHeight(), 4, 4));
                            }
                            plaetze.add(new Platz(c, ax, ay, k));
                        } else {
                            plaetze.add(zeichneEinen(g, c, ax, ay, ppm, len, tiefe, faktor, c == hover));
                        }
                        x += len + luecke;
                    }
                }
            }
        } finally {
            g.dispose();
        }
    }

    /** Packt die Container einer Stadt in Reihen zu h&ouml;chstens {@link #REIHE_TEU} TEU. */
    static List<List<ContainerInfo>> reihen(List<ContainerInfo> alle) {
        List<List<ContainerInfo>> out = new ArrayList<List<ContainerInfo>>();
        List<ContainerInfo> reihe = new ArrayList<ContainerInfo>();
        int teu = 0;
        for (ContainerInfo c : alle) {
            if (teu + c.getTeu() > REIHE_TEU && !reihe.isEmpty()) {
                out.add(reihe);
                reihe = new ArrayList<ContainerInfo>();
                teu = 0;
            }
            reihe.add(c);
            teu += c.getTeu();
        }
        if (!reihe.isEmpty()) {
            out.add(reihe);
        }
        return out;
    }

    private Platz zeichneEinen(Graphics2D g, ContainerInfo c, double ax, double ay, double ppm,
            double len, double tiefe, double faktor, boolean hover) {
        // Weicher Schatten nach Suedosten - das Licht kommt aus Nordwesten
        double sx = ax + 2.2 * faktor;
        double sy = ay + 1.8 * faktor;
        g.setColor(new Color(0, 0, 0, 38));
        g.fill(new RoundRectangle2D.Double(sx - len / 2 - 2, sy - tiefe / 2 - 2, len + 4, tiefe + 4, 6, 6));
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Double(sx - len / 2, sy - tiefe / 2, len, tiefe, 3, 3));

        ContainerSprite sp = cache.get(c.getFarbe(), c.getGroesseFuss(), c.isReefer(), 0);
        double skala = ppm / sp.getPixelProMeter();
        AffineTransform at = new AffineTransform();
        at.translate(ax - sp.getAnkerX() * skala, ay - sp.getAnkerY() * skala);
        at.scale(skala, skala);
        g.drawImage(sp.getBild(), at, null);
        Rectangle2D koerper = koerper(ax, ay, len, tiefe, ppm);
        if ("ZOLL".equals(c.getStatus())) {
            zollMarke(g, koerper.getMaxX() - 2 * faktor, koerper.getY() + 1 * faktor, faktor);
        }
        if (hover) {
            g.setColor(new Color(0xE6, 0xEE, 0xF1, 210));
            g.setStroke(new BasicStroke(1.3f));
            g.draw(new RoundRectangle2D.Double(koerper.getX() - 2.5, koerper.getY() - 2.5,
                    koerper.getWidth() + 5, koerper.getHeight() + 5, 6, 6));
        }
        return new Platz(c, ax, ay, koerper);
    }

    /** Bernsteinfarbenes "Z" oben rechts: dieser Container wartet beim Zoll. */
    static void zollMarke(Graphics2D g, double x, double y, double faktor) {
        double r = 5.5 * Math.max(1, faktor * 0.9);
        java.awt.geom.Ellipse2D.Double k = new java.awt.geom.Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
        g.setColor(new Color(0x0A, 0x1B, 0x26, 230));
        g.setStroke(new BasicStroke(2.5f));
        g.draw(k);
        g.setColor(ZOLL_FARBE);
        g.fill(k);
        g.setColor(new Color(0x1A1206));
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, (int) Math.round(r * 1.5)));
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString("Z", (float) (x - fm.stringWidth("Z") / 2.0), (float) (y + fm.getAscent() / 2.0 - 1));
    }

    /** Signalfarbe f&uuml;r alles, was mit Zoll zu tun hat. */
    public static final Color ZOLL_FARBE = new Color(0xE8A33C);

    /**
     * Ein einzelner Container au&szlig;erhalb des Stellplatzes: beim Ziehen, beim
     * Zur&uuml;ckfedern und auf der Fahrt.
     *
     * @param ax       Bodenpunkt in Bildpunkten (dorthin f&auml;llt der Schatten)
     * @param hub      wie hoch er &uuml;ber dem Boden schwebt, in Bildpunkten
     * @param gierGrad Drehung um die Hochachse, siehe {@link ContainerRenderer}
     */
    public void zeichneFrei(Graphics2D g2, ContainerInfo c, double ax, double ay, double faktor,
            double hub, double gierGrad) {
        Graphics2D g = (Graphics2D) g2.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double gross = 1 + Math.min(hub, 14) / 90.0;
            double ppm = BASIS_PPM * faktor * gross;
            double len = ContainerMesh.laenge(c.getGroesseFuss()) * ppm;
            // Schatten: je hoeher, desto groesser, blasser und weiter nach Suedosten
            double weich = 2 + hub * 0.5;
            double sx = ax + 2.2 * faktor + hub * 0.35;
            double sy = ay + 1.8 * faktor + hub * 0.25;
            double sr = Math.max(len, ContainerMesh.BREITE * ppm) * 0.62;
            g.setColor(new Color(0, 0, 0, (int) Math.max(18, 60 - hub * 2)));
            g.fill(new java.awt.geom.Ellipse2D.Double(sx - sr - weich, sy - sr * 0.42 - weich,
                    2 * (sr + weich), 2 * (sr * 0.42 + weich)));
            ContainerSprite sp = cache.get(c.getFarbe(), c.getGroesseFuss(), c.isReefer(), SpriteCache.stufe(gierGrad));
            double skala = ppm / sp.getPixelProMeter();
            AffineTransform at = new AffineTransform();
            at.translate(ax - sp.getAnkerX() * skala, ay - hub - sp.getAnkerY() * skala);
            at.scale(skala, skala);
            g.drawImage(sp.getBild(), at, null);
            if ("ZOLL".equals(c.getStatus())) {
                zollMarke(g, ax + len * 0.4, ay - hub - ContainerMesh.HOEHE * Math.cos(EL) * ppm, faktor);
            }
        } finally {
            g.dispose();
        }
    }

    /** Platz eines Containers beim letzten Zeichnen, oder {@code null}. */
    public Platz platz(int containerId) {
        for (Platz p : plaetze) {
            if (p.container.getContainerId() == containerId) {
                return p;
            }
        }
        return null;
    }

    /** Rendert alle 16 Drehstufen vorab - f&uuml;r ruckelfreies Ziehen. Hintergrundfaden. */
    public void vorwaermenAlleStufen(Collection<ContainerInfo> alle) {
        for (ContainerInfo c : alle) {
            for (int s = 0; s < SpriteCache.STUFEN; s++) {
                cache.get(c.getFarbe(), c.getGroesseFuss(), c.isReefer(), s);
            }
        }
    }

    /** Sichtbarer K&ouml;rper eines ungedrehten Containers: Dach plus Seite. */
    private static Rectangle2D koerper(double ax, double ay, double len, double tiefe, double ppm) {
        double hoch = ContainerMesh.HOEHE * Math.cos(EL) * ppm;
        return new Rectangle2D.Double(ax - len / 2, ay - tiefe / 2 - hoch, len, tiefe + hoch);
    }

    /** Der oberste Container unter dem Punkt, oder {@code null}. */
    public ContainerInfo treffer(double px, double py) {
        for (int i = plaetze.size() - 1; i >= 0; i--) {
            if (plaetze.get(i).bild.contains(px, py)) {
                return plaetze.get(i).container;
            }
        }
        return null;
    }
}
