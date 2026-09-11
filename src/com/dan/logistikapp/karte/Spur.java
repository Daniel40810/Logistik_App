package com.dan.logistikapp.karte;

import com.dan.logistikapp.geo.Geodaesie;
import com.dan.logistikapp.geo.Laea3035;
import com.dan.logistikapp.model.Bewegung;
import com.dan.logistikapp.model.Zone;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Die Spur eines Containers: jede Fahrt seiner Historie als Gro&szlig;kreis,
 * nummeriert, mit Pfeil in Fahrtrichtung. &Auml;ltere Fahrten treten zur&uuml;ck,
 * die letzte ist kr&auml;ftig. Farbe nach der Zone am Ziel, Bernstein bei Zoll.
 *
 * <p>Die Routen werden einmal in Weltkoordinaten (EPSG:3035) gerechnet;
 * beim Zeichnen wird nur noch abgebildet.</p>
 *
 * @author Dan
 */
final class Spur {

    private final List<Bewegung> fahrten;
    private final List<double[][]> welt = new ArrayList<double[][]>();
    private final Rectangle2D rahmen;

    Spur(KartenModell m, List<Bewegung> fahrten) {
        this.fahrten = Collections.unmodifiableList(new ArrayList<Bewegung>(fahrten));
        Rectangle2D r = null;
        for (Bewegung b : this.fahrten) {
            KartenModell.OrtPunkt a = m.punkt(b.getVonOrtId());
            KartenModell.OrtPunkt z = m.punkt(b.getNachOrtId());
            if (a == null || z == null) {
                welt.add(null);
                continue;
            }
            double[][] ll = Geodaesie.grosskreis(a.getOrt().getLaenge(), a.getOrt().getBreite(),
                    z.getOrt().getLaenge(), z.getOrt().getBreite(), 48);
            double[][] xy = new double[ll.length][];
            for (int i = 0; i < ll.length; i++) {
                xy[i] = Laea3035.vor(ll[i][0], ll[i][1]);
                if (r == null) {
                    r = new Rectangle2D.Double(xy[i][0], xy[i][1], 0, 0);
                } else {
                    r.add(xy[i][0], xy[i][1]);
                }
            }
            welt.add(xy);
        }
        this.rahmen = r;
    }

    List<Bewegung> getFahrten() {
        return fahrten;
    }

    /** Weltrahmen aller Strecken, oder {@code null} ohne Fahrten. */
    Rectangle2D getRahmen() {
        return rahmen;
    }

    boolean istLeer() {
        return fahrten.isEmpty();
    }

    void zeichne(Graphics2D g, KartenAnsicht an) {
        int n = fahrten.size();
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, 10);
        for (int k = 0; k < n; k++) {
            double[][] xy = welt.get(k);
            if (xy == null) {
                continue;
            }
            Bewegung b = fahrten.get(k);
            float deck = n == 1 ? 1f : (float) (0.4 + 0.6 * k / (n - 1.0));
            Color c = farbe(b);
            Path2D.Double p = new Path2D.Double();
            for (int i = 0; i < xy.length; i++) {
                Point2D.Double s = an.zuBildschirm(xy[i][0], xy[i][1]);
                if (i == 0) {
                    p.moveTo(s.x, s.y);
                } else {
                    p.lineTo(s.x, s.y);
                }
            }
            g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0x0A, 0x1B, 0x26, (int) (170 * deck)));
            g.draw(p);
            g.setStroke(new BasicStroke(k == n - 1 ? 3f : 2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (255 * deck)));
            g.draw(p);

            // Pfeil kurz hinter der Mitte, in Fahrtrichtung
            int m = (int) (xy.length * 0.58);
            Point2D.Double s0 = an.zuBildschirm(xy[m - 1][0], xy[m - 1][1]);
            Point2D.Double s1 = an.zuBildschirm(xy[m][0], xy[m][1]);
            double w = Math.atan2(s1.y - s0.y, s1.x - s0.x);
            Path2D.Double pf = new Path2D.Double();
            pf.moveTo(s1.x + 8 * Math.cos(w), s1.y + 8 * Math.sin(w));
            pf.lineTo(s1.x + 6 * Math.cos(w + 2.5), s1.y + 6 * Math.sin(w + 2.5));
            pf.lineTo(s1.x + 6 * Math.cos(w - 2.5), s1.y + 6 * Math.sin(w - 2.5));
            pf.closePath();
            g.setStroke(new BasicStroke(2f));
            g.setColor(KartenFarben.HALO);
            g.draw(pf);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (255 * deck)));
            g.fill(pf);

            // Nummer an der Mitte der Strecke
            int h = xy.length / 2 - 4;
            Point2D.Double sm = an.zuBildschirm(xy[h][0], xy[h][1]);
            String t = String.valueOf(k + 1);
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            double r = 8;
            Ellipse2D.Double kreis = new Ellipse2D.Double(sm.x - r, sm.y - r, 2 * r, 2 * r);
            g.setColor(KartenFarben.TAFEL);
            g.fill(kreis);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (255 * deck)));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(kreis);
            g.setColor(KartenFarben.TEXT);
            g.drawString(t, (float) (sm.x - fm.stringWidth(t) / 2.0), (float) (sm.y + fm.getAscent() / 2.0 - 1));
        }
        // Ausgangspunkt der ersten Fahrt: weisser Ring
        if (n > 0 && welt.get(0) != null) {
            Point2D.Double s = an.zuBildschirm(welt.get(0)[0][0], welt.get(0)[0][1]);
            g.setStroke(new BasicStroke(4f));
            g.setColor(KartenFarben.HALO);
            g.draw(new Ellipse2D.Double(s.x - 12, s.y - 12, 24, 24));
            g.setStroke(new BasicStroke(2f));
            g.setColor(KartenFarben.TEXT);
            g.draw(new Ellipse2D.Double(s.x - 12, s.y - 12, 24, 24));
        }
    }

    static Color farbe(Bewegung b) {
        if (b.isZoll()) {
            return ContainerEbene.ZOLL_FARBE;
        }
        try {
            return KartenFarben.hell(KartenFarben.marke(Zone.vonCode(b.getNachZone())));
        } catch (IllegalArgumentException e) {
            return KartenFarben.TEXT_LEISE;
        }
    }
}
