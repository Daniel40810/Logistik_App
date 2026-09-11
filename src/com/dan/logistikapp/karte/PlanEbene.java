package com.dan.logistikapp.karte;

import com.dan.logistikapp.geo.Geodaesie;
import com.dan.logistikapp.geo.Laea3035;
import com.dan.logistikapp.model.Auftrag;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Geplante Fahrten als Vorschau: je Container die Kette seiner offenen
 * Auftr&auml;ge, vom heutigen Standort &uuml;ber jedes Ziel, fein gestrichelt.
 * Am Ziel eine kleine Uhr mit dem Countdown; ein blockierter Auftrag
 * (beim Zoll, Ziel voll) tr&auml;gt ein Ausrufezeichen in Bernstein.
 *
 * @author Dan
 */
final class PlanEbene {

    private static final class Strecke {
        final Auftrag auftrag;
        final double[][] welt;

        Strecke(Auftrag a, double[][] w) {
            auftrag = a;
            welt = w;
        }
    }

    private final List<Strecke> strecken = new ArrayList<Strecke>();

    PlanEbene(KartenModell m, List<Auftrag> auftraege) {
        // offene Auftraege je Container in Rangfolge; die Liste kommt schon nach Termin sortiert
        Map<Integer, List<Auftrag>> je = new LinkedHashMap<Integer, List<Auftrag>>();
        for (Auftrag a : auftraege) {
            if (!a.istOffen()) {
                continue;
            }
            List<Auftrag> l = je.get(a.getContainerId());
            if (l == null) {
                l = new ArrayList<Auftrag>();
                je.put(a.getContainerId(), l);
            }
            l.add(a);
        }
        for (List<Auftrag> kette : je.values()) {
            // Start: wo der Container im Modell steht - in der Prognose also dort,
            // wo ihn die fruehen Schritte schon hingebracht haben
            int start = kette.get(0).getOrtJetztId();
            for (com.dan.logistikapp.model.ContainerInfo c : m.getContainer()) {
                if (c.getContainerId() == kette.get(0).getContainerId()) {
                    start = c.getOrtId();
                }
            }
            KartenModell.OrtPunkt von = m.punkt(start);
            for (Auftrag a : kette) {
                KartenModell.OrtPunkt nach = m.punkt(a.getNachOrtId());
                if (von == null || nach == null) {
                    break;
                }
                if (von != nach) {
                    double[][] ll = Geodaesie.grosskreis(von.getOrt().getLaenge(), von.getOrt().getBreite(),
                            nach.getOrt().getLaenge(), nach.getOrt().getBreite(), 40);
                    double[][] xy = new double[ll.length][];
                    for (int i = 0; i < ll.length; i++) {
                        xy[i] = Laea3035.vor(ll[i][0], ll[i][1]);
                    }
                    strecken.add(new Strecke(a, xy));
                }
                von = nach;
            }
        }
    }

    int strecken() {
        return strecken.size();
    }

    void zeichne(Graphics2D g, KartenAnsicht an, long jetzt) {
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, 10);
        float[] strich = {3f, 5f};
        for (Strecke s : strecken) {
            Auftrag a = s.auftrag;
            boolean blockiert = a.getGrund() != null && a.getVersuche() > 0;
            Color c = blockiert ? ContainerEbene.ZOLL_FARBE : new Color(0xB9CBD1);
            Path2D.Double p = new Path2D.Double();
            for (int i = 0; i < s.welt.length; i++) {
                Point2D.Double b = an.zuBildschirm(s.welt[i][0], s.welt[i][1]);
                if (i == 0) {
                    p.moveTo(b.x, b.y);
                } else {
                    p.lineTo(b.x, b.y);
                }
            }
            g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0x0A, 0x1B, 0x26, 120));
            g.draw(p);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, strich, 0f));
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 210));
            g.draw(p);

            // Uhr in der Mitte der Strecke - am Ziel verdeckte sie bei kurzen Wegen den Stadtnamen
            double[] w = s.welt[s.welt.length / 2];
            Point2D.Double u = an.zuBildschirm(w[0], w[1]);
            String t = blockiert ? "!" : countdown(a.getFaelligMs() - jetzt);
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            double bw = 18 + fm.stringWidth(t);
            RoundRectangle2D.Double tafel = new RoundRectangle2D.Double(u.x - bw / 2, u.y - 8, bw, 16, 8, 8);
            g.setColor(KartenFarben.TAFEL);
            g.fill(tafel);
            g.setColor(c);
            g.setStroke(new BasicStroke(1.1f));
            g.draw(tafel);
            double ux = u.x - bw / 2 + 8;
            g.draw(new Ellipse2D.Double(ux - 4, u.y - 4, 8, 8));
            g.draw(new Line2D.Double(ux, u.y, ux, u.y - 2.6));
            g.draw(new Line2D.Double(ux, u.y, ux + 2, u.y));
            g.setColor(KartenFarben.TEXT);
            g.drawString(t, (float) (ux + 6), (float) (u.y + 4));
        }
    }

    /** "in 4:32", "in 1:02:05", "fällig". */
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
}
