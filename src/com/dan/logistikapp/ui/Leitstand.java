package com.dan.logistikapp.ui;

import com.dan.logistikapp.dienst.ContainerDienst;
import com.dan.logistikapp.karte.KartenModell;
import com.dan.logistikapp.karte.KartenPanel;
import com.dan.logistikapp.model.Bestand;
import com.dan.logistikapp.model.Bewegung;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.ContainerAnlage;
import com.dan.logistikapp.model.Auftrag;
import com.dan.logistikapp.model.Iso6346;
import com.dan.logistikapp.model.Ort;
import com.dan.logistikapp.model.OrtAnlage;
import com.dan.logistikapp.model.ZollFall;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;

/**
 * Verbindet die Karte mit der Seitenleiste: Zoll, Bestand, Fahrten und die
 * Demo-Szene. Alles, was hier gelesen wird, kommt vom Dienst, den die Karte
 * gerade hat - w&auml;hrend der Demo also vom Spiel-Dienst, sonst aus DEMO.
 *
 * <p>Ohne Fenster und Datenbank benutzbar; die Tests bauen genau diese
 * Klasse mit einem Dienst im Speicher auf.</p>
 *
 * @author Dan
 */
public final class Leitstand {

    /** Wie viele Fahrten die Liste zeigt. */
    public static final int FAHRTEN_MAX = 60;

    /** Wohin Lesefehler gehen (Statuszeile). */
    public interface Meldung {
        void fehler(String text);
    }

    private final KartenPanel karte;
    private final ZollTafel zoll = new ZollTafel();
    private final BestandTafel bestand = new BestandTafel();
    private final FahrtenTafel fahrten = new FahrtenTafel();
    private final KostenTafel kosten = new KostenTafel();
    private final AuftragsTafel auftraege = new AuftragsTafel();
    private final SeitenLeiste seite = new SeitenLeiste(zoll, bestand, fahrten, auftraege, kosten);
    private final Zeitleiste zeitleiste = new Zeitleiste();
    /** Die Demo spielt gerade ihre eigenen Fahrten auf der Zeitleiste ab. */
    private boolean demoReise;
    /** Zeitraffer der Demo-Wiedergabe: ihre Fahrten liegen Sekunden auseinander. */
    static final int DEMO_TEMPO = 8;
    private com.dan.logistikapp.model.Zeitreise zeitreise;
    /** Wann die Seitenleiste zuletzt f&uuml;r die Zeitreise neu gerechnet wurde, und f&uuml;r welchen Stand. */
    private long tafelnMs;
    private String tafelnFuer;
    private final AuftragsLauf lauf;
    private final javax.swing.Timer abfrage;
    private String letzteMarke;
    private PlanerFenster planerFenster;
    private ContainerFenster containerFenster;
    private StadtFenster stadtFenster;
    private ContainerDetailFenster containerDetailFenster;
    private CsvFenster csvFenster;
    private final java.util.Set<Integer> favoritenOrte = new java.util.HashSet<Integer>();
    private final java.util.Set<Integer> favoritenContainer = new java.util.HashSet<Integer>();
    private final java.util.prefs.Preferences favoritenSpeicher =
            java.util.prefs.Preferences.userNodeForPackage(Leitstand.class);

    /** Zeigt den Planer als Dialog; liefert ihn zur&uuml;ck, wenn geplant werden soll, sonst {@code null}. */
    public interface PlanerFenster {
        AuftragPlaner zeige(AuftragPlaner planer);
    }

    /** Zeigt den Dialog zum Anlegen eines Containers. */
    public interface ContainerFenster {
        ContainerAnlage zeige(KartenModell modell);
    }

    /** Zeigt den Dialog zum Anlegen einer Stadt. */
    public interface StadtFenster {
        OrtAnlage zeige(KartenModell modell);
    }

    /** Zeigt die Details eines Containers mit Historie und Aufträgen. */
    public interface ContainerDetailFenster {
        void zeige(ContainerInfo container, List<Bewegung> historie, List<Auftrag> auftraege,
                boolean favorit, Runnable favoritAktion);
    }

    /** Dateiauswahl für den CSV-Import und -Export. */
    public interface CsvFenster {
        java.io.File exportOrdner();
        java.io.File importDatei();
    }
    private final DemoSzene demo;
    private Meldung meldung;
    private int laufend;

    public Leitstand(KartenPanel karte, long demoPauseMs) {
        this.karte = karte;
        ladeFavoriten();
        this.demo = new DemoSzene(karte, demoPauseMs);
        this.lauf = new AuftragsLauf(karte, this);
        this.abfrage = new javax.swing.Timer(10000, new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                abfragen();
            }
        });
        verdrahte();
    }

    public SeitenLeiste getSeitenLeiste() {
        return seite;
    }

    public ZollTafel getZollTafel() {
        return zoll;
    }

    public BestandTafel getBestandTafel() {
        return bestand;
    }

    public FahrtenTafel getFahrtenTafel() {
        return fahrten;
    }

    public KostenTafel getKostenTafel() {
        return kosten;
    }

    public AuftragsTafel getAuftragsTafel() {
        return auftraege;
    }

    public Zeitleiste getZeitleiste() {
        return zeitleiste;
    }

    /** Die geladene Historie (f&uuml;r Tests), oder {@code null}. */
    public com.dan.logistikapp.model.Zeitreise getZeitreise() {
        return zeitreise;
    }

    public AuftragsLauf getAuftragsLauf() {
        return lauf;
    }

    public void setPlanerFenster(PlanerFenster f) {
        this.planerFenster = f;
    }

    public void setContainerFenster(ContainerFenster f) {
        this.containerFenster = f;
    }

    public void setStadtFenster(StadtFenster f) {
        this.stadtFenster = f;
    }

    public void setContainerDetailFenster(ContainerDetailFenster f) {
        this.containerDetailFenster = f;
    }

    public void setCsvFenster(CsvFenster f) {
        this.csvFenster = f;
    }

    /**
     * Startet den Auftragslauf (1 s) und die Abfrage der Standmarke. Die
     * Abfrage sieht, was der Job oder ein zweiter Arbeitsplatz getan hat.
     */
    public void starteHintergrund(int abfrageMs) {
        abfrage.setDelay(abfrageMs);
        abfrage.setInitialDelay(abfrageMs);
        abfrage.start();
        lauf.start();
    }

    public void stoppeHintergrund() {
        abfrage.stop();
        lauf.stopp();
    }

    public DemoSzene getDemo() {
        return demo;
    }

    public void setMeldung(Meldung m) {
        this.meldung = m;
    }

    /** Kein Lesen unterwegs (f&uuml;r Tests). */
    public boolean istRuhig() {
        return laufend == 0;
    }

    private void verdrahte() {
        zoll.setAktion(new ZollTafel.Aktion() {
            @Override
            public void freigeben(int id) {
                karte.zollFreigeben(karte.container(id));
            }

            @Override
            public void alleFreigeben(List<Integer> ids) {
                List<ContainerInfo> l = new java.util.ArrayList<ContainerInfo>();
                for (int id : ids) {
                    if (karte.container(id) != null) {
                        l.add(karte.container(id));
                    }
                }
                karte.zollFreigeben(l);
            }

            @Override
            public void zeigen(int id) {
                karte.zeigeContainer(id);
            }
        });
        auftraege.setAktion(new AuftragsTafel.Aktion() {
            @Override
            public void stornieren(long auftragId) {
                storniere(auftragId);
            }

            @Override
            public void zeigen(int containerId) {
                karte.zeigeContainer(containerId);
            }
        });
        karte.addPropertyChangeListener("auftragPlanen", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                int[] w = (int[]) e.getNewValue();
                planerOeffnen(w[0], w[1]);
            }
        });
        karte.addPropertyChangeListener("containerDetails", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                containerDetailsOeffnen(((Integer) e.getNewValue()).intValue());
            }
        });
        bestand.setAktion(new BestandTafel.Aktion() {
            @Override
            public void zeigeOrt(int ortId) {
                karte.zeigeOrt(ortId);
            }

            @Override
            public void favoritOrt(int ortId) {
                if (!favoritenOrte.add(ortId)) {
                    favoritenOrte.remove(ortId);
                }
                speichereFavoriten();
                favoritenAktualisieren();
            }
        });
        seite.setFavoritenAktion(new FavoritenTafel.Aktion() {
            @Override
            public void zeigeOrt(int id) {
                karte.zeigeOrt(id);
            }

            @Override
            public void zeigeContainer(int id) {
                karte.zeigeContainer(id);
            }
        });
        fahrten.setAktion(new FahrtenTafel.Aktion() {
            @Override
            public void zeigeFahrt(Bewegung b, List<Bewegung> alle, boolean historie) {
                if (historie) {
                    karte.zeigeSpur(alle, false);
                    karte.zeigeOrt(b.getNachOrtId());
                } else {
                    karte.zeigeSpur(Collections.singletonList(b), true);
                }
            }

            @Override
            public void alleFahrten() {
                fahrten.setLetzte(Collections.<Bewegung>emptyList());
                karte.loescheSpur();
                fahrtenLaden();
            }
        });
        seite.setDemoAktion(new Runnable() {
            @Override
            public void run() {
                if (demo.laeuft()) {
                    demo.stopp();
                    seite.setDemoZustand("Demo endet nach diesem Schritt …", true);
                } else {
                    zeitleiste.setGesperrt(true);
                    if (demo.start(zoll.getFaelle())) {
                        seite.setDemoZustand("Demo beenden", true);
                    } else {
                        zeitleiste.setGesperrt(false);
                    }
                }
            }
        });
        seite.setContainerAktion(new Runnable() {
            @Override
            public void run() {
                containerOeffnen();
            }
        });
        seite.setStadtAktion(new Runnable() {
            @Override
            public void run() {
                stadtOeffnen();
            }
        });
        seite.setCsvAktion(new Runnable() {
            @Override public void run() { csvExportOeffnen(); }
        }, new Runnable() {
            @Override public void run() { csvImportOeffnen(); }
        });
        seite.setKartenFilterAktion(new KartenFilter.FilterAktion() {
            @Override
            public void filter(String wert) {
                karte.setContainerFilter(wert);
            }
        });
        demo.setBeobachter(new DemoSzene.Beobachter() {
            @Override
            public void schritt(int nr, int von, String text) {
                seite.setDemoZustand("Demo beenden  ·  " + nr + "/" + von, true);
            }

            @Override
            public void ende(boolean abgebrochen) {
                seite.setDemoZustand("Demo abspielen", false);
                demoReise = false;
                zeitleiste.setTempoWert(Zeitleiste.TEMPO[0]);
                zeitleiste.setGesperrt(false);
                // lief gerade die Wiedergabe: zurueck zu jetzt, damit der echte Stand wieder dasteht
                zeitleiste.jetzt();
            }
        });
        demo.setBuehne(new DemoSzene.Buehne() {
            @Override
            public void reiter(int r) {
                seite.zeige(r);
            }

            @Override
            public boolean zeitreise() {
                return demoZeitreise();
            }

            @Override
            public boolean zeitreiseLaeuft() {
                if (demoReise && !zeitleiste.spielt()) {
                    // Wiedergabe vorbei: zurueck zu jetzt, die Zeitleiste gehoert wieder der Demo-Sperre
                    zeitleiste.jetzt();
                    demoReise = false;
                    zeitleiste.setTempoWert(Zeitleiste.TEMPO[0]);
                    zeitleiste.setGesperrt(true);
                }
                return demoReise;
            }
        });
        karte.addPropertyChangeListener("zeitreiseVerlassen", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                if (!demo.laeuft()) {
                    zeitleiste.jetzt();
                }
            }
        });
        zeitleiste.setBeobachter(new Zeitleiste.Beobachter() {
            @Override
            public void zeit(Long ms, long fahrtDauerMs) {
                zeigeZeit(ms, fahrtDauerMs);
            }
        });
        karte.addPropertyChangeListener("containerStand", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                neuLaden();
            }
        });
        karte.addPropertyChangeListener("orte", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                neuLaden();
            }
        });
        karte.addPropertyChangeListener("historie", new PropertyChangeListener() {
            @Override
            @SuppressWarnings("unchecked")
            public void propertyChange(PropertyChangeEvent e) {
                List<Bewegung> l = (List<Bewegung>) e.getNewValue();
                if (l != null && !l.isEmpty()) {
                    fahrten.setHistorie(l.get(0).getKennungAnzeige(), l);
                    seite.zeige(SeitenLeiste.FAHRTEN);
                }
            }
        });
        karte.addPropertyChangeListener("spur", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                if (karte.getSpur().isEmpty() && fahrten.istHistorie()) {
                    fahrtenLaden();
                }
            }
        });
    }

    /** Dialog und Speichern eines neuen Containers. */
    private void containerOeffnen() {
        if (demo.laeuft() || karte.istZeitreise()) {
            karte.zeigeHinweis("Während der Demo oder Zeitreise können keine Container angelegt werden");
            return;
        }
        if (karte.getContainerDienst() == null || karte.getModell() == null || containerFenster == null) {
            return;
        }
        ContainerAnlage anlage = containerFenster.zeige(karte.getModell());
        if (anlage != null) {
            containerAnlegen(anlage);
        }
    }

    private void csvExportOeffnen() {
        if (demo.laeuft() || karte.istZeitreise()) {
            karte.zeigeHinweis("Während der Demo oder Zeitreise ist der CSV-Export gesperrt");
            return;
        }
        if (karte.getModell() == null || csvFenster == null) {
            return;
        }
        final java.io.File ordner = csvFenster.exportOrdner();
        if (ordner == null) {
            return;
        }
        final KartenModell m = karte.getModell();
        laufend++;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                StammdatenCsv.exportiere(ordner, m);
                return null;
            }

            @Override
            protected void done() {
                laufend--;
                try {
                    get();
                    karte.zeigeHinweis("CSV exportiert: staedte.csv, waren.csv und container.csv");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("CSV-Export", e);
                }
            }
        }.execute();
    }

    private void csvImportOeffnen() {
        if (demo.laeuft() || karte.istZeitreise()) {
            karte.zeigeHinweis("Während der Demo oder Zeitreise ist der CSV-Import gesperrt");
            return;
        }
        if (karte.getContainerDienst() == null || csvFenster == null) {
            return;
        }
        final java.io.File datei = csvFenster.importDatei();
        if (datei == null) {
            return;
        }
        final ContainerDienst d = karte.getContainerDienst();
        laufend++;
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                StammdatenCsv.ImportDaten daten = StammdatenCsv.importiere(datei);
                int neu = 0;
                int containerNeu = 0;
                List<String> fehler = new java.util.ArrayList<String>();
                for (OrtAnlage a : daten.orte) {
                    try {
                        d.ortAnlegen(a);
                        neu++;
                    } catch (com.dan.logistikapp.dienst.DienstFehler e) {
                        fehler.add(a.getName() + ": " + e.getMessage());
                    }
                }
                Map<String, Ort> nachName = new HashMap<String, Ort>();
                for (Ort o : d.orte()) {
                    nachName.put(o.getName().toLowerCase(java.util.Locale.GERMANY), o);
                }
                for (StammdatenCsv.ContainerImport ci : daten.container) {
                    Ort o = nachName.get(ci.getOrtName().toLowerCase(java.util.Locale.GERMANY));
                    if (o == null) {
                        fehler.add(ci.getOrtName() + ": Stadt für Container nicht gefunden");
                        continue;
                    }
                    if (ci.getAnzahl() <= 0 || ci.getAnzahl() > 1000) {
                        fehler.add(ci.getOrtName() + ": Anzahl muss zwischen 1 und 1000 liegen");
                        continue;
                    }
                    for (int i = 0; i < ci.getAnzahl(); i++) {
                        try {
                            d.containerAnlegen(new com.dan.logistikapp.model.ContainerAnlage(null,
                                    ci.getGroesseFuss(), ci.getWareCode(), o.getOrtId()));
                            containerNeu++;
                        } catch (com.dan.logistikapp.dienst.DienstFehler e) {
                            fehler.add(ci.getOrtName() + ": " + e.getMessage());
                        }
                    }
                }
                return new Object[] {Integer.valueOf(neu), Integer.valueOf(containerNeu), fehler, d.orte(), d.container()};
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                laufend--;
                try {
                    Object[] r = get();
                    karte.setOrte((List<Ort>) r[2]);
                    List<String> fehler = (List<String>) r[2];
                    String text = r[0] + " Städte und " + r[1] + " Container importiert";
                    if (!fehler.isEmpty()) {
                        text += " · " + fehler.size() + " übersprungen";
                    }
                    karte.setContainerStand((List<com.dan.logistikapp.model.ContainerInfo>) r[4]);
                    karte.zeigeHinweis(text);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("CSV-Import", e);
                }
            }
        }.execute();
    }

    /** Liest Historie und Aufträge asynchron und öffnet danach das Detailfenster. */
    private void containerDetailsOeffnen(final int containerId) {
        final ContainerInfo c = karte.container(containerId);
        if (c == null || containerDetailFenster == null) {
            return;
        }
        final ContainerDienst d = karte.getContainerDienst();
        if (d == null) {
            containerDetailFenster.zeige(c, Collections.<Bewegung>emptyList(), Collections.<Auftrag>emptyList(),
                    favoritenContainer.contains(containerId), new Runnable() {
                        @Override
                        public void run() { toggleContainerFavorit(containerId); }
                    });
            return;
        }
        laufend++;
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                return new Object[] {d.historie(containerId), d.auftraege()};
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                laufend--;
                try {
                    Object[] r = get();
                    containerDetailFenster.zeige(c, (List<Bewegung>) r[0], (List<Auftrag>) r[1],
                            favoritenContainer.contains(containerId), new Runnable() {
                                @Override
                                public void run() { toggleContainerFavorit(containerId); }
                            });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("Containerdetails", e);
                }
            }
        }.execute();
    }

    private void toggleContainerFavorit(int containerId) {
        if (!favoritenContainer.add(containerId)) {
            favoritenContainer.remove(containerId);
        }
        speichereFavoriten();
        favoritenAktualisieren();
    }

    private void ladeFavoriten() {
        leseFavoriten(favoritenSpeicher.get("orte", ""), favoritenOrte);
        leseFavoriten(favoritenSpeicher.get("container", ""), favoritenContainer);
    }

    private static void leseFavoriten(String text, java.util.Set<Integer> ziel) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        for (String teil : text.split(",")) {
            try {
                ziel.add(Integer.valueOf(teil.trim()));
            } catch (NumberFormatException e) {
                // Veraltete oder beschädigte Einträge werden einfach ignoriert.
            }
        }
    }

    private void speichereFavoriten() {
        favoritenSpeicher.put("orte", favoritenText(favoritenOrte));
        favoritenSpeicher.put("container", favoritenText(favoritenContainer));
    }

    private static String favoritenText(java.util.Set<Integer> ids) {
        StringBuilder s = new StringBuilder();
        for (Integer id : ids) {
            if (s.length() > 0) {
                s.append(',');
            }
            s.append(id);
        }
        return s.toString();
    }

    private void favoritenAktualisieren() {
        KartenModell m = karte.getModell();
        if (m == null) {
            return;
        }
        bestand.setFavoriten(favoritenOrte);
        seite.setFavoriten(m.getOrte(), m.getContainer(), favoritenOrte, favoritenContainer);
    }

    /** Dialog und Speichern einer neuen Stadt. */
    private void stadtOeffnen() {
        if (demo.laeuft() || karte.istZeitreise()) {
            karte.zeigeHinweis("Während der Demo oder Zeitreise können keine Städte angelegt werden");
            return;
        }
        if (karte.getContainerDienst() == null || karte.getModell() == null || stadtFenster == null) {
            return;
        }
        OrtAnlage anlage = stadtFenster.zeige(karte.getModell());
        if (anlage != null) {
            ortAnlegen(anlage);
        }
    }

    private void ortAnlegen(final OrtAnlage anlage) {
        final ContainerDienst d = karte.getContainerDienst();
        laufend++;
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                Ort neu = d.ortAnlegen(anlage);
                return new Object[] {neu, d.orte()};
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                laufend--;
                try {
                    Object[] r = get();
                    Ort neu = (Ort) r[0];
                    karte.setOrte((List<Ort>) r[1]);
                    karte.zeigeHinweis("Stadt " + neu.getName() + " angelegt");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("Stadt anlegen", e);
                }
            }
        }.execute();
    }

    private void containerAnlegen(final ContainerAnlage anlage) {
        final ContainerDienst d = karte.getContainerDienst();
        laufend++;
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                ContainerInfo neu = d.containerAnlegen(anlage);
                return new Object[] {neu, d.container()};
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                laufend--;
                try {
                    Object[] r = get();
                    ContainerInfo neu = (ContainerInfo) r[0];
                    karte.setContainerStand((List<ContainerInfo>) r[1]);
                    karte.zeigeHinweis("Container " + Iso6346.anzeige(neu.getKennung()) + " angelegt in "
                            + neu.getOrt());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("Container anlegen", e);
                }
            }
        }.execute();
    }

    /** Alles neu: Bestand sofort aus dem Kartenstand, Zoll und Fahrten im Hintergrund. */
    public void neuLaden() {
        if (karte.istZeitreise()) {
            // die Seitenleiste zeigt gerade den Stand von damals; neu gelesen wird bei "jetzt"
            return;
        }
        KartenModell m = karte.getModell();
        if (m != null) {
            seite.setKartenFilterWaren(m.getWaren());
            bestand.setStand(Bestand.aus(m.getOrte(), m.getWaren(), m.getContainer()));
            dashboard(m.getContainer(), seite.getZollAnzahl(), auftraege.getOffen().size());
            Map<Integer, Color> f = new HashMap<Integer, Color>();
            for (ContainerInfo c : m.getContainer()) {
                f.put(c.getContainerId(), c.getFarbe());
            }
            fahrten.setFarben(f);
            zoll.setTarif(m.getTarif());
            kosten.setWaren(m.getWaren());
            favoritenAktualisieren();
        }
        zoll.setStichtag(0);
        kosten.setStichtag(0);
        zollLaden();
        fahrtenLaden();
        kostenLaden();
        auftraegeLaden();
        zeitreiseLaden();
    }

    // ------------------------------------------------------------ Zeitreise

    /**
     * Demo, letzter Schritt: die Fahrten des Spiel-Dienstes (im Speicher, also
     * sofort da) im Zeitraffer x8 abspielen - die Sperre der Zeitleiste wird
     * daf&uuml;r kurz aufgehoben.
     */
    private boolean demoZeitreise() {
        ContainerDienst d = karte.getContainerDienst();
        KartenModell m = karte.getModell();
        if (d == null || m == null) {
            return false;
        }
        try {
            List<Bewegung> alle = d.alleBewegungen();
            if (alle.isEmpty()) {
                return false;
            }
            zeitreise = new com.dan.logistikapp.model.Zeitreise(m.getContainer(), alle, d.auftraege(), m.getOrte(),
                    System.currentTimeMillis());
        } catch (com.dan.logistikapp.dienst.DienstFehler e) {
            return false;
        }
        zeitleiste.setGesperrt(false);
        zeitleiste.setDaten(zeitreise);
        zeitleiste.setTempoWert(DEMO_TEMPO);
        demoReise = true;
        zeitleiste.spielen();
        return true;
    }

    /** Die ganze Historie und die offenen Auftr&auml;ge f&uuml;r die Zeitleiste. */
    private void zeitreiseLaden() {
        final ContainerDienst d = karte.getContainerDienst();
        final KartenModell m = karte.getModell();
        if (d == null || m == null) {
            return;
        }
        final List<ContainerInfo> stand = m.getContainer();
        laufend++;
        new SwingWorker<com.dan.logistikapp.model.Zeitreise, Void>() {
            @Override
            protected com.dan.logistikapp.model.Zeitreise doInBackground() throws Exception {
                List<Bewegung> alle = d.alleBewegungen();
                List<com.dan.logistikapp.model.Auftrag> a = d.auftraege();
                return new com.dan.logistikapp.model.Zeitreise(stand, alle, a, m.getOrte(), System.currentTimeMillis());
            }

            @Override
            protected void done() {
                laufend--;
                try {
                    zeitreise = get();
                    if (!karte.istZeitreise()) {
                        zeitleiste.setDaten(zeitreise);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("Zeitleiste", e);
                }
            }
        }.execute();
    }

    /**
     * Die Zeitleiste hat einen neuen Zeitpunkt: Karte und Seitenleiste zeigen
     * den Stand von damals (oder die Prognose); {@code null} = zur&uuml;ck zu jetzt.
     * Die Karte wird bei jedem Takt neu gesetzt, die Tafeln nur, wenn sich
     * der Stand ge&auml;ndert hat oder 400 ms vergangen sind.
     */
    public void zeigeZeit(Long ms, long fahrtDauerMs) {
        com.dan.logistikapp.model.Zeitreise z = zeitreise;
        KartenModell m = karte.getModell();
        if (ms == null || z == null || m == null) {
            if (karte.istZeitreise()) {
                karte.beendeZeitreise();
                tafelnFuer = null;
                neuLaden();
            }
            return;
        }
        long t = ms;
        boolean prognose = z.istPrognose(t);
        List<ContainerInfo> stand = z.standUm(t);
        karte.setZeitreise(t, stand, z.unterwegs(t, fahrtDauerMs),
                prognose ? z.offenNach(t) : Collections.<com.dan.logistikapp.model.Auftrag>emptyList(), prognose);
        List<Bewegung> bis = z.fahrtenBis(t);
        String fuer = bis.size() + "|" + z.planBis(t) + "|" + zollZahl(stand);
        long jetzt = System.currentTimeMillis();
        if (!fuer.equals(tafelnFuer) || jetzt - tafelnMs > 400 || fahrtDauerMs == 0) {
            tafelnFuer = fuer;
            tafelnMs = jetzt;
            tafelnDamals(z, t, stand, bis);
        }
    }

    private static int zollZahl(List<ContainerInfo> l) {
        int n = 0;
        for (ContainerInfo c : l) {
            n += "ZOLL".equals(c.getStatus()) ? 1 : 0;
        }
        return n;
    }

    private void dashboard(List<ContainerInfo> stand, int zoll, int offeneAuftraege) {
        int teu = 0;
        for (ContainerInfo c : stand) {
            teu += c.getTeu();
        }
        seite.setDashboard(stand.size(), teu, zoll, offeneAuftraege);
    }

    private void tafelnDamals(com.dan.logistikapp.model.Zeitreise z, long t, List<ContainerInfo> stand,
            List<Bewegung> bis) {
        KartenModell m = karte.getModell();
        bestand.setStand(Bestand.aus(m.getOrte(), m.getWaren(), stand));
        fahrten.setLetzte(bis.size() > FAHRTEN_MAX ? bis.subList(0, FAHRTEN_MAX) : bis);
        Map<Integer, String[]> ware = new HashMap<Integer, String[]>();
        for (ContainerInfo c : stand) {
            ware.put(c.getContainerId(), new String[] {c.getWareCode(), c.getWare()});
        }
        kosten.setStichtag(t);
        kosten.setZeilen(com.dan.logistikapp.model.KostenZeile.aus(bis, ware));
        Map<Integer, com.dan.logistikapp.model.Land> land = new HashMap<Integer, com.dan.logistikapp.model.Land>();
        for (com.dan.logistikapp.model.Ort o : m.getOrte()) {
            land.put(o.getOrtId(), m.land(o.getIso2()));
        }
        List<ZollFall> zf = z.zollUm(t, land);
        zoll.setStichtag(t);
        zoll.setFaelle(zf);
        seite.setZollAnzahl(zf.size());
        dashboard(stand, zf.size(), z.istPrognose(t) ? z.offenNach(t).size() : 0);
    }

    private void auftraegeLaden() {
        final ContainerDienst d = karte.getContainerDienst();
        if (d == null) {
            return;
        }
        laufend++;
        new SwingWorker<List<com.dan.logistikapp.model.Auftrag>, Void>() {
            @Override
            protected List<com.dan.logistikapp.model.Auftrag> doInBackground() throws Exception {
                return d.auftraege();
            }

            @Override
            protected void done() {
                laufend--;
                try {
                    List<com.dan.logistikapp.model.Auftrag> l = get();
                    auftraege.setAuftraege(l);
                    karte.setAuftraege(l);
                    lauf.setAuftraege(l);
                    seite.setAuftragAnzahl(auftraege.getOffen().size(), auftraege.blockiert() > 0);
                    KartenModell m = karte.getModell();
                    if (m != null) {
                        dashboard(m.getContainer(), seite.getZollAnzahl(), auftraege.getOffen().size());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("Aufträge", e);
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------ Auftraege

    /** Planer f&uuml;r diesen Container; Ziel -1 = noch nicht gew&auml;hlt. */
    public void planerOeffnen(int containerId, int zielOrtId) {
        ContainerInfo c = karte.container(containerId);
        if (c == null || karte.getModell() == null || planerFenster == null || karte.istZeitreise()) {
            return;
        }
        AuftragPlaner p = planerFenster.zeige(new AuftragPlaner(karte.getModell(), c, zielOrtId));
        if (p != null) {
            planen(p.getContainerId(), p.getZielOrtId(), p.faelligMs(System.currentTimeMillis()));
        }
    }

    /** Legt einen Auftrag an (im Hintergrund) und liest danach neu. */
    public void planen(final int containerId, final int zielOrtId, final long faelligMs) {
        final ContainerDienst d = karte.getContainerDienst();
        if (d == null) {
            return;
        }
        laufend++;
        new SwingWorker<Long, Void>() {
            @Override
            protected Long doInBackground() throws Exception {
                return d.auftragAnlegen(containerId, zielOrtId, faelligMs);
            }

            @Override
            protected void done() {
                laufend--;
                try {
                    long id = get();
                    KartenModell m = karte.getModell();
                    String ziel = m == null || m.punkt(zielOrtId) == null ? "#" + zielOrtId
                            : m.punkt(zielOrtId).getOrt().getName();
                    karte.zeigeHinweis("Auftrag #" + id + " geplant: " + kennung(containerId) + " → " + ziel
                            + (faelligMs <= System.currentTimeMillis() ? ", sofort" : ", fällig um "
                            + AuftragsTafel.uhr(faelligMs)));
                    auftraegeLaden();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("Planen", e);
                }
            }
        }.execute();
    }

    void storniere(final long auftragId) {
        final ContainerDienst d = karte.getContainerDienst();
        if (d == null) {
            return;
        }
        if (karte.istZeitreise()) {
            karte.zeigeHinweis("Zeitreise: nur Ansicht — mit „Jetzt“ zurück, dann stornieren");
            return;
        }
        laufend++;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                d.auftragStornieren(auftragId);
                return null;
            }

            @Override
            protected void done() {
                laufend--;
                try {
                    get();
                    karte.zeigeHinweis("Auftrag #" + auftragId + " storniert");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("Stornieren", e);
                }
                auftraegeLaden();
            }
        }.execute();
    }

    /**
     * Auftrag ausf&uuml;hren lassen, ohne Animation - wenn die Karte ihn nicht
     * fahren kann. Danach steht Versuch und Grund am Auftrag.
     */
    void ausfuehrenStill(final long auftragId) {
        final ContainerDienst d = karte.getContainerDienst();
        if (d == null) {
            return;
        }
        laufend++;
        new SwingWorker<List<ContainerInfo>, Void>() {
            @Override
            protected List<ContainerInfo> doInBackground() throws Exception {
                d.auftragAusfuehren(auftragId);
                return d.container();
            }

            @Override
            protected void done() {
                laufend--;
                try {
                    karte.setContainerStand(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("Auftrag", e);
                }
            }
        }.execute();
    }

    /** Standmarke abfragen; hat sich etwas ge&auml;ndert, den Stand neu lesen. */
    public void abfragen() {
        final ContainerDienst d = karte.getContainerDienst();
        if (d == null || demo.laeuft() || !karte.istRuhig() || laufend > 0 || karte.istZeitreise()) {
            return;
        }
        laufend++;
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                String m = d.standMarke();
                boolean neu = letzteMarke != null && !letzteMarke.equals(m);
                return new Object[] {m, neu ? d.container() : null};
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                laufend--;
                try {
                    Object[] r = get();
                    letzteMarke = (String) r[0];
                    if (r[1] != null && karte.istRuhig()) {
                        karte.setContainerStand((List<ContainerInfo>) r[1]);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("Abfrage", e);
                }
            }
        }.execute();
    }

    private void kostenLaden() {
        final ContainerDienst d = karte.getContainerDienst();
        if (d == null) {
            return;
        }
        laufend++;
        new SwingWorker<List<com.dan.logistikapp.model.KostenZeile>, Void>() {
            @Override
            protected List<com.dan.logistikapp.model.KostenZeile> doInBackground() throws Exception {
                return d.kosten();
            }

            @Override
            protected void done() {
                laufend--;
                try {
                    kosten.setZeilen(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("Kosten", e);
                }
            }
        }.execute();
    }

    private void zollLaden() {
        final ContainerDienst d = karte.getContainerDienst();
        if (d == null) {
            return;
        }
        laufend++;
        new SwingWorker<List<ZollFall>, Void>() {
            @Override
            protected List<ZollFall> doInBackground() throws Exception {
                return d.zollFaelle();
            }

            @Override
            protected void done() {
                laufend--;
                try {
                    List<ZollFall> l = get();
                    zoll.setFaelle(l);
                    seite.setZollAnzahl(l.size());
                    KartenModell m = karte.getModell();
                    if (m != null) {
                        dashboard(m.getContainer(), l.size(), auftraege.getOffen().size());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("Zoll-Liste", e);
                }
            }
        }.execute();
    }

    /** Die j&uuml;ngsten Fahrten - oder, im Historie-Modus, die Historie desselben Containers neu. */
    private void fahrtenLaden() {
        final ContainerDienst d = karte.getContainerDienst();
        if (d == null) {
            return;
        }
        final Integer hist = fahrten.istHistorie() && karte.getSpurContainer() >= 0 ? karte.getSpurContainer() : null;
        laufend++;
        new SwingWorker<List<Bewegung>, Void>() {
            @Override
            protected List<Bewegung> doInBackground() throws Exception {
                return hist != null ? d.historie(hist) : d.letzteBewegungen(FAHRTEN_MAX);
            }

            @Override
            protected void done() {
                laufend--;
                try {
                    List<Bewegung> l = get();
                    if (hist != null && !l.isEmpty()) {
                        fahrten.setHistorie(l.get(0).getKennungAnzeige(), l);
                        if (l.size() != karte.getSpur().size()) {
                            karte.zeigeSpur(l, false);
                        }
                    } else {
                        fahrten.setLetzte(l);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    fehler("Fahrten", e);
                }
            }
        }.execute();
    }

    private void fehler(String was, ExecutionException e) {
        Throwable t = e.getCause() != null ? e.getCause() : e;
        String s = t.getMessage() != null ? t.getMessage() : t.toString();
        int i = s.indexOf('\n');
        if (meldung != null) {
            meldung.fehler(was + ": " + (i > 0 ? s.substring(0, i) : s));
        }
    }

    /** Kennung eines Containers f&uuml;r Texte. */
    String kennung(int id) {
        ContainerInfo c = karte.container(id);
        return c == null ? "#" + id : Iso6346.anzeige(c.getKennung());
    }
}
