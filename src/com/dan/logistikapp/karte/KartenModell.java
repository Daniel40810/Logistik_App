package com.dan.logistikapp.karte;

import com.dan.logistikapp.geo.Laea3035;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.Land;
import com.dan.logistikapp.model.Ort;
import com.dan.logistikapp.model.Ware;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Alles, was die Karte zeichnet, fertig projiziert (EPSG:3035, Meter).
 *
 * <p>Projiziert wird genau einmal, beim Aufbau. Beim Zeichnen passiert
 * nur noch die Umrechnung Meter &rarr; Bildpunkte.</p>
 *
 * @author Dan
 */
public final class KartenModell {

    /** Ein Land mit projiziertem Umriss. */
    public static final class LandForm {
        private final Land land;
        private final Path2D.Double pfad;
        private final Rectangle2D rahmen;

        LandForm(Land land, Path2D.Double pfad) {
            this.land = land;
            this.pfad = pfad;
            this.rahmen = pfad.getBounds2D();
        }

        public Land getLand() {
            return land;
        }

        public Path2D.Double getPfad() {
            return pfad;
        }

        public Rectangle2D getRahmen() {
            return rahmen;
        }
    }

    /** Eine Stadt mit projizierter Lage. */
    public static final class OrtPunkt {
        private final Ort ort;
        private final double x;
        private final double y;

        OrtPunkt(Ort ort, double x, double y) {
            this.ort = ort;
            this.x = x;
            this.y = y;
        }

        public Ort getOrt() {
            return ort;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }
    }

    private final List<LandForm> formen;
    private final List<OrtPunkt> punkte;
    private final List<Ware> waren;
    private final List<ContainerInfo> container;
    private final Path2D.Double gradnetz;
    private final Rectangle2D stadtRahmen;
    private final com.dan.logistikapp.model.Tarif tarif;
    /** Belegte TEU je Stadt - dieselbe Rechnung wie LOG_API.belegt_teu, Zoll-Wartende eingeschlossen. */
    private final java.util.Map<Integer, Integer> belegt = new java.util.HashMap<Integer, Integer>();

    private KartenModell(List<LandForm> formen, List<OrtPunkt> punkte, List<Ware> waren,
            List<ContainerInfo> container, com.dan.logistikapp.model.Tarif tarif) {
        this.tarif = tarif;
        for (ContainerInfo c : container) {
            Integer b = belegt.get(c.getOrtId());
            belegt.put(c.getOrtId(), (b == null ? 0 : b) + c.getTeu());
        }
        this.formen = Collections.unmodifiableList(new ArrayList<LandForm>(formen));
        this.punkte = Collections.unmodifiableList(new ArrayList<OrtPunkt>(punkte));
        this.waren = Collections.unmodifiableList(new ArrayList<Ware>(waren));
        this.container = Collections.unmodifiableList(new ArrayList<ContainerInfo>(container));
        this.gradnetz = baueGradnetz();
        Rectangle2D r = null;
        for (OrtPunkt p : punkte) {
            if (r == null) {
                r = new Rectangle2D.Double(p.x, p.y, 0, 0);
            } else {
                r.add(p.x, p.y);
            }
        }
        this.stadtRahmen = (r != null) ? r : new Rectangle2D.Double(3.0e6, 2.0e6, 3.0e6, 3.0e6);
    }

    /** L&auml;dt aus der Quelle und projiziert. L&auml;nder ohne Fl&auml;che werden &uuml;bergangen. */
    public static KartenModell aus(KartenQuelle quelle) {
        List<LandForm> f = new ArrayList<LandForm>();
        for (Land l : quelle.laender()) {
            if (l.getGrenze() != null) {
                f.add(new LandForm(l, l.getGrenze().projiziert()));
            }
        }
        List<OrtPunkt> p = new ArrayList<OrtPunkt>();
        double[] xy = new double[2];
        for (Ort o : quelle.orte()) {
            Laea3035.vor(o.getLaenge(), o.getBreite(), xy);
            p.add(new OrtPunkt(o, xy[0], xy[1]));
        }
        return new KartenModell(f, p, quelle.waren(), quelle.container(), quelle.tarif());
    }

    /** L&auml;ngen- und Breitenkreise alle 5&deg;, in halben Grad abgetastet - in LAEA sind sie gebogen. */
    private static Path2D.Double baueGradnetz() {
        Path2D.Double g = new Path2D.Double();
        double[] p = new double[2];
        for (int lon = -30; lon <= 60; lon += 5) {
            for (double lat = 30; lat <= 75.001; lat += 0.5) {
                Laea3035.vor(lon, lat, p);
                if (lat == 30) {
                    g.moveTo(p[0], p[1]);
                } else {
                    g.lineTo(p[0], p[1]);
                }
            }
        }
        for (int lat = 30; lat <= 75; lat += 5) {
            for (double lon = -30; lon <= 60.001; lon += 0.5) {
                Laea3035.vor(lon, lat, p);
                if (lon == -30) {
                    g.moveTo(p[0], p[1]);
                } else {
                    g.lineTo(p[0], p[1]);
                }
            }
        }
        return g;
    }

    public List<LandForm> getFormen() {
        return formen;
    }

    public List<OrtPunkt> getPunkte() {
        return punkte;
    }

    /** Dasselbe Modell mit neuem Containerstand - L&auml;nder bleiben projiziert. */
    public KartenModell mitContainer(List<ContainerInfo> neu) {
        return new KartenModell(formen, punkte, waren, neu, tarif);
    }

    /** Dasselbe Modell mit einem neu geladenen Stadtstand. */
    public KartenModell mitOrten(List<Ort> neu) {
        List<OrtPunkt> p = new ArrayList<OrtPunkt>();
        double[] xy = new double[2];
        for (Ort o : neu) {
            Laea3035.vor(o.getLaenge(), o.getBreite(), xy);
            p.add(new OrtPunkt(o, xy[0], xy[1]));
        }
        return new KartenModell(formen, p, waren, container, tarif);
    }

    /** Alle St&auml;dte. */
    public List<Ort> getOrte() {
        List<Ort> out = new ArrayList<Ort>();
        for (OrtPunkt p : punkte) {
            out.add(p.ort);
        }
        return out;
    }

    /** Alle L&auml;nder mit Fl&auml;che. */
    public List<Land> getLaender() {
        List<Land> out = new ArrayList<Land>();
        for (LandForm f : formen) {
            out.add(f.land);
        }
        return out;
    }

    /** Das Land mit diesem Code, oder {@code null}. */
    public Land land(String iso2) {
        for (LandForm f : formen) {
            if (f.land.getIso2().equals(iso2)) {
                return f.land;
            }
        }
        return null;
    }

    /** Tarife f&uuml;r die Kostenvorschau, {@code null} ohne. */
    public com.dan.logistikapp.model.Tarif getTarif() {
        return tarif;
    }

    /** Belegte TEU in dieser Stadt. */
    public int belegtTeu(int ortId) {
        Integer b = belegt.get(ortId);
        return b == null ? 0 : b;
    }

    /** Freie TEU in dieser Stadt, {@code null} wenn sie unbegrenzt ist. */
    public Integer freiTeu(int ortId) {
        OrtPunkt p = punkt(ortId);
        if (p == null || p.ort.getKapazitaetTeu() == null) {
            return null;
        }
        return p.ort.getKapazitaetTeu() - belegtTeu(ortId);
    }

    public List<Ware> getWaren() {
        return waren;
    }

    public List<ContainerInfo> getContainer() {
        return container;
    }

    /** Die Stadt mit dieser Nummer, oder {@code null}. */
    public OrtPunkt punkt(int ortId) {
        for (OrtPunkt p : punkte) {
            if (p.ort.getOrtId() == ortId) {
                return p;
            }
        }
        return null;
    }

    public Path2D.Double getGradnetz() {
        return gradnetz;
    }

    /** Rahmen um alle St&auml;dte in Metern - Grundlage f&uuml;r "alles zeigen". */
    public Rectangle2D getStadtRahmen() {
        return stadtRahmen;
    }

    /** Das Land unter einem Punkt (Meter), oder {@code null} auf dem Meer. */
    public LandForm landBei(double x, double y) {
        for (LandForm f : formen) {
            if (f.rahmen.contains(x, y) && f.pfad.contains(x, y)) {
                return f;
            }
        }
        return null;
    }
}
