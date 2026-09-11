package com.dan.logistikapp.karte;

import com.dan.logistikapp.dienst.ContainerDienst;
import com.dan.logistikapp.dienst.DienstFehler;
import com.dan.logistikapp.dienst.Fahrt;
import com.dan.logistikapp.geo.Geodaesie;
import com.dan.logistikapp.geo.Laea3035;
import com.dan.logistikapp.mesh.ContainerRenderer;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.Iso6346;
import com.dan.logistikapp.model.Land;
import com.dan.logistikapp.model.Regeln;
import com.dan.logistikapp.model.Zone;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Locale;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/**
 * Drag and Drop der Container - als kleiner Zustandsautomat.
 *
 * <pre>
 *   RUHE --dr&uuml;cken auf Container--&gt; ZIEHEN
 *   ZIEHEN --loslassen bei einer anderen Stadt--&gt; FAHRT (+ Auftrag an LOG_API)
 *   ZIEHEN --loslassen sonstwo / Esc--&gt; RUECK
 *   FAHRT --angekommen, Auftrag noch offen--&gt; WARTEN
 *   FAHRT/WARTEN --Auftrag gelungen--&gt; EINPARKEN --&gt; RUHE
 *   FAHRT/WARTEN --Auftrag abgelehnt--&gt; RUECK --&gt; RUHE
 * </pre>
 *
 * <p>Die Zust&auml;nde schreiten im Takt eines Swing-Timers voran, nicht beim
 * Zeichnen - so l&auml;uft der Ablauf auch ohne sichtbares Fenster (Tests).
 * Die Datenbank wird nie auf dem EDT gerufen.</p>
 *
 * @author Dan
 */
final class Ziehen {

    enum Modus { RUHE, ZIEHEN, RUECK, FAHRT, WARTEN, EINPARKEN }

    /** So weit (Bildpunkte) darf man neben einer Stadt loslassen. */
    static final double FANG_PX = 34;
    /** Wie hoch ein gezogener Container schwebt. */
    static final double HUB = 14;
    /** Ziel voll: Fangring, Etikett und F&uuml;llstand. */
    static final Color VOLL = new Color(0xE0524A);

    private static final double SIN_EL = Math.sin(Math.toRadians(ContainerRenderer.ERHEBUNG_GRAD));

    private final KartenPanel panel;
    private final Timer takt;

    private Modus modus = Modus.RUHE;
    private ContainerInfo container;
    private KartenModell.OrtPunkt von;
    private KartenModell.OrtPunkt ziel;
    private double mx;
    private double my;

    private long t0;
    private long dauer;
    private double startX;
    private double startY;
    private double startHub;
    private double[][] route;
    private double gier;

    private List<Grenzen.Uebergang> uebergaenge = new java.util.ArrayList<Grenzen.Uebergang>();
    private long[] blitzStart = new long[0];
    private final List<String> geblitzt = new java.util.ArrayList<String>();

    /** &gt; 0: diese Fahrt f&uuml;hrt einen Auftrag aus (LOG_API.auftrag_ausfuehren statt verschieben). */
    private long auftragId;
    private boolean auftragFertig;
    private Fahrt fahrt;
    private DienstFehler fehler;
    private List<ContainerInfo> neuerStand;

    Ziehen(KartenPanel panel) {
        this.panel = panel;
        this.takt = new Timer(16, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tick();
            }
        });
    }

    Modus getModus() {
        return modus;
    }

    boolean ruht() {
        return modus == Modus.RUHE;
    }

    /** Id des Containers, der gerade nicht auf seinem Platz steht, sonst -1. */
    int unterwegsId() {
        return (modus == Modus.FAHRT || modus == Modus.WARTEN || modus == Modus.EINPARKEN)
                ? container.getContainerId() : -1;
    }

    /** Id des Containers, dessen Platz als Umriss erscheint (Ziehen, Zur&uuml;ckfedern). */
    int geistId() {
        return (modus == Modus.ZIEHEN || modus == Modus.RUECK) ? container.getContainerId() : -1;
    }

    // ================================================================ Maus

    /** @return true, wenn der Druck einem Container galt (dann kein Verschieben der Karte) */
    boolean druecken(ContainerInfo c, double px, double py) {
        if (!ruht() || c == null) {
            return false;
        }
        if (panel.getContainerDienst() == null) {
            panel.toast("Nur Ansicht: keine Verbindung zum Schreiben", Toast.Art.HINWEIS);
            return true;
        }
        if ("ZOLL".equals(c.getStatus())) {
            panel.toast(Iso6346.anzeige(c.getKennung()) + " steht beim Zoll — Rechtsklick: freigeben",
                    Toast.Art.ZOLL);
            return true;
        }
        container = c;
        von = panel.getModell().punkt(c.getOrtId());
        mx = px;
        my = py;
        ziel = null;
        gier = 180;
        modus = Modus.ZIEHEN;
        takt.start();
        panel.repaint();
        return true;
    }

    void ziehen(double px, double py) {
        if (modus != Modus.ZIEHEN) {
            return;
        }
        mx = px;
        my = py;
        ziel = fange(px, py);
        Point2D.Double v = panel.getAnsicht().zuBildschirm(von.getX(), von.getY());
        Point2D.Double z = zielBild();
        if (z.distance(v) > 3) {
            gier = gierAusBild(z.x - v.x, z.y - v.y);
        }
        panel.repaint();
    }

    void loslassen() {
        loslassen(false);
    }

    /**
     * @param planen Shift beim Loslassen: nicht fahren, sondern einen Auftrag
     *               f&uuml;r diese Strecke planen lassen (der Container federt zur&uuml;ck)
     */
    void loslassen(boolean planen) {
        if (modus != Modus.ZIEHEN) {
            return;
        }
        if (ziel == null || ziel == von) {
            zurueck(mx, my, HUB, 380);
            return;
        }
        if (planen) {
            ContainerInfo c = container;
            KartenModell.OrtPunkt z = ziel;
            zurueck(mx, my, HUB, 380);
            panel.planen(c.getContainerId(), z.getOrt().getOrtId());
            return;
        }
        auftragId = 0;
        if (zielVoll(ziel)) {
            // LOG_API wuerde ablehnen - gar nicht erst fragen
            Integer frei = panel.getModell().freiTeu(ziel.getOrt().getOrtId());
            panel.toast(ziel.getOrt().getName() + " ist voll: " + Math.max(0, frei) + " TEU frei, der Container braucht "
                    + container.getTeu(), Toast.Art.FEHLER);
            zurueck(mx, my, HUB, 380);
            return;
        }
        starteFahrt();
    }

    /** Passt der gezogene Container nicht mehr in die Stadt? */
    boolean zielVoll(KartenModell.OrtPunkt z) {
        if (z == null || container == null) {
            return false;
        }
        Integer frei = panel.getModell().freiTeu(z.getOrt().getOrtId());
        return frei != null && frei < container.getTeu();
    }

    /**
     * Eine Fahrt ohne Maus - f&uuml;r die Demo-Szene. Der Container hebt an
     * seinem Platz ab und f&auml;hrt dieselbe Strecke wie nach dem Loslassen.
     *
     * @return false, wenn gerade etwas anderes l&auml;uft oder er nicht fahren darf
     */
    boolean fahre(ContainerInfo c, KartenModell.OrtPunkt nach) {
        return fahre(c, nach, 0);
    }

    /** Wie {@link #fahre(ContainerInfo, KartenModell.OrtPunkt)}, aber als Ausf&uuml;hrung eines Auftrags. */
    boolean fahre(ContainerInfo c, KartenModell.OrtPunkt nach, long auftrag) {
        if (!ruht() || c == null || nach == null || panel.getContainerDienst() == null
                || "ZOLL".equals(c.getStatus()) || c.getOrtId() == nach.getOrt().getOrtId()) {
            return false;
        }
        Integer frei = panel.getModell().freiTeu(nach.getOrt().getOrtId());
        if (frei != null && frei < c.getTeu()) {
            return false;
        }
        container = c;
        von = panel.getModell().punkt(c.getOrtId());
        ziel = nach;
        Point2D.Double v = panel.getAnsicht().zuBildschirm(von.getX(), von.getY());
        Point2D.Double z = panel.getAnsicht().zuBildschirm(ziel.getX(), ziel.getY());
        gier = gierAusBild(z.x - v.x, z.y - v.y);
        auftragId = auftrag;
        takt.start();
        starteFahrt();
        return true;
    }

    private void starteFahrt() {
        route = Geodaesie.grosskreis(von.getOrt().getLaenge(), von.getOrt().getBreite(),
                ziel.getOrt().getLaenge(), ziel.getOrt().getBreite(), 64);
        double km = Geodaesie.entfernungM(von.getOrt().getLaenge(), von.getOrt().getBreite(),
                ziel.getOrt().getLaenge(), ziel.getOrt().getBreite()) / 1000;
        dauer = (long) Math.max(900, Math.min(2600, 700 + km * 0.9));
        uebergaenge = Grenzen.finde(panel.getModell(), route);
        blitzStart = new long[uebergaenge.size()];
        t0 = jetzt();
        modus = Modus.FAHRT;
        starteAuftrag(container.getContainerId(), ziel.getOrt().getOrtId());
    }

    void abbrechen() {
        if (modus == Modus.ZIEHEN) {
            zurueck(mx, my, HUB, 380);
        }
    }

    private void zurueck(double x, double y, double hub, long ms) {
        startX = x;
        startY = y;
        startHub = hub;
        dauer = ms;
        t0 = jetzt();
        modus = Modus.RUECK;
        takt.start();
        panel.repaint();
    }

    /** Die n&auml;chste Stadt im Fangradius, oder {@code null}. */
    private KartenModell.OrtPunkt fange(double px, double py) {
        KartenModell.OrtPunkt best = null;
        double bd = FANG_PX;
        for (KartenModell.OrtPunkt p : panel.getModell().getPunkte()) {
            Point2D.Double s = panel.getAnsicht().zuBildschirm(p.getX(), p.getY());
            double d = s.distance(px, py);
            if (d <= bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private Point2D.Double zielBild() {
        if (ziel != null) {
            return panel.getAnsicht().zuBildschirm(ziel.getX(), ziel.getY());
        }
        return new Point2D.Double(mx, my);
    }

    /**
     * Bildrichtung &rarr; Drehung des Containers. Die Karte blickt schr&auml;g
     * (52&deg;), eine Nord-S&uuml;d-Strecke erscheint deshalb verk&uuml;rzt - die Drehung
     * im Raum ist steiler als der Winkel auf dem Bild. Plus 180&deg;: die T&uuml;ren
     * zeigen nach hinten, wie auf dem Lkw.
     */
    static double gierAusBild(double dx, double dyBild) {
        double phi = Math.atan2(-dyBild, dx);
        double g = Math.atan2(Math.sin(phi) / SIN_EL, Math.cos(phi));
        return Math.toDegrees(g) + 180;
    }

    // ================================================================ Auftrag

    private void starteAuftrag(final int containerId, final int ortId) {
        auftragFertig = false;
        fahrt = null;
        fehler = null;
        neuerStand = null;
        final ContainerDienst dienst = panel.getContainerDienst();
        final long auftrag = auftragId;
        new SwingWorker<Void, Void>() {
            private Fahrt f;
            private DienstFehler df;
            private List<ContainerInfo> stand;

            @Override
            protected Void doInBackground() {
                try {
                    if (auftrag > 0) {
                        com.dan.logistikapp.dienst.AuftragErgebnis e = dienst.auftragAusfuehren(auftrag);
                        if (!e.istGefahren()) {
                            stand = dienst.container();
                            throw new DienstFehler(DienstFehler.Art.AUFTRAG_NICHT_GEFAHREN,
                                    e.getErgebnis() + (e.getGrund() != null ? " — " + e.getGrund() : ""), null);
                        }
                        f = e.getFahrt();
                    } else {
                        f = dienst.verschieben(containerId, ortId);
                    }
                    stand = dienst.container();
                } catch (DienstFehler e) {
                    df = e;
                } catch (RuntimeException e) {
                    df = new DienstFehler(DienstFehler.Art.DATENBANK, String.valueOf(e.getMessage()), e);
                }
                return null;
            }

            @Override
            protected void done() {
                fahrt = f;
                fehler = df;
                neuerStand = stand;
                auftragFertig = true;
                if (modus == Modus.WARTEN) {
                    ankommen();
                }
            }
        }.execute();
    }

    private void ankommen() {
        Point2D.Double z = panel.getAnsicht().zuBildschirm(ziel.getX(), ziel.getY());
        if (fehler != null) {
            panel.toast(fehlerText(fehler), Toast.Art.FEHLER);
            if (auftragId > 0 && neuerStand != null) {
                // Versuch und Grund stehen jetzt am Auftrag - Listen neu lesen lassen
                panel.setContainerStand(neuerStand);
            }
            zurueck(z.x, z.y, HUB * 0.7, 650);
            return;
        }
        panel.setContainerStand(neuerStand);
        for (ContainerInfo c : neuerStand) {
            if (c.getContainerId() == container.getContainerId()) {
                container = c;
            }
        }
        String text = Iso6346.anzeige(container.getKennung()) + "  " + von.getOrt().getName() + " → "
                + ziel.getOrt().getName() + String.format(Locale.GERMANY, "  ·  %.0f km  ·  ",
                        fahrt.getDistanzM() / 1000.0)
                + zoneText(fahrt.getVonZone()) + " → " + zoneText(fahrt.getNachZone());
        if (fahrt.getKostenEur() != null) {
            text += "  ·  " + euro(fahrt.getKostenEur());
        }
        if (auftragId > 0) {
            text = "Auftrag  ·  " + text;
            panel.auftragGefahren(auftragId);
        }
        panel.toast(fahrt.isZoll() ? text + "  ·  beim Zoll" : text,
                fahrt.isZoll() ? Toast.Art.ZOLL : Toast.Art.ERFOLG);
        startX = z.x;
        startY = z.y;
        startHub = HUB * 0.7;
        dauer = 420;
        t0 = jetzt();
        modus = Modus.EINPARKEN;
    }

    /** "1.234,56 €" */
    static String euro(java.math.BigDecimal b) {
        return String.format(Locale.GERMANY, "%,.2f €", b);
    }

    static String zoneText(String code) {
        try {
            return Zone.vonCode(code).anzeige();
        } catch (IllegalArgumentException e) {
            return String.valueOf(code);
        }
    }

    static String fehlerText(DienstFehler f) {
        switch (f.getArt()) {
            case BEIM_ZOLL:
                return "Abgelehnt: der Container steht beim Zoll";
            case SCHON_DA:
                return "Der Container steht bereits dort";
            case ZIEL_VOLL:
                return "Abgelehnt: " + f.getMessage();
            case AUFTRAG_NICHT_GEFAHREN:
                return "Auftrag nicht gefahren: " + f.getMessage();
            case CONTAINER_UNBEKANNT:
            case ORT_UNBEKANNT:
                return "Abgelehnt: " + f.getMessage() + " — Karte neu laden";
            default:
                return "Datenbank: " + f.getMessage();
        }
    }

    // ================================================================ Takt

    long jetzt() {
        return System.nanoTime() / 1000000L;
    }

    void tick() {
        double p = dauer > 0 ? Math.min(1, (jetzt() - t0) / (double) dauer) : 1;
        switch (modus) {
            case RUECK:
            case EINPARKEN:
                if (p >= 1) {
                    modus = Modus.RUHE;
                    takt.stop();
                }
                break;
            case FAHRT:
                double f = weich(p);
                for (int i = 0; i < uebergaenge.size(); i++) {
                    if (blitzStart[i] == 0 && f >= uebergaenge.get(i).getAnteil()) {
                        blitzStart[i] = jetzt();
                        geblitzt.add(uebergaenge.get(i).getNach().getIso2());
                    }
                }
                if (p >= 1) {
                    if (auftragFertig) {
                        ankommen();
                    } else {
                        modus = Modus.WARTEN;
                    }
                }
                break;
            default:
                break;
        }
        panel.repaint();
    }

    // ================================================================ Zeichnen

    /** Wird vom Panel nach den Stellpl&auml;tzen und vor den St&auml;dten gerufen. */
    void zeichne(Graphics2D g2, ContainerEbene ebene, double faktor) {
        if (modus == Modus.RUHE) {
            return;
        }
        Graphics2D g = (Graphics2D) g2.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double p = dauer > 0 ? Math.min(1, (jetzt() - t0) / (double) dauer) : 1;
            switch (modus) {
                case ZIEHEN:
                    zeichneZiehen(g, ebene, faktor);
                    break;
                case RUECK:
                case EINPARKEN: {
                    ContainerEbene.Platz pl = ebene.platz(container.getContainerId());
                    double tx = pl != null ? pl.getAnkerX() : startX;
                    double ty = pl != null ? pl.getAnkerY() : startY;
                    double e = zurueckfedern(p);
                    double x = startX + (tx - startX) * e;
                    double y = startY + (ty - startY) * e;
                    double gg = (modus == Modus.EINPARKEN) ? gier : gier + (0 - gier) * Math.min(1, p * 1.4);
                    ebene.zeichneFrei(g, container, x, y, faktor, startHub * (1 - Math.min(1, p)), p >= 0.7 ? 0 : gg);
                    break;
                }
                case FAHRT:
                case WARTEN: {
                    zeichneRoute(g, route, 0.35f);
                    zeichneBlitze(g);
                    zeichneUebergaenge(g, 0);
                    double f = (modus == Modus.WARTEN) ? 1 : weich(p);
                    double[] pos = aufRoute(f);
                    double[] vor = aufRoute(Math.min(1, f + 0.01));
                    double[] nach = aufRoute(Math.max(0, f - 0.01));
                    if (Math.hypot(vor[0] - nach[0], vor[1] - nach[1]) > 0.5) {
                        gier = gierAusBild(vor[0] - nach[0], vor[1] - nach[1]);
                    }
                    double hub = HUB * 0.7 + (modus == Modus.WARTEN ? 2 * Math.sin(jetzt() / 160.0) : 0);
                    ebene.zeichneFrei(g, container, pos[0], pos[1], faktor, hub, gier);
                    break;
                }
                default:
                    break;
            }
        } finally {
            g.dispose();
        }
    }

    private void zeichneZiehen(Graphics2D g, ContainerEbene ebene, double faktor) {
        Point2D.Double z = zielBild();
        double lon;
        double lat;
        if (ziel != null) {
            lon = ziel.getOrt().getLaenge();
            lat = ziel.getOrt().getBreite();
        } else {
            Point2D.Double w = panel.getAnsicht().zuWelt(mx, my);
            double[] ll = Laea3035.zurueck(w.x, w.y);
            lon = ll[0];
            lat = ll[1];
        }
        double[][] r = Geodaesie.grosskreis(von.getOrt().getLaenge(), von.getOrt().getBreite(), lon, lat, 48);
        zeichneRoute(g, r, 1f);
        uebergaenge = Grenzen.finde(panel.getModell(), r);
        zeichneUebergaenge(g, (jetzt() % 1200) / 1200.0);
        if (ziel != null && ziel != von) {
            // Fangring um die Zielstadt - rot, wenn der Container nicht mehr hineinpasst
            Color ring = zielVoll(ziel) ? VOLL : new Color(0xE6EEF1);
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), 220));
            g.draw(new Ellipse2D.Double(z.x - 15, z.y - 15, 30, 30));
            g.setColor(new Color(ring.getRed(), ring.getGreen(), ring.getBlue(), 70));
            g.draw(new Ellipse2D.Double(z.x - 21, z.y - 21, 42, 42));
        }
        ebene.zeichneFrei(g, container, mx, my, faktor, HUB, gier);
        zeichneEtikett(g, mx, my);
    }

    /**
     * Schlagbaum an jeder Zonen- oder Zollgrenze der Route: Kreis in der
     * Farbe dessen, was danach kommt (Bernstein bei Zoll), mit Querbalken
     * und den beiden L&auml;ndercodes.
     */
    private void zeichneUebergaenge(Graphics2D g, double puls) {
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, 10);
        for (Grenzen.Uebergang u : uebergaenge) {
            double[] w = Laea3035.vor(u.getLaenge(), u.getBreite());
            Point2D.Double s = panel.getAnsicht().zuBildschirm(w[0], w[1]);
            Color c = u.isZoll() ? ContainerEbene.ZOLL_FARBE : KartenFarben.marke(u.getNach().getZone());
            if (puls > 0) {
                double r = 8 + 9 * puls;
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (150 * (1 - puls))));
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Double(s.x - r, s.y - r, 2 * r, 2 * r));
            }
            Ellipse2D.Double k = new Ellipse2D.Double(s.x - 7, s.y - 7, 14, 14);
            g.setColor(KartenFarben.HALO);
            g.setStroke(new BasicStroke(3.5f));
            g.draw(k);
            g.setColor(c);
            g.fill(k);
            g.setColor(new Color(0x0A1B26));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(s.x - 4, s.y + 1, s.x + 4, s.y - 2));
            g.fill(new Ellipse2D.Double(s.x - 5.5, s.y - 0.5, 3, 3));
            String t = u.getVon().getIso2() + " \u2192 " + u.getNach().getIso2() + (u.isZoll() ? "  Zoll" : "");
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            double tx = s.x - fm.stringWidth(t) / 2.0;
            double ty = s.y - 11;
            g.setColor(KartenFarben.TAFEL);
            g.fill(new RoundRectangle2D.Double(tx - 5, ty - 11, fm.stringWidth(t) + 10, 15, 6, 6));
            g.setColor(u.isZoll() ? ContainerEbene.ZOLL_FARBE : KartenFarben.TEXT);
            g.drawString(t, (float) tx, (float) ty);
        }
    }

    /** Das Land hinter der Grenze leuchtet kurz auf, wenn der Container sie passiert. */
    private void zeichneBlitze(Graphics2D g) {
        long jetzt = jetzt();
        java.awt.geom.AffineTransform at = panel.getAnsicht().transform();
        for (int i = 0; i < uebergaenge.size(); i++) {
            if (blitzStart[i] == 0) {
                continue;
            }
            double a = 1 - (jetzt - blitzStart[i]) / 900.0;
            if (a <= 0) {
                continue;
            }
            Grenzen.Uebergang u = uebergaenge.get(i);
            KartenModell.LandForm form = null;
            for (KartenModell.LandForm lf : panel.getModell().getFormen()) {
                if (lf.getLand() == u.getNach()) {
                    form = lf;
                }
            }
            if (form == null) {
                continue;
            }
            java.awt.Shape s = KartenPanel.bildpfad(form.getPfad(), at);
            Color c = u.isZoll() ? ContainerEbene.ZOLL_FARBE : KartenFarben.marke(u.getNach().getZone());
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (40 * a)));
            g.fill(s);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (230 * a)));
            g.setStroke(new BasicStroke((float) (1.5 + 2.5 * a), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(s);
        }
    }

    /** L&auml;ndercodes, deren Grenze bei Fahrten aufgeleuchtet hat (f&uuml;r Tests). */
    List<String> getGeblitzt() {
        return geblitzt;
    }

    /** &Uuml;berg&auml;nge der zuletzt gezeichneten oder gefahrenen Route. */
    List<Grenzen.Uebergang> getUebergaenge() {
        return uebergaenge;
    }

    /** Route als gestrichelte Linie; jedes St&uuml;ck in der Farbe der Zone darunter. */
    private void zeichneRoute(Graphics2D g, double[][] r, float deckkraft) {
        double[] a = new double[2];
        double[] b = new double[2];
        Point2D.Double pa = null;
        KartenAnsicht an = panel.getAnsicht();
        KartenModell m = panel.getModell();
        float[] strich = {7f, 5f};
        for (int i = 0; i < r.length; i++) {
            Laea3035.vor(r[i][0], r[i][1], b);
            Point2D.Double pb = an.zuBildschirm(b[0], b[1]);
            if (pa != null) {
                KartenModell.LandForm land = m.landBei((a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
                Color c = land == null ? new Color(0x8FA7B0) : KartenFarben.marke(land.getLand().getZone());
                g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(0x0A, 0x1B, 0x26, (int) (160 * deckkraft)));
                g.draw(new Line2D.Double(pa, pb));
                g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, strich,
                        (float) ((i * 6.7) % 12)));
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (255 * deckkraft)));
                g.draw(new Line2D.Double(pa, pb));
            }
            a[0] = b[0];
            a[1] = b[1];
            pa = pb;
        }
    }

    private void zeichneEtikett(Graphics2D g, double x, double y) {
        String zeile1;
        String zeile2 = null;
        String zeile3 = null;
        Color farbe3 = KartenFarben.TEXT;
        Color akzent = KartenFarben.TEXT_LEISE;
        if (ziel == null || ziel == von) {
            zeile1 = ziel == von ? "zurück an den Platz" : "loslassen: zurück an den Platz";
        } else {
            double km = Geodaesie.entfernungM(von.getOrt().getLaenge(), von.getOrt().getBreite(),
                    ziel.getOrt().getLaenge(), ziel.getOrt().getBreite()) / 1000;
            Land lv = panel.getModell().land(von.getOrt().getIso2());
            Land lz = panel.getModell().land(ziel.getOrt().getIso2());
            boolean zoll = lv != null && lz != null && Regeln.zollNoetig(lv, lz);
            zeile1 = "→ " + ziel.getOrt().getName() + String.format(Locale.GERMANY, "  ·  %.0f km", km);
            zeile2 = von.getOrt().getZone().anzeige() + " → " + ziel.getOrt().getZone().anzeige()
                    + (zoll ? "  ·  Zoll" : "  ·  zollfrei");
            akzent = zoll ? ContainerEbene.ZOLL_FARBE : KartenFarben.marke(ziel.getOrt().getZone());
            // Kosten und Platz am Ziel - dieselbe Rechnung wie LOG_API (Tarif, Kapazitaet)
            Integer frei = panel.getModell().freiTeu(ziel.getOrt().getOrtId());
            com.dan.logistikapp.model.Tarif t = panel.getModell().getTarif();
            java.math.BigDecimal kosten = t == null ? null : t.vorschau(Math.round(km * 1000), container.getTeu(),
                    container.getWareCode(), zoll);
            if (frei != null && frei < container.getTeu()) {
                zeile3 = "voll — " + Math.max(0, frei) + " TEU frei, braucht " + container.getTeu();
                farbe3 = VOLL;
                akzent = VOLL;
            } else if (kosten != null || frei != null) {
                zeile3 = (kosten != null ? "≈ " + euro(kosten) : "")
                        + (kosten != null && frei != null ? "  ·  " : "")
                        + (frei != null ? "frei " + frei + " TEU" : "");
            }
        }
        Font f1 = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        Font f2 = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        g.setFont(f1);
        FontMetrics m1 = g.getFontMetrics();
        int w = m1.stringWidth(zeile1);
        if (zeile2 != null) {
            g.setFont(f2);
            w = Math.max(w, g.getFontMetrics().stringWidth(zeile2));
        }
        if (zeile3 != null) {
            g.setFont(f1);
            w = Math.max(w, g.getFontMetrics().stringWidth(zeile3));
        }
        double bx = x + 22;
        double by = y + 14;
        double bh = zeile2 == null ? 24 : (zeile3 == null ? 40 : 56);
        g.setColor(KartenFarben.TAFEL);
        g.fill(new RoundRectangle2D.Double(bx, by, w + 22, bh, 8, 8));
        g.setColor(akzent);
        g.fill(new RoundRectangle2D.Double(bx, by, 4, bh, 4, 4));
        g.setFont(f1);
        g.setColor(KartenFarben.TEXT);
        g.drawString(zeile1, (float) bx + 12, (float) by + 16);
        if (zeile2 != null) {
            g.setFont(f2);
            g.setColor(akzent == VOLL ? KartenFarben.TEXT_LEISE : akzent);
            g.drawString(zeile2, (float) bx + 12, (float) by + 32);
        }
        if (zeile3 != null) {
            g.setFont(f1);
            g.setColor(farbe3);
            g.drawString(zeile3, (float) bx + 12, (float) by + 49);
        }
    }

    /** Punkt auf der Fahrtroute (Anteil 0..1) in Bildpunkten. */
    private double[] aufRoute(double f) {
        double pos = f * (route.length - 1);
        int i = Math.min(route.length - 2, (int) Math.floor(pos));
        double t = pos - i;
        double[] a = Laea3035.vor(route[i][0], route[i][1]);
        double[] b = Laea3035.vor(route[i + 1][0], route[i + 1][1]);
        Point2D.Double s = panel.getAnsicht().zuBildschirm(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t);
        return new double[] {s.x, s.y};
    }

    /** Sanft anfahren, sanft bremsen. */
    static double weich(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    /** Zur&uuml;ckfedern mit leichtem &Uuml;berschwingen, wie die FStyle-Komponenten. */
    static double zurueckfedern(double t) {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        double u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }
}
