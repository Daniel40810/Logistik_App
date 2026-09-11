package com.dan.logistikapp.ui;

import com.dan.logistikapp.dienst.ContainerDienst;
import com.dan.logistikapp.dienst.SpeicherDienst;
import com.dan.logistikapp.karte.KartenModell;
import com.dan.logistikapp.karte.KartenPanel;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.Zone;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Timer;

/**
 * Die Demo-Szene: ein Rundgang durch alle Zonenregeln, gespielt auf einer
 * Kopie des Stands. W&auml;hrend der Demo schreibt die Karte in einen
 * {@link SpeicherDienst}, nicht in die Datenbank; danach steht wieder der
 * gespeicherte Stand da.
 *
 * <p>Jeder Schritt wartet, bis die Karte ruht (Fahrt, Auftrag, Einparken),
 * und dann eine Pause - so l&auml;uft der Rundgang im Takt der Animation und
 * nicht nach der Uhr.</p>
 *
 * @author Dan
 */
public final class DemoSzene {

    /** Bescheid an die Anwendung. */
    public interface Beobachter {
        void schritt(int nr, int von, String text);

        void ende(boolean abgebrochen);
    }

    /**
     * Was die Demo au&szlig;erhalb der Karte braucht: einen Reiter zeigen und die
     * Zeitreise &uuml;ber die eigenen Fahrten abspielen. Setzt der Leitstand.
     */
    public interface Buehne {
        void reiter(int reiter);

        /** Startet die Wiedergabe der Demo-Fahrten; false, wenn es nichts abzuspielen gibt. */
        boolean zeitreise();

        /** L&auml;uft die Wiedergabe noch? Solange wartet die Demo. */
        boolean zeitreiseLaeuft();
    }

    private abstract static class Schritt {
        final String text;
        int reiter = -1;

        Schritt(String text) {
            this.text = text;
        }

        Schritt reiter(int r) {
            this.reiter = r;
            return this;
        }

        /** Der Text im Band; manche Schritte rechnen ihn aus dem Stand. */
        String text(DemoSzene d) {
            return text;
        }

        /** @return false, wenn es nichts zu tun gab (der Schritt wird &uuml;bersprungen) */
        abstract boolean los(DemoSzene d);
    }

    private static final class Blick extends Schritt {
        Blick(String text) {
            super(text);
        }

        @Override
        boolean los(DemoSzene d) {
            d.karte.allesZeigen();
            return true;
        }
    }

    private static final class Fahren extends Schritt {
        final String von;
        final String nach;

        Fahren(String von, String nach, String text) {
            super(text);
            this.von = von;
            this.nach = nach;
        }

        @Override
        boolean los(DemoSzene d) {
            KartenModell m = d.karte.getModell();
            KartenModell.OrtPunkt z = ort(m, nach);
            ContainerInfo c = d.waehle(ort(m, von), z);
            if (c == null || z == null) {
                return false;
            }
            d.zuletzt = c.getContainerId();
            d.gefahren.add(c.getContainerId());
            return d.karte.fahre(c.getContainerId(), z.getOrt().getOrtId());
        }
    }

    private static final class Freigeben extends Schritt {
        Freigeben(String text) {
            super(text);
        }

        @Override
        boolean los(DemoSzene d) {
            List<ContainerInfo> zoll = new ArrayList<ContainerInfo>();
            for (ContainerInfo c : d.karte.getModell().getContainer()) {
                if ("ZOLL".equals(c.getStatus())) {
                    zoll.add(c);
                }
            }
            if (zoll.isEmpty()) {
                return false;
            }
            d.karte.zollFreigeben(zoll);
            return true;
        }
    }

    private static final class Historie extends Schritt {
        Historie(String text) {
            super(text);
        }

        @Override
        boolean los(DemoSzene d) {
            if (d.zuletzt < 0) {
                return false;
            }
            d.karte.zeigeHistorie(d.zuletzt);
            return true;
        }
    }

    /** Kapazit&auml;t: die vollste Stadt ins Bild holen, der Ring zeigt die Belegung. */
    private static final class Kapazitaet extends Schritt {
        Kapazitaet() {
            super("Kapazität");
        }

        private static KartenModell.OrtPunkt vollste(KartenModell m) {
            KartenModell.OrtPunkt best = null;
            double anteil = -1;
            for (KartenModell.OrtPunkt p : m.getPunkte()) {
                Integer kap = p.getOrt().getKapazitaetTeu();
                if (kap != null && kap > 0) {
                    double a = m.belegtTeu(p.getOrt().getOrtId()) / (double) kap;
                    if (a > anteil) {
                        anteil = a;
                        best = p;
                    }
                }
            }
            return best;
        }

        @Override
        String text(DemoSzene d) {
            KartenModell m = d.karte.getModell();
            KartenModell.OrtPunkt p = vollste(m);
            if (p == null) {
                return "Kapazität: jede Stadt hat nur so viel Platz, wie ihr Ring zeigt";
            }
            return "Kapazität: der Ring zeigt die Belegung — " + p.getOrt().getName() + " "
                    + m.belegtTeu(p.getOrt().getOrtId()) + " von " + p.getOrt().getKapazitaetTeu()
                    + " TEU; ist das Ziel voll, federt der Container zurück";
        }

        @Override
        boolean los(DemoSzene d) {
            KartenModell.OrtPunkt p = vollste(d.karte.getModell());
            if (p == null) {
                return false;
            }
            d.karte.loescheSpur();
            d.karte.zeigeOrt(p.getOrt().getOrtId());
            return true;
        }
    }

    /** Kosten: zur&uuml;ck zur &Uuml;bersicht, rechts der Reiter mit den Summen. */
    private static final class Kosten extends Schritt {
        Kosten(String text) {
            super(text);
        }

        @Override
        boolean los(DemoSzene d) {
            d.karte.allesZeigen();
            return true;
        }
    }

    /** Einen Auftrag mit Termin planen - der Countdown l&auml;uft auf der Karte. */
    private static final class Planen extends Schritt {
        final String von;
        final String nach;

        Planen(String von, String nach) {
            super("");
            this.von = von;
            this.nach = nach;
        }

        @Override
        String text(DemoSzene d) {
            return "Ein Auftrag mit Termin: " + von + " → " + nach + ", fällig in " + (d.auftragMs / 1000)
                    + " s — gestrichelt, mit Countdown";
        }

        @Override
        boolean los(DemoSzene d) {
            KartenModell m = d.karte.getModell();
            KartenModell.OrtPunkt z = ort(m, nach);
            ContainerInfo c = d.waehle(ort(m, von), z);
            if (c == null || z == null) {
                return false;
            }
            long faellig = System.currentTimeMillis() + d.auftragMs;
            try {
                d.auftragId = d.spiel.auftragAnlegen(c.getContainerId(), z.getOrt().getOrtId(), faellig);
            } catch (Exception e) {
                return false;
            }
            d.zuletzt = c.getContainerId();
            d.gefahren.add(c.getContainerId());
            d.wartenBis = faellig;
            // neu lesen lassen: Liste, Reiter und Vorschau auf der Karte
            d.karte.setContainerStand(d.spiel.container());
            return true;
        }
    }

    /** Der geplante Auftrag ist f&auml;llig: die Karte f&auml;hrt ihn selbst. */
    private static final class AuftragFahren extends Schritt {
        AuftragFahren(String text) {
            super(text);
        }

        @Override
        boolean los(DemoSzene d) {
            if (d.auftragId <= 0) {
                return false;
            }
            for (com.dan.logistikapp.model.Auftrag a : d.spiel.auftraege()) {
                if (a.getAuftragId() == d.auftragId && a.istOffen()) {
                    return d.karte.fahreAuftrag(a);
                }
            }
            return false;
        }
    }

    /** Zum Schluss: alle Fahrten der Demo noch einmal im Zeitraffer. */
    private static final class Rueckblick extends Schritt {
        Rueckblick(String text) {
            super(text);
        }

        @Override
        boolean los(DemoSzene d) {
            d.karte.loescheSpur();
            d.karte.allesZeigen();
            return d.buehne != null && d.buehne.zeitreise();
        }
    }

    private final KartenPanel karte;
    private final long pauseMs;
    private Buehne buehne;
    /** Wie weit der Demo-Auftrag in der Zukunft liegt. */
    private final long auftragMs;
    private long auftragId;
    private long wartenBis;
    private final List<Schritt> schritte = new ArrayList<Schritt>();
    private final Timer takt;
    private Beobachter beobachter;

    private ContainerDienst echt;
    private List<ContainerInfo> vorher;
    private SpeicherDienst spiel;
    private int naechster;
    private long weiterAb;
    private boolean abbrechen;
    private boolean laeuft;
    private int zuletzt = -1;
    private final List<Integer> gefahren = new ArrayList<Integer>();

    /**
     * @param pauseMs Pause nach jedem Schritt, wenn die Karte wieder ruht
     */
    public DemoSzene(KartenPanel karte, long pauseMs) {
        this.karte = karte;
        this.pauseMs = pauseMs;
        this.auftragMs = Math.max(2000, Math.min(8000, pauseMs * 4));
        schritte.add(new Blick("Europa in drei Zonen — die Farbe eines Containers ist seine Ware"));
        schritte.add(new Fahren("Hamburg", "München", "Inland → Inland: keine Grenze, kein Zoll"));
        schritte.add(new Fahren("Köln", "Paris", "Inland → EU-Ausland: Zonenwechsel, aber zollfrei (Zollunion)"));
        schritte.add(new Fahren("Berlin", "Warschau", "Nach Polen: EU-Ausland, ebenfalls zollfrei"));
        schritte.add(new Fahren("Hamburg", "Oslo", "Nach Norwegen: Nicht-EU, außerhalb der Zollunion — Zoll"));
        schritte.add(new Fahren("München", "Zürich", "In die Schweiz: Nicht-EU — der Container wartet beim Zoll"));
        schritte.add(new Freigeben("Rechts die Liste „Beim Zoll“ — alle Container freigeben")
                .reiter(SeitenLeiste.ZOLL));
        schritte.add(new Fahren("Barcelona", "London", "EU-Ausland → Vereinigtes Königreich: Zoll"));
        schritte.add(new Fahren("Zürich", "Oslo", "Zürich → Oslo: Nicht-EU nach Nicht-EU, trotzdem Zoll"));
        schritte.add(new Freigeben("Freigeben — erst dann darf er weiter; das Standgeld zählt bis hierher")
                .reiter(SeitenLeiste.ZOLL));
        schritte.add(new Historie("Die Historie: jede Fahrt bleibt gespeichert, hier als Spur"));
        // Runde 2: Kapazitaet, Kosten, Auftraege, Zeitreise
        schritte.add(new Kapazitaet().reiter(SeitenLeiste.BESTAND));
        schritte.add(new Kosten("Jede Fahrt kostet: Fracht je TEU-km nach Ware, Zoll 85 €, Standgeld je Viertelstunde")
                .reiter(SeitenLeiste.KOSTEN));
        schritte.add(new Planen("Hamburg", "Berlin").reiter(SeitenLeiste.AUFTRAEGE));
        schritte.add(new AuftragFahren("Fällig — die Karte fährt den Auftrag selbst, mit Animation")
                .reiter(SeitenLeiste.AUFTRAEGE));
        schritte.add(new Rueckblick("Zeitreise: die ganze Demo noch einmal im Zeitraffer — unten die Zeitleiste")
                .reiter(SeitenLeiste.FAHRTEN));
        takt = new Timer(100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tick();
            }
        });
    }

    public void setBeobachter(Beobachter b) {
        this.beobachter = b;
    }

    public void setBuehne(Buehne b) {
        this.buehne = b;
    }

    public boolean laeuft() {
        return laeuft;
    }

    public int getSchritte() {
        return schritte.size();
    }

    /** Der Dienst, in den die Demo schreibt - f&uuml;r Tests, sonst {@code null}. */
    public SpeicherDienst getSpielDienst() {
        return spiel;
    }

    /** Startet den Rundgang. Ohne Karte oder w&auml;hrend einer Fahrt passiert nichts. */
    public boolean start() {
        return start(null);
    }

    /**
     * @param wartende wer beim Start schon beim Zoll steht (aus der Zoll-Liste),
     *                 damit die Liste auch in der Demo stimmt
     */
    public boolean start(List<com.dan.logistikapp.model.ZollFall> wartende) {
        KartenModell m = karte.getModell();
        if (laeuft || m == null || !karte.istRuhig()) {
            return false;
        }
        echt = karte.getContainerDienst();
        vorher = new ArrayList<ContainerInfo>(m.getContainer());
        spiel = new SpeicherDienst(m.getOrte(), m.getLaender(), vorher).setVerzoegerungMs(250)
                .setTarif(m.getTarif()).wartende(wartende);
        karte.loescheSpur();
        karte.setContainerDienst(spiel);
        karte.setDemoText("");
        naechster = 0;
        zuletzt = -1;
        auftragId = 0;
        wartenBis = 0;
        gefahren.clear();
        abbrechen = false;
        laeuft = true;
        weiterAb = System.currentTimeMillis();
        takt.start();
        return true;
    }

    /** Beendet nach dem laufenden Schritt. */
    public void stopp() {
        if (laeuft) {
            abbrechen = true;
        }
    }

    private void tick() {
        boolean faehrt = !karte.istRuhig();
        if (abbrechen && !faehrt) {
            // auch mitten in der Wiedergabe: die Demo endet sofort
            ende(true);
            return;
        }
        if (faehrt || (buehne != null && buehne.zeitreiseLaeuft())) {
            weiterAb = Math.max(wartenBis, System.currentTimeMillis() + pauseMs);
            return;
        }
        if (System.currentTimeMillis() < weiterAb) {
            return;
        }
        while (naechster < schritte.size()) {
            Schritt s = schritte.get(naechster++);
            String t = s.text(this);
            karte.setDemoText(naechster + "/" + schritte.size() + "  ·  " + t);
            if (beobachter != null) {
                beobachter.schritt(naechster, schritte.size(), t);
            }
            if (buehne != null && s.reiter >= 0) {
                buehne.reiter(s.reiter);
            }
            wartenBis = 0;
            if (s.los(this)) {
                weiterAb = Math.max(wartenBis,
                        System.currentTimeMillis() + pauseMs + (s instanceof Blick ? pauseMs : 0));
                return;
            }
        }
        ende(false);
    }

    private void ende(boolean abgebrochen) {
        takt.stop();
        laeuft = false;
        karte.setDemoText(null);
        karte.loescheSpur();
        karte.setContainerDienst(echt);
        karte.setContainerStand(vorher);
        karte.zeigeHinweis(abgebrochen ? "Demo abgebrochen — wieder der gespeicherte Stand"
                : "Demo vorbei — wieder der gespeicherte Stand");
        if (beobachter != null) {
            beobachter.ende(abgebrochen);
        }
    }

    /**
     * Welcher Container f&auml;hrt: zuerst einer, der in dieser Demo schon
     * gefahren ist und am Start steht (dann erz&auml;hlt seine Historie mehr); dann irgendein bereiter am Start; dann einer aus einer
     * anderen Stadt derselben Zone. So passt der Rundgang auch zu einem
     * Stand, der sich seit dem Aufbau ver&auml;ndert hat.
     */
    private ContainerInfo waehle(KartenModell.OrtPunkt start, KartenModell.OrtPunkt ziel) {
        if (start == null || ziel == null) {
            return null;
        }
        int sId = start.getOrt().getOrtId();
        int zId = ziel.getOrt().getOrtId();
        ContainerInfo erster = null;
        ContainerInfo gleicheZone = null;
        ContainerInfo schonGefahren = null;
        int rang = -1;
        Zone zone = start.getOrt().getZone();
        KartenModell m = karte.getModell();
        for (ContainerInfo c : m.getContainer()) {
            if (!"BEREIT".equals(c.getStatus()) || c.getOrtId() == zId) {
                continue;
            }
            if (c.getOrtId() == sId) {
                int r = gefahren.lastIndexOf(c.getContainerId());
                if (r > rang) {
                    rang = r;
                    schonGefahren = c;
                }
                if (erster == null) {
                    erster = c;
                }
            } else if (gleicheZone == null) {
                KartenModell.OrtPunkt p = m.punkt(c.getOrtId());
                if (p != null && p.getOrt().getZone() == zone) {
                    gleicheZone = c;
                }
            }
        }
        return schonGefahren != null ? schonGefahren : erster != null ? erster : gleicheZone;
    }

    private static KartenModell.OrtPunkt ort(KartenModell m, String name) {
        for (KartenModell.OrtPunkt p : m.getPunkte()) {
            if (p.getOrt().getName().equals(name)) {
                return p;
            }
        }
        return null;
    }
}
