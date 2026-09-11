package com.dan.logistikapp.karte;

import com.dan.logistikapp.model.Ort;
import com.dan.logistikapp.model.Zone;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * Die Europa-Karte: L&auml;nder nach Zone eingef&auml;rbt, St&auml;dte mit Zonenmarke,
 * Gradnetz, Legende und Ma&szlig;stab.
 *
 * <p>Bedienung: Mausrad zoomt um den Mauszeiger, Ziehen verschiebt,
 * Doppelklick zeigt wieder alle St&auml;dte. Was unter der Maus liegt, meldet
 * die Karte als Eigenschaft {@code "hinweis"} (Text) - die Statuszeile
 * der Anwendung h&ouml;rt darauf.</p>
 *
 * <p>Sp&auml;ter (Phase 3/4) kommen die Container als eigene Ebene &uuml;ber die
 * St&auml;dte; dieses Panel zeichnet dann weiterhin den Grund.</p>
 *
 * @author Dan
 */
public class KartenPanel extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Wie nah (Bildpunkte) die Maus einer Stadt kommen muss. */
    private static final double TREFFER_PX = 12;

    private final KartenAnsicht ansicht = new KartenAnsicht();
    private KartenModell modell;
    private String meldung = "Karte wird geladen …";
    private boolean eingepasst;

    private final ContainerEbene containerEbene = new ContainerEbene();
    private String containerFilter = "ALLE";
    /** Ma&szlig;stab der Europa-Ansicht - Bezug f&uuml;r die Containergr&ouml;&szlig;e. */
    private double grundMassstab = 1 / 5000.0;
    /** Startansicht: 4 km je Bildpunkt, damit die Leiste 500 km zeigt. */
    private static final double START_MASSSTAB = 1 / 4000.0;
    private com.dan.logistikapp.model.ContainerInfo hoverContainer;
    private KartenModell.OrtPunkt hoverOrt;
    private KartenModell.LandForm hoverLand;
    private String hinweis = "";

    // Grund-Ebene (Meer, Gradnetz, Laender) als Bild. Das Neuzeichnen kostet
    // bei 58 000 Stuetzpunkten um die 100 ms - zu viel fuer jede Mausbewegung.
    // Waehrend Ziehen und Zoomen wird das vorhandene Bild nur verschoben und
    // skaliert; erst wenn die Maus 140 ms ruht, wird scharf neu gezeichnet.
    private transient java.awt.image.BufferedImage schicht;
    private transient AffineTransform schichtTransform;
    private boolean interaktiv;
    private final javax.swing.Timer ruhe = new javax.swing.Timer(140, new java.awt.event.ActionListener() {
        @Override
        public void actionPerformed(java.awt.event.ActionEvent e) {
            interaktiv = false;
            repaint();
        }
    });

    private final Ziehen ziehen = new Ziehen(this);
    private transient com.dan.logistikapp.dienst.ContainerDienst dienst;
    private Toast toast;
    private int hervorheben = -1;
    private long hervorhebenBis;
    private int laufendeAuftraege;
    private Spur spur;
    private int spurContainer = -1;
    private String demoText;
    private PlanEbene plaene;

    // Zeitreise (Runde 2): solange zeitModell gesetzt ist, zeigt die Karte den
    // Stand von damals - oder die Prognose - und ist nur Ansicht.
    private KartenModell zeitModell;
    private long zeitMs;
    private boolean zeitPrognose;
    private java.util.List<com.dan.logistikapp.model.Zeitreise.Unterwegs> zeitUnterwegs =
            java.util.Collections.emptyList();
    private PlanEbene zeitPlaene;
    private int ortHervor = -1;
    private final javax.swing.Timer toastTakt = new javax.swing.Timer(60, new java.awt.event.ActionListener() {
        @Override
        public void actionPerformed(java.awt.event.ActionEvent e) {
            long jetzt = System.currentTimeMillis();
            if (toast != null && toast.vorbei(jetzt)) {
                toast = null;
            }
            if (toast == null && jetzt > hervorhebenBis) {
                ((javax.swing.Timer) e.getSource()).stop();
            }
            repaint();
        }
    });

    private final Font schriftStadt = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private final Font schriftKlein = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private final Font schriftTitel = new Font(Font.SANS_SERIF, Font.BOLD, 11);

    public KartenPanel() {
        setOpaque(true);
        setPreferredSize(new java.awt.Dimension(1200, 800));
        ruhe.setRepeats(false);
        setFocusable(true);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "abbrechen");
        getActionMap().put("abbrechen", new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (ziehen.getModus() == Ziehen.Modus.ZIEHEN) {
                    ziehen.abbrechen();
                } else if (zeitModell != null) {
                    // KartenPanel.this: sonst feuert die AbstractAction ihr eigenes Ereignis
                    KartenPanel.this.firePropertyChange("zeitreiseVerlassen", false, true);
                } else if (spur != null) {
                    loescheSpur();
                }
            }
        });
        Maus maus = new Maus();
        addMouseListener(maus);
        addMouseMotionListener(maus);
        addMouseWheelListener(maus);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                ansicht.setGroesse(getWidth(), getHeight());
                if (!eingepasst && modell != null) {
                    allesZeigen();
                }
                repaint();
            }
        });
    }

    // ================================================================ API

    public void setModell(KartenModell m) {
        this.modell = m;
        this.meldung = null;
        this.eingepasst = false;
        if (getWidth() > 0 && getHeight() > 0) {
            allesZeigen();
        }
        repaint();
    }

    public KartenModell getModell() {
        return modell;
    }

    /** Text statt Karte, etwa wenn die Datenbank nicht erreichbar ist. */
    public void setMeldung(String text) {
        this.meldung = text;
        repaint();
    }

    public KartenAnsicht getAnsicht() {
        return ansicht;
    }

    /** Stellt den gespeicherten Kartenausschnitt wieder her. */
    public void setGespeicherteAnsicht(double massstab, double mitteX, double mitteY) {
        ansicht.setAnsicht(massstab, mitteX, mitteY);
        grundMassstab = ansicht.getMassstab();
        eingepasst = true;
        repaint();
    }

    /** Alle St&auml;dte ins Bild, mit Rand. */
    public void allesZeigen() {
        if (modell == null) {
            return;
        }
        boolean ersteAnsicht = !eingepasst;
        ansicht.setGroesse(getWidth(), getHeight());
        ansicht.einpassen(modell.getStadtRahmen(), 0.18);
        if (ersteAnsicht && ansicht.getMassstab() != START_MASSSTAB) {
            ansicht.zoomUm(getWidth() / 2.0, getHeight() / 2.0,
                    START_MASSSTAB / ansicht.getMassstab());
        }
        grundMassstab = ansicht.getMassstab();
        eingepasst = true;
        repaint();
    }

    public String getHinweis() {
        return hinweis;
    }

    public ContainerEbene getContainerEbene() {
        return containerEbene;
    }

    /** Setzt den Anzeige-Filter f&uuml;r Container auf der Karte. */
    public void setContainerFilter(String filter) {
        containerFilter = filter == null || filter.isEmpty() ? "ALLE" : filter;
        hoverContainer = null;
        repaint();
    }

    public String getContainerFilter() {
        return containerFilter;
    }

    private ContainerEbene.Filter containerFilter() {
        final String filter = containerFilter;
        if ("ALLE".equals(filter)) {
            return null;
        }
        if ("ZOLL".equals(filter)) {
            return new ContainerEbene.Filter() {
                @Override
                public boolean zeigt(com.dan.logistikapp.model.ContainerInfo c) {
                    return "ZOLL".equals(c.getStatus());
                }
            };
        }
        if (filter.startsWith("WARE:")) {
            final String code = filter.substring("WARE:".length());
            return new ContainerEbene.Filter() {
                @Override
                public boolean zeigt(com.dan.logistikapp.model.ContainerInfo c) {
                    return code.equals(c.getWareCode());
                }
            };
        }
        return null;
    }

    /** Ohne Dienst ist die Karte reine Ansicht: Container lassen sich nicht ziehen. */
    public void setContainerDienst(com.dan.logistikapp.dienst.ContainerDienst dienst) {
        this.dienst = dienst;
    }

    public com.dan.logistikapp.dienst.ContainerDienst getContainerDienst() {
        return dienst;
    }

    /** Neuer Containerstand aus der Datenbank; L&auml;nder und St&auml;dte bleiben. */
    public void setContainerStand(java.util.List<com.dan.logistikapp.model.ContainerInfo> neu) {
        if (modell != null && neu != null) {
            modell = modell.mitContainer(neu);
            hoverContainer = null;
            repaint();
            firePropertyChange("containerStand", null, neu);
        }
    }

    /** Neuer Stadtstand aus Datenbank oder Demo; Container bleiben erhalten. */
    public void setOrte(java.util.List<com.dan.logistikapp.model.Ort> neu) {
        if (modell != null && neu != null) {
            modell = modell.mitOrten(neu);
            hoverContainer = null;
            eingepasst = false;
            if (getWidth() > 0 && getHeight() > 0) {
                allesZeigen();
            }
            repaint();
            firePropertyChange("orte", null, neu);
        }
    }

    /** L&auml;ndercodes, deren Grenze bei Fahrten aufgeleuchtet hat (f&uuml;r Tests). */
    public java.util.List<String> getGeblitzteLaender() {
        return new java.util.ArrayList<String>(ziehen.getGeblitzt());
    }

    /** Grenz&uuml;berg&auml;nge der zuletzt gezeichneten oder gefahrenen Route (f&uuml;r Tests). */
    public java.util.List<Grenzen.Uebergang> getLetzteUebergaenge() {
        return new java.util.ArrayList<Grenzen.Uebergang>(ziehen.getUebergaenge());
    }

    /**
     * Holt einen Container ins Bild: auf seine Stadt zentrieren, n&auml;her heran,
     * und er leuchtet kurz auf. Aufruf aus der Zoll-Liste.
     */
    public void zeigeContainer(int containerId) {
        if (modell == null) {
            return;
        }
        for (com.dan.logistikapp.model.ContainerInfo c : modell.getContainer()) {
            if (c.getContainerId() == containerId) {
                KartenModell.OrtPunkt p = modell.punkt(c.getOrtId());
                if (p == null) {
                    return;
                }
                double m = Math.max(ansicht.getMassstab(), grundMassstab * 2.2);
                ansicht.einpassen(new java.awt.geom.Rectangle2D.Double(p.getX(), p.getY() - 60000, 1, 1), 0);
                ansicht.zoomUm(getWidth() / 2.0, getHeight() / 2.0, m / ansicht.getMassstab());
                bewegt();
                hervorheben = containerId;
                ortHervor = -1;
                hervorhebenBis = System.currentTimeMillis() + 1800;
                toastTakt.start();
                repaint();
                return;
            }
        }
    }

    /** Holt eine Stadt ins Bild und l&auml;sst ihren Stellplatz kurz aufleuchten. Aufruf aus der Bestandsliste. */
    public void zeigeOrt(int ortId) {
        if (modell == null) {
            return;
        }
        KartenModell.OrtPunkt p = modell.punkt(ortId);
        if (p == null) {
            return;
        }
        double m = Math.max(ansicht.getMassstab(), grundMassstab * 2.2);
        ansicht.einpassen(new java.awt.geom.Rectangle2D.Double(p.getX(), p.getY() - 60000, 1, 1), 0);
        ansicht.zoomUm(getWidth() / 2.0, getHeight() / 2.0, m / ansicht.getMassstab());
        bewegt();
        ortHervor = ortId;
        hervorheben = -1;
        hervorhebenBis = System.currentTimeMillis() + 1800;
        toastTakt.start();
        repaint();
    }

    /**
     * Eine Fahrt ohne Maus, mit derselben Animation und demselben Auftrag
     * wie nach dem Loslassen. F&uuml;r die Demo-Szene.
     *
     * @return false, wenn gerade etwas l&auml;uft, der Container beim Zoll steht
     *         oder schon am Ziel ist
     */
    public boolean fahre(int containerId, int nachOrtId) {
        if (modell == null) {
            return false;
        }
        return ziehen.fahre(container(containerId), modell.punkt(nachOrtId));
    }

    /**
     * Zeigt Fahrten als nummerierte Spur. Mit {@code einpassen} passt die
     * Karte den Ausschnitt an, sodass alle Strecken zu sehen sind.
     */
    public void zeigeSpur(java.util.List<com.dan.logistikapp.model.Bewegung> fahrten, boolean einpassen) {
        java.util.List<com.dan.logistikapp.model.Bewegung> alt = getSpur();
        if (modell == null || fahrten == null || fahrten.isEmpty()) {
            spur = null;
            spurContainer = -1;
        } else {
            spur = new Spur(modell, fahrten);
            spurContainer = fahrten.get(fahrten.size() - 1).getContainerId();
            if (einpassen && spur.getRahmen() != null) {
                double vorher = ansicht.getMassstab();
                ansicht.einpassen(spur.getRahmen(), 0.22);
                if (ansicht.getMassstab() > grundMassstab * 3) {
                    ansicht.zoomUm(getWidth() / 2.0, getHeight() / 2.0, grundMassstab * 3 / ansicht.getMassstab());
                }
                if (vorher != ansicht.getMassstab()) {
                    bewegt();
                }
            }
        }
        repaint();
        firePropertyChange("spur", alt, getSpur());
    }

    public void loescheSpur() {
        zeigeSpur(null, false);
    }

    /** Die gezeigte Spur, leer ohne Spur. */
    public java.util.List<com.dan.logistikapp.model.Bewegung> getSpur() {
        return spur == null ? java.util.Collections.<com.dan.logistikapp.model.Bewegung>emptyList() : spur.getFahrten();
    }

    /** Container der gezeigten Spur, oder -1. */
    public int getSpurContainer() {
        return spurContainer;
    }

    /** L&auml;dt die Historie eines Containers im Hintergrund und zeigt sie als Spur. */
    public void zeigeHistorie(final int containerId) {
        if (dienst == null) {
            return;
        }
        final com.dan.logistikapp.dienst.ContainerDienst d = dienst;
        laufendeAuftraege++;
        new javax.swing.SwingWorker<java.util.List<com.dan.logistikapp.model.Bewegung>, Void>() {
            @Override
            protected java.util.List<com.dan.logistikapp.model.Bewegung> doInBackground() throws Exception {
                return d.historie(containerId);
            }

            @Override
            protected void done() {
                laufendeAuftraege--;
                try {
                    java.util.List<com.dan.logistikapp.model.Bewegung> l = get();
                    com.dan.logistikapp.model.ContainerInfo c = container(containerId);
                    String k = c == null ? "#" + containerId : com.dan.logistikapp.model.Iso6346.anzeige(c.getKennung());
                    if (l.isEmpty()) {
                        loescheSpur();
                        toast(k + " ist noch nie gefahren", Toast.Art.HINWEIS);
                    } else {
                        long m = 0;
                        for (com.dan.logistikapp.model.Bewegung b : l) {
                            m += b.getDistanzM();
                        }
                        zeigeSpur(l, true);
                        KartenPanel.this.firePropertyChange("historie", null, l);
                        toast(k + "  ·  " + l.size() + (l.size() == 1 ? " Fahrt" : " Fahrten")
                                + String.format(Locale.GERMANY, "  ·  %,.0f km", m / 1000.0)
                                + "  ·  Esc blendet aus", Toast.Art.HINWEIS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    toast(t instanceof com.dan.logistikapp.dienst.DienstFehler
                            ? Ziehen.fehlerText((com.dan.logistikapp.dienst.DienstFehler) t)
                            : "Historie: " + t, Toast.Art.FEHLER);
                }
            }
        }.execute();
    }

    /**
     * Demo-Betrieb: ein Band oben links sagt, dass nichts gespeichert wird,
     * und {@code text} steht als Untertitel unten. {@code null} beendet das.
     */
    public void setDemoText(String text) {
        this.demoText = text;
        repaint();
    }

    public String getDemoText() {
        return demoText;
    }

    // ------------------------------------------------------------ Zeitreise (Runde 2)

    /**
     * Zeigt einen fr&uuml;heren (oder, mit {@code prognose}, einen geplanten)
     * Stand. Die Karte ist dann nur Ansicht: kein Ziehen, kein Kontextmen&uuml;,
     * keine Auftr&auml;ge. Verschieben und Zoomen gehen weiter.
     *
     * @param stand     Containerstand um {@code zeitMs}
     * @param unterwegs wer gerade zwischen zwei St&auml;dten ist (Wiedergabe)
     * @param geplant   in der Prognose: was danach noch offen w&auml;re, sonst leer
     */
    public void setZeitreise(long zeitMs, java.util.List<com.dan.logistikapp.model.ContainerInfo> stand,
            java.util.List<com.dan.logistikapp.model.Zeitreise.Unterwegs> unterwegs,
            java.util.List<com.dan.logistikapp.model.Auftrag> geplant, boolean prognose) {
        if (modell == null) {
            return;
        }
        if (zeitModell == null && ziehen.getModus() == Ziehen.Modus.ZIEHEN) {
            ziehen.abbrechen();
        }
        java.util.Set<Integer> fort = new java.util.HashSet<Integer>();
        for (com.dan.logistikapp.model.Zeitreise.Unterwegs u : unterwegs) {
            fort.add(u.getContainer().getContainerId());
        }
        java.util.List<com.dan.logistikapp.model.ContainerInfo> da =
                new java.util.ArrayList<com.dan.logistikapp.model.ContainerInfo>();
        for (com.dan.logistikapp.model.ContainerInfo c : stand) {
            if (!fort.contains(c.getContainerId())) {
                da.add(c);
            }
        }
        boolean neu = zeitModell == null;
        this.zeitModell = modell.mitContainer(da);
        this.zeitMs = zeitMs;
        this.zeitPrognose = prognose;
        this.zeitUnterwegs = new java.util.ArrayList<com.dan.logistikapp.model.Zeitreise.Unterwegs>(unterwegs);
        this.zeitPlaene = prognose && geplant != null && !geplant.isEmpty() ? new PlanEbene(zeitModell, geplant) : null;
        hoverContainer = null;
        if (neu) {
            firePropertyChange("zeitreise", false, true);
        }
        repaint();
    }

    /** Zur&uuml;ck zum heutigen Stand. */
    public void beendeZeitreise() {
        if (zeitModell == null) {
            return;
        }
        zeitModell = null;
        zeitUnterwegs = java.util.Collections.emptyList();
        zeitPlaene = null;
        hoverContainer = null;
        firePropertyChange("zeitreise", true, false);
        repaint();
    }

    public boolean istZeitreise() {
        return zeitModell != null;
    }

    /** Das Modell, das gerade gezeichnet wird - heute oder damals. */
    public KartenModell getAnzeigeModell() {
        return zeitModell != null ? zeitModell : modell;
    }

    /** Wie viele Container die Zeitreise gerade fahrend zeigt (f&uuml;r Tests). */
    public int getUnterwegsAnzahl() {
        return zeitUnterwegs.size();
    }

    // ------------------------------------------------------------ Auftraege (Runde 2)

    /**
     * F&auml;hrt einen Auftrag mit derselben Animation wie nach dem Loslassen;
     * geschrieben wird &uuml;ber LOG_API.auftrag_ausfuehren.
     *
     * @return false, wenn gerade etwas l&auml;uft oder die Karte ihn nicht fahren kann
     */
    public boolean fahreAuftrag(com.dan.logistikapp.model.Auftrag a) {
        if (modell == null || a == null || zeitModell != null) {
            return false;
        }
        return ziehen.fahre(container(a.getContainerId()), modell.punkt(a.getNachOrtId()), a.getAuftragId());
    }

    /** Offene Auftr&auml;ge als gestrichelte Vorschau auf der Karte. */
    public void setAuftraege(java.util.List<com.dan.logistikapp.model.Auftrag> l) {
        plaene = (modell == null || l == null) ? null : new PlanEbene(modell, l);
        repaint();
    }

    /** Wie viele offene Auftr&auml;ge die Karte als Vorschau zeichnet (f&uuml;r Tests). */
    public int getPlanStrecken() {
        return plaene == null ? 0 : plaene.strecken();
    }

    /** Bittet die Anwendung, einen Auftrag zu planen; Ziel -1 = noch offen. Ereignis "auftragPlanen". */
    void planen(int containerId, int zielOrtId) {
        firePropertyChange("auftragPlanen", null, new int[] {containerId, zielOrtId});
    }

    /** Ein Auftrag ist mit Animation gefahren. Ereignis "auftragGefahren" (f&uuml;r Tests und Statuszeile). */
    void auftragGefahren(long auftragId) {
        firePropertyChange("auftragGefahren", null, auftragId);
    }

    /** Eine Meldung f&uuml;r den Nutzer, von au&szlig;en ausgel&ouml;st. */
    public void zeigeHinweis(String text) {
        toast(text, Toast.Art.HINWEIS);
    }

    /** Kurze Meldung oben in der Karte. */
    void toast(String text, Toast.Art art) {
        toast = new Toast(text, art, System.currentTimeMillis(), art == Toast.Art.FEHLER ? 6000 : 4000);
        toastTakt.start();
        String alt = hinweis;
        hinweis = text;
        firePropertyChange("hinweis", alt, hinweis);
        repaint();
    }

    /** Die letzte Meldung (f&uuml;r Tests), oder {@code null}. */
    public String getToastText() {
        return toast == null ? null : toast.getText();
    }

    /** Kein Ziehen, keine Fahrt, kein offener Auftrag. */
    public boolean istRuhig() {
        return ziehen.ruht() && laufendeAuftraege == 0;
    }

    /** Zustand des Ziehens als Text (f&uuml;r Tests und Statuszeile). */
    public String getZiehModus() {
        return ziehen.getModus().name();
    }

    /**
     * Gibt einen Container beim Zoll frei - im Hintergrund, danach wird der
     * Stand neu gelesen. Aufruf aus dem Kontextmen&uuml;.
     */
    public void zollFreigeben(final com.dan.logistikapp.model.ContainerInfo c) {
        if (c != null) {
            zollFreigeben(java.util.Collections.singletonList(c));
        }
    }

    /** Mehrere auf einmal - ein Auftrag, eine Meldung. */
    public void zollFreigeben(final java.util.List<com.dan.logistikapp.model.ContainerInfo> liste) {
        if (dienst == null || liste == null || liste.isEmpty()) {
            return;
        }
        final com.dan.logistikapp.dienst.ContainerDienst d = dienst;
        laufendeAuftraege++;
        new javax.swing.SwingWorker<java.util.List<com.dan.logistikapp.model.ContainerInfo>, Void>() {
            private com.dan.logistikapp.dienst.DienstFehler fehler;
            private int frei;

            @Override
            protected java.util.List<com.dan.logistikapp.model.ContainerInfo> doInBackground() {
                for (com.dan.logistikapp.model.ContainerInfo c : liste) {
                    try {
                        d.zollFreigeben(c.getContainerId());
                        frei++;
                    } catch (com.dan.logistikapp.dienst.DienstFehler e) {
                        fehler = e;
                    }
                }
                try {
                    return d.container();
                } catch (com.dan.logistikapp.dienst.DienstFehler e) {
                    fehler = e;
                    return null;
                }
            }

            @Override
            protected void done() {
                laufendeAuftraege--;
                try {
                    java.util.List<com.dan.logistikapp.model.ContainerInfo> neu = get();
                    if (neu != null) {
                        setContainerStand(neu);
                    }
                    if (fehler != null) {
                        toast(Ziehen.fehlerText(fehler), Toast.Art.FEHLER);
                    } else if (liste.size() == 1) {
                        toast(com.dan.logistikapp.model.Iso6346.anzeige(liste.get(0).getKennung())
                                + " vom Zoll freigegeben", Toast.Art.ERFOLG);
                    } else {
                        toast(frei + " Container vom Zoll freigegeben", Toast.Art.ERFOLG);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException e) {
                    toast("Freigabe fehlgeschlagen: " + e.getCause(), Toast.Art.FEHLER);
                }
            }
        }.execute();
    }

    /** Der Container mit dieser Nummer im aktuellen Stand, oder {@code null}. */
    public com.dan.logistikapp.model.ContainerInfo container(int id) {
        if (modell != null) {
            for (com.dan.logistikapp.model.ContainerInfo c : modell.getContainer()) {
                if (c.getContainerId() == id) {
                    return c;
                }
            }
        }
        return null;
    }

    private void kontextmenue(final com.dan.logistikapp.model.ContainerInfo c, int x, int y) {
        javax.swing.JPopupMenu menue = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem titel = new javax.swing.JMenuItem(
                com.dan.logistikapp.model.Iso6346.anzeige(c.getKennung()) + "  \u00B7  " + c.getWare()
                + "  \u00B7  " + c.getOrt());
        titel.setEnabled(false);
        menue.add(titel);
        menue.addSeparator();
        boolean zoll = "ZOLL".equals(c.getStatus());
        javax.swing.JMenuItem frei = new javax.swing.JMenuItem(zoll ? "Zoll freigeben" : "Keine Zollsperre");
        frei.setEnabled(zoll && dienst != null);
        frei.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                zollFreigeben(c);
            }
        });
        menue.add(frei);
        javax.swing.JMenuItem plan = new javax.swing.JMenuItem("Auftrag planen …");
        plan.setEnabled(dienst != null);
        plan.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                planen(c.getContainerId(), -1);
            }
        });
        menue.add(plan);
        javax.swing.JMenuItem hist = new javax.swing.JMenuItem("Historie zeigen");
        hist.setEnabled(dienst != null);
        hist.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                zeigeHistorie(c.getContainerId());
            }
        });
        menue.add(hist);
        if (spur != null) {
            javax.swing.JMenuItem aus = new javax.swing.JMenuItem("Spur ausblenden");
            aus.addActionListener(new java.awt.event.ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    loescheSpur();
                }
            });
            menue.add(aus);
        }
        menue.show(this, x, y);
    }

    /**
     * Containergr&ouml;&szlig;e relativ zur Europa-Ansicht. W&auml;chst beim Hineinzoomen
     * mit der Wurzel des Ma&szlig;stabs - sonst w&auml;ren sie in der &Uuml;bersicht
     * Staub und im Nahbereich Lastwagen -, begrenzt auf 0,75 bis 2.
     */
    public double containerFaktor() {
        double f = Math.sqrt(ansicht.getMassstab() / grundMassstab);
        return Math.max(0.75, Math.min(2.0, f));
    }

    // ================================================================ Zeichnen

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            ansicht.setGroesse(w, h);
            if (modell == null) {
                zeichneMeer(g2, w, h);
                zeichneMeldung(g2, w, h);
                return;
            }
            AffineTransform at = ansicht.transform();
            boolean passt = schicht != null && schicht.getWidth() == w && schicht.getHeight() == h;
            if (!passt || (!interaktiv && !at.equals(schichtTransform))) {
                zeichneSchicht(w, h, at);
            }
            if (at.equals(schichtTransform)) {
                g2.drawImage(schicht, 0, 0, null);
            } else {
                // Uebergang: altes Bild auf den neuen Ausschnitt abbilden
                zeichneMeer(g2, w, h);
                try {
                    AffineTransform alt2neu = new AffineTransform(at);
                    alt2neu.concatenate(schichtTransform.createInverse());
                    Graphics2D gi = (Graphics2D) g2.create();
                    gi.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    gi.drawImage(schicht, alt2neu, null);
                    gi.dispose();
                } catch (java.awt.geom.NoninvertibleTransformException ex) {
                    zeichneSchicht(w, h, at);
                    g2.drawImage(schicht, 0, 0, null);
                }
            }
            if (hoverLand != null) {
                // Hervorhebung als Deckschicht - dafuer muss der Grund nicht neu gezeichnet werden
                g2.setColor(new Color(255, 255, 255, 34));
                g2.fill(bildpfad(hoverLand.getPfad(), at));
            }
            if (zeitModell != null) {
                containerEbene.zeichne(g2, zeitModell, ansicht, containerFaktor(), hoverContainer, -1, -1,
                        containerFilter());
                if (zeitPlaene != null) {
                    zeitPlaene.zeichne(g2, ansicht, zeitMs);
                }
                zeichneUnterwegs(g2);
            } else {
                containerEbene.zeichne(g2, modell, ansicht, containerFaktor(),
                        ziehen.ruht() ? hoverContainer : null, ziehen.unterwegsId(), ziehen.geistId(),
                        containerFilter());
                if (plaene != null) {
                    plaene.zeichne(g2, ansicht, System.currentTimeMillis());
                }
            }
            if (spur != null) {
                spur.zeichne(g2, ansicht);
            }
            zeichneStaedte(g2);
            if (ortHervor >= 0 && System.currentTimeMillis() < hervorhebenBis) {
                KartenModell.OrtPunkt op = modell.punkt(ortHervor);
                if (op != null) {
                    Point2D.Double s = ansicht.zuBildschirm(op.getX(), op.getY());
                    double t = (hervorhebenBis - System.currentTimeMillis()) / 1800.0;
                    double r = 16 + 12 * Math.abs(Math.sin(t * Math.PI * 3));
                    g2.setColor(new Color(0xE6, 0xEE, 0xF1, (int) (220 * Math.min(1, t * 2))));
                    g2.setStroke(new BasicStroke(2.2f));
                    g2.draw(new Ellipse2D.Double(s.x - r, s.y - r, 2 * r, 2 * r));
                }
            }
            if (hervorheben >= 0 && System.currentTimeMillis() < hervorhebenBis) {
                ContainerEbene.Platz pl = containerEbene.platz(hervorheben);
                if (pl != null) {
                    double t = (hervorhebenBis - System.currentTimeMillis()) / 1800.0;
                    double r = 14 + 10 * Math.abs(Math.sin(t * Math.PI * 3));
                    g2.setColor(new Color(0xE8, 0xA3, 0x3C, (int) (220 * Math.min(1, t * 2))));
                    g2.setStroke(new BasicStroke(2.2f));
                    g2.draw(new Ellipse2D.Double(pl.getAnkerX() - r * 1.6, pl.getAnkerY() - r - 4, r * 3.2, r * 2));
                }
            }
            if (zeitModell == null) {
                ziehen.zeichne(g2, containerEbene, containerFaktor());
            }
            zeichneLegende(g2, h);
            zeichneMassstab(g2, w, h);
            if (meldung != null) {
                zeichneMeldung(g2, w, h);
            }
            if (demoText != null) {
                zeichneDemo(g2, w, h);
            } else if (zeitModell != null) {
                zeichneZeitBand(g2, w, h);
            }
            if (toast != null) {
                toast.zeichne(g2, w, System.currentTimeMillis());
            }
        } finally {
            g2.dispose();
        }
    }

    private static void zeichneMeer(Graphics2D g2, int w, int h) {
        g2.setPaint(new GradientPaint(0, 0, KartenFarben.MEER_OBEN, 0, h, KartenFarben.MEER_UNTEN));
        g2.fillRect(0, 0, w, h);
    }

    /** Meer, Gradnetz und L&auml;nder f&uuml;r den aktuellen Ausschnitt ins Ebenenbild. */
    private void zeichneSchicht(int w, int h, AffineTransform at) {
        if (schicht == null || schicht.getWidth() != w || schicht.getHeight() != h) {
            schicht = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        }
        Graphics2D g2 = schicht.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            zeichneMeer(g2, w, h);
            g2.setColor(KartenFarben.GRADNETZ);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(at.createTransformedShape(modell.getGradnetz()));
            zeichneLaender(g2, at, ansicht.weltRahmen());
        } finally {
            g2.dispose();
        }
        schichtTransform = new AffineTransform(at);
    }

    /** Ziehen und Zoomen melden sich hier: bis zur Ruhe wird nur verschoben/skaliert. */
    private void bewegt() {
        interaktiv = true;
        ruhe.restart();
    }

    /** F&uuml;r Tests: sofort scharf zeichnen lassen, als ruhe die Maus. */
    public void ruheJetzt() {
        ruhe.stop();
        interaktiv = false;
    }

    private void zeichneLaender(Graphics2D g2, AffineTransform at, Rectangle2D sicht) {
        List<KartenModell.LandForm> sichtbar = new ArrayList<KartenModell.LandForm>();
        List<Shape> formen = new ArrayList<Shape>();
        for (KartenModell.LandForm f : modell.getFormen()) {
            if (f.getRahmen().intersects(sicht)) {
                sichtbar.add(f);
                formen.add(bildpfad(f.getPfad(), at));
            }
        }
        // Kuestensaum: breiter Strich unter allen Flaechen, bleibt nur zum Meer hin sichtbar
        g2.setColor(KartenFarben.KUESTE);
        g2.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Shape s : formen) {
            g2.draw(s);
        }
        for (int i = 0; i < formen.size(); i++) {
            g2.setColor(KartenFarben.flaeche(sichtbar.get(i).getLand().getZone()));
            g2.fill(formen.get(i));
        }
        g2.setColor(KartenFarben.GRENZE);
        g2.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Shape s : formen) {
            g2.draw(s);
        }
        // Das Inland bekommt einen kraeftigen Rand: es ist der Bezugspunkt aller Zonen
        g2.setColor(KartenFarben.GRENZE_INLAND);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < formen.size(); i++) {
            if (sichtbar.get(i).getLand().getZone() == Zone.INLAND) {
                g2.draw(formen.get(i));
            }
        }
    }

    /**
     * Pfad in Bildpunkten, ohne St&uuml;tzpunkte, die n&auml;her als ein
     * Dreiviertel-Bildpunkt am vorigen liegen. In der Europa-Ansicht f&auml;llt
     * so der gr&ouml;&szlig;te Teil der 58 000 Punkte weg, beim Hineinzoomen kommen
     * sie von selbst zur&uuml;ck. Ringanfang und -ende bleiben immer erhalten.
     */
    static Path2D.Float bildpfad(Path2D pfad, AffineTransform at) {
        Path2D.Float out = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        java.awt.geom.PathIterator it = pfad.getPathIterator(at);
        float[] c = new float[6];
        float lx = 0;
        float ly = 0;
        float mx = 0;
        float my = 0;
        boolean offen = false;
        while (!it.isDone()) {
            int typ = it.currentSegment(c);
            if (typ == java.awt.geom.PathIterator.SEG_MOVETO) {
                out.moveTo(c[0], c[1]);
                lx = mx = c[0];
                ly = my = c[1];
                offen = true;
            } else if (typ == java.awt.geom.PathIterator.SEG_LINETO) {
                float dx = c[0] - lx;
                float dy = c[1] - ly;
                if (dx * dx + dy * dy >= 0.5625f) {
                    out.lineTo(c[0], c[1]);
                    lx = c[0];
                    ly = c[1];
                }
            } else if (typ == java.awt.geom.PathIterator.SEG_CLOSE && offen) {
                if (lx != mx || ly != my) {
                    out.lineTo(mx, my);
                }
                out.closePath();
                offen = false;
            }
            it.next();
        }
        return out;
    }

    private void zeichneStaedte(Graphics2D g2) {
        Rectangle2D sicht = ansicht.weltRahmen();
        for (KartenModell.OrtPunkt p : modell.getPunkte()) {
            if (!sicht.contains(p.getX(), p.getY())) {
                continue;
            }
            Point2D.Double s = ansicht.zuBildschirm(p.getX(), p.getY());
            boolean hover = p == hoverOrt;
            zeichneFuellstand(g2, p, s.x, s.y);
            zeichneMarke(g2, p.getOrt().getZone(), s.x, s.y, hover ? 9 : 7);
            zeichneText(g2, p.getOrt().getName(), schriftStadt,
                    (float) (s.x + (hover ? 13 : 11)), (float) (s.y + 5), KartenFarben.TEXT);
        }
    }

    /**
     * F&uuml;llstand als Bogen um die Stadtmarke, im Uhrzeigersinn ab 12 Uhr.
     * T&uuml;rkis bis drei Viertel, dann Bernstein, voll rot. Nur bei Kapazit&auml;t.
     */
    private void zeichneFuellstand(Graphics2D g2, KartenModell.OrtPunkt p, double x, double y) {
        Integer kap = p.getOrt().getKapazitaetTeu();
        if (kap == null || kap <= 0) {
            return;
        }
        int belegt = getAnzeigeModell().belegtTeu(p.getOrt().getOrtId());
        double anteil = Math.min(1.0, belegt / (double) kap);
        double r = 13;
        java.awt.geom.Arc2D.Double spur = new java.awt.geom.Arc2D.Double(x - r, y - r, 2 * r, 2 * r, 0, 360,
                java.awt.geom.Arc2D.OPEN);
        g2.setStroke(new BasicStroke(5f));
        g2.setColor(KartenFarben.HALO);
        g2.draw(spur);
        g2.setStroke(new BasicStroke(2.6f));
        g2.setColor(new Color(0x2C, 0x4A, 0x57, 200));
        g2.draw(spur);
        if (belegt > 0) {
            Color c = anteil >= 1 ? Ziehen.VOLL : anteil >= 0.75 ? ContainerEbene.ZOLL_FARBE : KartenFarben.GRENZE_INLAND;
            g2.setColor(c);
            g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g2.draw(new java.awt.geom.Arc2D.Double(x - r, y - r, 2 * r, 2 * r, 90, -360 * anteil,
                    java.awt.geom.Arc2D.OPEN));
        }
    }

    /** Inland Kreis, EU Ring, Drittland Raute - lesbar auch ohne Farbe. */
    public static void zeichneMarke(Graphics2D g2, Zone z, double x, double y, double r) {
        Color c = KartenFarben.marke(z);
        Shape form;
        switch (z) {
            case INLAND:
                form = new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
                break;
            case EU:
                form = new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
                break;
            default:
                Path2D.Double d = new Path2D.Double();
                d.moveTo(x, y - r * 1.25);
                d.lineTo(x + r * 1.25, y);
                d.lineTo(x, y + r * 1.25);
                d.lineTo(x - r * 1.25, y);
                d.closePath();
                form = d;
        }
        g2.setStroke(new BasicStroke(4f));
        g2.setColor(KartenFarben.HALO);
        g2.draw(form);
        if (z == Zone.EU) {
            g2.setColor(KartenFarben.MEER_OBEN);
            g2.fill(form);
            g2.setColor(c);
            g2.setStroke(new BasicStroke(3f));
            g2.draw(new Ellipse2D.Double(x - r + 1.5, y - r + 1.5, 2 * r - 3, 2 * r - 3));
        } else {
            g2.setColor(c);
            g2.fill(form);
        }
    }

    /** Text mit dunklem Saum, damit er auf jeder Fl&auml;che lesbar bleibt. */
    private static void zeichneText(Graphics2D g2, String text, Font f, float x, float y, Color farbe) {
        TextLayout tl = new TextLayout(text, f, g2.getFontRenderContext());
        Shape umriss = tl.getOutline(AffineTransform.getTranslateInstance(x, y));
        g2.setColor(KartenFarben.HALO);
        g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(umriss);
        g2.setColor(farbe);
        g2.fill(umriss);
    }

    private void zeichneLegende(Graphics2D g2, int h) {
        int zeilen = Zone.values().length;
        List<com.dan.logistikapp.model.Ware> waren = modell.getWaren();
        double bx = 16;
        double bh = 30 + zeilen * 22 + (waren.isEmpty() ? 0 : 26 + waren.size() * 20);
        double by = h - bh - 16;
        g2.setColor(KartenFarben.TAFEL);
        RoundRectangle2D tafel = new RoundRectangle2D.Double(bx, by, 176, bh, 10, 10);
        g2.fill(tafel);
        g2.setColor(KartenFarben.TAFEL_RAND);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(tafel);
        g2.setFont(schriftTitel);
        g2.setColor(KartenFarben.TEXT_LEISE);
        g2.drawString("ZONE", (float) bx + 12, (float) by + 19);
        int i = 0;
        for (Zone z : Zone.values()) {
            double y = by + 38 + i * 22;
            g2.setColor(KartenFarben.flaeche(z));
            g2.fill(new RoundRectangle2D.Double(bx + 12, y - 8, 22, 14, 4, 4));
            zeichneMarke(g2, z, bx + 23, y - 1, 4.5);
            g2.setFont(schriftKlein);
            g2.setColor(KartenFarben.TEXT);
            g2.drawString(z.anzeige(), (float) bx + 44, (float) y + 3);
            i++;
        }
        if (!waren.isEmpty()) {
            double wy = by + 38 + zeilen * 22 + 8;
            g2.setFont(schriftTitel);
            g2.setColor(KartenFarben.TEXT_LEISE);
            g2.drawString("WARE", (float) bx + 12, (float) wy);
            int k = 0;
            for (com.dan.logistikapp.model.Ware w : waren) {
                double y = wy + 18 + k * 20;
                g2.setColor(w.getFarbe());
                g2.fill(new java.awt.geom.Rectangle2D.Double(bx + 12, y - 8, 22, 10));
                g2.setColor(new Color(0, 0, 0, 90));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new java.awt.geom.Rectangle2D.Double(bx + 12, y - 8, 22, 10));
                g2.setFont(schriftKlein);
                g2.setColor(KartenFarben.TEXT);
                g2.drawString(w.getName(), (float) bx + 44, (float) y + 1);
                k++;
            }
        }
    }

    /**
     * Ma&szlig;stabsleiste. LAEA ist fl&auml;chentreu, nicht l&auml;ngentreu - die
     * Leiste gilt f&uuml;r die Bildmitte, am Rand weicht sie ein wenig ab.
     */
    private void zeichneMassstab(Graphics2D g2, int w, int h) {
        double mProPx = 1 / ansicht.getMassstab();
        double[] stufen = {1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000};
        double km = stufen[stufen.length - 1];
        for (double s : stufen) {
            if (s * 1000 / mProPx >= 110) {
                km = s;
                break;
            }
        }
        double len = km * 1000 / mProPx;
        double x1 = w - 24 - len;
        double y = h - 30;
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g2.setColor(KartenFarben.HALO);
        g2.draw(new java.awt.geom.Line2D.Double(x1, y, x1 + len, y));
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(KartenFarben.TEXT);
        g2.draw(new java.awt.geom.Line2D.Double(x1, y, x1 + len, y));
        g2.draw(new java.awt.geom.Line2D.Double(x1, y - 5, x1, y + 5));
        g2.draw(new java.awt.geom.Line2D.Double(x1 + len, y - 5, x1 + len, y + 5));
        String t = (km >= 1 ? String.format(Locale.GERMANY, "%.0f km", km) : "");
        zeichneText(g2, t, schriftKlein, (float) (x1 + len / 2 - 18), (float) y - 9, KartenFarben.TEXT);
        g2.setFont(schriftKlein);
        g2.setColor(KartenFarben.TEXT_LEISE);
        g2.drawString("ETRS89-LAEA · EPSG:3035", (float) (w - 24 - 150), (float) y + 18);
    }

    /** Bernsteinfarbener Rahmen, Band oben links, Untertitel unten mittig. */
    private void zeichneDemo(Graphics2D g2, int w, int h) {
        Color z = ContainerEbene.ZOLL_FARBE;
        g2.setColor(new Color(z.getRed(), z.getGreen(), z.getBlue(), 150));
        g2.setStroke(new BasicStroke(3f));
        g2.drawRect(1, 1, w - 3, h - 3);
        g2.setFont(schriftTitel);
        FontMetrics fm = g2.getFontMetrics();
        String band = "DEMO  ·  NICHTS WIRD GESPEICHERT";
        int bw = fm.stringWidth(band) + 24;
        g2.setColor(z);
        g2.fill(new RoundRectangle2D.Double(14, 14, bw, 24, 8, 8));
        g2.setColor(new Color(0x0A1B26));
        g2.drawString(band, 26, 30);
        if (demoText.isEmpty()) {
            return;
        }
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, 16);
        g2.setFont(f);
        fm = g2.getFontMetrics();
        int tw = fm.stringWidth(demoText);
        double bx = (w - tw) / 2.0 - 20;
        double by = h - 74;
        g2.setColor(KartenFarben.TAFEL);
        g2.fill(new RoundRectangle2D.Double(bx, by, tw + 40, 38, 12, 12));
        g2.setColor(new Color(z.getRed(), z.getGreen(), z.getBlue(), 200));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new RoundRectangle2D.Double(bx, by, tw + 40, 38, 12, 12));
        g2.setColor(KartenFarben.TEXT);
        g2.drawString(demoText, (float) bx + 20, (float) by + 25);
    }

    /** Farbe der Zeitreise: ein ruhiges Blauviolett; die Prognose t&uuml;rkis. */
    public static final Color ZEIT_FARBE = new Color(0xA7B6FF);
    public static final Color PROGNOSE_FARBE = new Color(0x7FD6C8);

    /** Rahmen und Band: welcher Zeitpunkt, und dass hier nichts geht. */
    private void zeichneZeitBand(Graphics2D g2, int w, int h) {
        Color z = zeitPrognose ? PROGNOSE_FARBE : ZEIT_FARBE;
        g2.setColor(new Color(z.getRed(), z.getGreen(), z.getBlue(), 140));
        if (zeitPrognose) {
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[] {14f, 8f}, 0f));
        } else {
            g2.setStroke(new BasicStroke(3f));
        }
        g2.drawRect(1, 1, w - 3, h - 3);
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("EE dd.MM.yyyy  ·  HH:mm:ss", Locale.GERMANY);
        String band = (zeitPrognose ? "PROGNOSE  ·  " : "ZEITREISE  ·  ")
                + f.format(new java.util.Date(zeitMs)).toUpperCase(Locale.GERMANY) + "  ·  NUR ANSICHT";
        g2.setFont(schriftTitel);
        FontMetrics fm = g2.getFontMetrics();
        int bw = fm.stringWidth(band) + 24;
        g2.setColor(z);
        g2.fill(new RoundRectangle2D.Double(14, 14, bw, 24, 8, 8));
        g2.setColor(new Color(0x0A1B26));
        g2.drawString(band, 26, 30);
        if (zeitPrognose) {
            g2.setFont(schriftKlein);
            zeichneText(g2, "wenn die offenen Aufträge wie geplant fahren - Zoll und Platz bleiben außen vor",
                    schriftKlein, 16, 54, KartenFarben.TEXT_LEISE);
        }
    }

    /** W&auml;hrend der Wiedergabe: Container auf dem Gro&szlig;kreis zwischen zwei St&auml;dten. */
    private void zeichneUnterwegs(Graphics2D g2) {
        double faktor = containerFaktor();
        for (com.dan.logistikapp.model.Zeitreise.Unterwegs u : zeitUnterwegs) {
            KartenModell.OrtPunkt a = modell.punkt(u.getVonOrtId());
            KartenModell.OrtPunkt b = modell.punkt(u.getNachOrtId());
            if (a == null || b == null) {
                continue;
            }
            double[][] r = com.dan.logistikapp.geo.Geodaesie.grosskreis(a.getOrt().getLaenge(), a.getOrt().getBreite(),
                    b.getOrt().getLaenge(), b.getOrt().getBreite(), 32);
            Path2D.Double p = new Path2D.Double();
            double[] xy = new double[2];
            for (int i = 0; i < r.length; i++) {
                com.dan.logistikapp.geo.Laea3035.vor(r[i][0], r[i][1], xy);
                Point2D.Double s = ansicht.zuBildschirm(xy[0], xy[1]);
                if (i == 0) {
                    p.moveTo(s.x, s.y);
                } else {
                    p.lineTo(s.x, s.y);
                }
            }
            Color c = u.isPrognose() ? PROGNOSE_FARBE : ZEIT_FARBE;
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                    new float[] {4f, 5f}, 0f));
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 150));
            g2.draw(p);
            double f = Ziehen.weich(Math.max(0, Math.min(1, u.getAnteil())));
            double pos = f * (r.length - 1);
            int i = Math.min(r.length - 2, (int) Math.floor(pos));
            double t = pos - i;
            double[] v = com.dan.logistikapp.geo.Laea3035.vor(r[i][0], r[i][1]);
            double[] n = com.dan.logistikapp.geo.Laea3035.vor(r[i + 1][0], r[i + 1][1]);
            Point2D.Double s = ansicht.zuBildschirm(v[0] + (n[0] - v[0]) * t, v[1] + (n[1] - v[1]) * t);
            Point2D.Double sv = ansicht.zuBildschirm(v[0], v[1]);
            Point2D.Double sn = ansicht.zuBildschirm(n[0], n[1]);
            double gier = Ziehen.gierAusBild(sn.x - sv.x, sn.y - sv.y);
            double hub = 10 * Math.sin(Math.PI * f) + 2;
            containerEbene.zeichneFrei(g2, u.getContainer(), s.x, s.y, faktor, hub, gier);
        }
    }

    private void zeichneMeldung(Graphics2D g2, int w, int h) {
        g2.setFont(schriftStadt);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(meldung);
        g2.setColor(KartenFarben.TAFEL);
        g2.fill(new RoundRectangle2D.Double((w - tw) / 2.0 - 16, h / 2.0 - 22, tw + 32, 36, 10, 10));
        g2.setColor(KartenFarben.TEXT);
        g2.drawString(meldung, (w - tw) / 2f, h / 2f + 1);
    }

    // ================================================================ Maus

    private void aktualisiereHover(int px, int py) {
        if (modell == null || !ziehen.ruht()) {
            return;
        }
        KartenModell.OrtPunkt bester = null;
        double bestD = TREFFER_PX;
        for (KartenModell.OrtPunkt p : modell.getPunkte()) {
            Point2D.Double s = ansicht.zuBildschirm(p.getX(), p.getY());
            double d = s.distance(px, py);
            if (d <= bestD) {
                bestD = d;
                bester = p;
            }
        }
        com.dan.logistikapp.model.ContainerInfo cont = containerEbene.treffer(px, py);
        if (cont != null) {
            bester = null;
        }
        Point2D.Double welt = ansicht.zuWelt(px, py);
        KartenModell.LandForm land = (cont == null) ? modell.landBei(welt.x, welt.y) : null;
        boolean neu = bester != hoverOrt || land != hoverLand || cont != hoverContainer;
        hoverContainer = cont;
        hoverOrt = bester;
        hoverLand = land;
        if (neu) {
            String alt = hinweis;
            hinweis = hinweisText();
            repaint();
            firePropertyChange("hinweis", alt, hinweis);
        }
    }

    private String hinweisText() {
        if (hoverContainer != null) {
            com.dan.logistikapp.model.ContainerInfo c = hoverContainer;
            return com.dan.logistikapp.model.Iso6346.anzeige(c.getKennung()) + " \u00B7 " + c.getTypCode()
                    + " \u00B7 " + c.getWare() + " \u00B7 " + c.getGroesseFuss() + " Fu\u00DF \u00B7 "
                    + c.getOrt() + " \u00B7 " + ("ZOLL".equals(c.getStatus()) ? "beim Zoll" : "bereit");
        }
        if (hoverOrt != null) {
            Ort o = hoverOrt.getOrt();
            String platz = o.getKapazitaetTeu() == null ? ""
                    : String.format(Locale.GERMANY, " · belegt %d von %d TEU",
                            getAnzeigeModell().belegtTeu(o.getOrtId()),
                            o.getKapazitaetTeu());
            return String.format(Locale.GERMANY, "%s · %s · %s%s · %.4f° N  %.4f° O",
                    o.getName(), o.getLand(), o.getZone().anzeige(), platz, o.getBreite(), o.getLaenge());
        }
        if (hoverLand != null) {
            com.dan.logistikapp.model.Land l = hoverLand.getLand();
            StringBuilder sb = new StringBuilder(l.getName()).append(" · ").append(l.getZone().anzeige());
            sb.append(l.isZollunion() ? " · Zollunion" : " · außerhalb der Zollunion");
            if (l.isSchengen()) {
                sb.append(" · Schengen");
            }
            return sb.toString();
        }
        return "";
    }

    private final class Maus extends MouseAdapter {
        private int lx;
        private int ly;
        private boolean schiebt;
        private com.dan.logistikapp.model.ContainerInfo gedrueckterContainer;
        private int druckX;
        private int druckY;

        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            lx = e.getX();
            ly = e.getY();
            schiebt = false;
            if (modell == null) {
                return;
            }
            if (zeitModell != null) {
                // Zeitreise: nur schauen - verschieben ja, ziehen und Menue nein
                schiebt = true;
                return;
            }
            com.dan.logistikapp.model.ContainerInfo c = ziehen.ruht()
                    ? containerEbene.treffer(e.getX(), e.getY()) : null;
            gedrueckterContainer = c;
            druckX = e.getX();
            druckY = e.getY();
            if (e.isPopupTrigger()) {
                if (c != null) {
                    kontextmenue(c, e.getX(), e.getY());
                }
                return;
            }
            if (javax.swing.SwingUtilities.isLeftMouseButton(e) && ziehen.druecken(c, e.getX(), e.getY())) {
                if (ziehen.getModus() == Ziehen.Modus.ZIEHEN && spur != null) {
                    loescheSpur();
                }
                return;
            }
            schiebt = true;
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (zeitModell != null) {
                schiebt = false;
                return;
            }
            if (e.isPopupTrigger() && modell != null && ziehen.ruht()) {
                gedrueckterContainer = null;
                com.dan.logistikapp.model.ContainerInfo c = containerEbene.treffer(e.getX(), e.getY());
                if (c != null) {
                    kontextmenue(c, e.getX(), e.getY());
                    return;
                }
            }
            if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                if (gedrueckterContainer != null
                        && Math.hypot(e.getX() - druckX, e.getY() - druckY) <= 5
                        && (ziehen.getModus() == Ziehen.Modus.ZIEHEN || ziehen.ruht())) {
                    if (ziehen.getModus() == Ziehen.Modus.ZIEHEN) {
                        ziehen.abbrechen();
                    }
                    int id = gedrueckterContainer.getContainerId();
                    gedrueckterContainer = null;
                    firePropertyChange("containerDetails", null, Integer.valueOf(id));
                    schiebt = false;
                    return;
                }
                // Shift beim Loslassen: planen statt fahren
                ziehen.loslassen(e.isShiftDown());
            }
            gedrueckterContainer = null;
            schiebt = false;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (ziehen.getModus() == Ziehen.Modus.ZIEHEN) {
                ziehen.ziehen(e.getX(), e.getY());
                return;
            }
            if (!schiebt) {
                return;
            }
            ansicht.verschieben(e.getX() - lx, e.getY() - ly);
            lx = e.getX();
            ly = e.getY();
            bewegt();
            repaint();
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            aktualisiereHover(e.getX(), e.getY());
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && ziehen.ruht()
                    && containerEbene.treffer(e.getX(), e.getY()) == null) {
                allesZeigen();
            }
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            double f = Math.pow(1.2, -e.getPreciseWheelRotation());
            ansicht.zoomUm(e.getX(), e.getY(), f);
            bewegt();
            aktualisiereHover(e.getX(), e.getY());
            repaint();
        }
    }
}
