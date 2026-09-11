package com.dan.logistikapp.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Der Stand zu einem beliebigen Zeitpunkt, rekonstruiert aus der Historie.
 *
 * <p>Nichts davon steht in der Datenbank als eigene Tabelle: jede Fahrt in
 * LOG_BEWEGUNG kennt Start, Ziel und Zeitpunkt, jede Zollfahrt ihre Freigabe
 * ({@code freigegeben_am}). Daraus folgt f&uuml;r jeden Container und jeden
 * Zeitpunkt t:</p>
 * <ul>
 *   <li>Ort = Ziel der letzten Fahrt bis t; vor seiner ersten Fahrt der Start
 *       dieser Fahrt; ohne Fahrten der heutige Ort.</li>
 *   <li>beim Zoll = die letzte Fahrt bis t war eine Zollfahrt und ist bis t
 *       nicht freigegeben.</li>
 * </ul>
 * <p>Der Durchstich rechnet dasselbe unabh&auml;ngig in SQL nach.</p>
 *
 * <p>Rechts von „jetzt“ wird es eine Prognose: offene Auftr&auml;ge fahren zu
 * ihrem Termin (fr&uuml;hestens jetzt), in Terminfolge. Zoll und Kapazit&auml;t
 * bleiben dabei au&szlig;en vor - es ist eine Vorschau, kein Versprechen.</p>
 *
 * @author Dan
 */
public final class Zeitreise {

    /** Ein Container zwischen zwei St&auml;dten - nur w&auml;hrend der Wiedergabe. */
    public static final class Unterwegs {
        private final ContainerInfo container;
        private final int vonOrtId;
        private final int nachOrtId;
        private final double anteil;
        private final boolean prognose;

        Unterwegs(ContainerInfo c, int von, int nach, double anteil, boolean prognose) {
            this.container = c;
            this.vonOrtId = von;
            this.nachOrtId = nach;
            this.anteil = anteil;
            this.prognose = prognose;
        }

        public ContainerInfo getContainer() {
            return container;
        }

        public int getVonOrtId() {
            return vonOrtId;
        }

        public int getNachOrtId() {
            return nachOrtId;
        }

        /** 0 = gerade abgefahren, 1 = angekommen. */
        public double getAnteil() {
            return anteil;
        }

        public boolean isPrognose() {
            return prognose;
        }
    }

    /** Ein Strich auf der Zeitleiste. */
    public static final class Ereignis {
        public enum Art { FAHRT, ZOLLFAHRT, FREIGABE, AUFTRAG }

        private final long ms;
        private final Art art;
        private final String text;

        Ereignis(long ms, Art art, String text) {
            this.ms = ms;
            this.art = art;
            this.text = text;
        }

        public long getMs() {
            return ms;
        }

        public Art getArt() {
            return art;
        }

        public String getText() {
            return text;
        }
    }

    /** Prognose: ein geplanter Schritt. */
    private static final class Plan {
        final long ms;
        final Auftrag auftrag;

        Plan(long ms, Auftrag a) {
            this.ms = ms;
            this.auftrag = a;
        }
    }

    private static final Comparator<Bewegung> REIHENFOLGE = new Comparator<Bewegung>() {
        @Override
        public int compare(Bewegung a, Bewegung b) {
            int c = Long.compare(a.getZeitpunktMs(), b.getZeitpunktMs());
            return c != 0 ? c : Long.compare(a.getBewegungId(), b.getBewegungId());
        }
    };

    private final long jetztMs;
    private final Map<Integer, ContainerInfo> heute = new LinkedHashMap<Integer, ContainerInfo>();
    private final Map<Integer, String> ortName = new HashMap<Integer, String>();
    private final List<Bewegung> fahrten;
    private final Map<Integer, List<Bewegung>> jeContainer = new HashMap<Integer, List<Bewegung>>();
    private final List<Plan> plan = new ArrayList<Plan>();
    private final List<Auftrag> offene = new ArrayList<Auftrag>();
    private final List<Ereignis> ereignisse = new ArrayList<Ereignis>();

    /**
     * @param stand     der heutige Stand aller Container
     * @param alle      alle Fahrten, Reihenfolge egal
     * @param auftraege Auftr&auml;ge; nur die offenen z&auml;hlen (Prognose)
     * @param orte      f&uuml;r die Namen der Orte
     * @param jetztMs   wann "jetzt" ist - der Stand wurde dann gelesen
     */
    public Zeitreise(List<ContainerInfo> stand, List<Bewegung> alle, List<Auftrag> auftraege, List<Ort> orte,
            long jetztMs) {
        this.jetztMs = jetztMs;
        for (ContainerInfo c : stand) {
            heute.put(c.getContainerId(), c);
        }
        for (Ort o : orte) {
            ortName.put(o.getOrtId(), o.getName());
        }
        fahrten = new ArrayList<Bewegung>(alle);
        Collections.sort(fahrten, REIHENFOLGE);
        for (Bewegung b : fahrten) {
            List<Bewegung> l = jeContainer.get(b.getContainerId());
            if (l == null) {
                l = new ArrayList<Bewegung>();
                jeContainer.put(b.getContainerId(), l);
            }
            l.add(b);
            ereignisse.add(new Ereignis(b.getZeitpunktMs(), b.isZoll() ? Ereignis.Art.ZOLLFAHRT : Ereignis.Art.FAHRT,
                    b.getKennungAnzeige() + " " + b.getVonOrt() + " → " + b.getNachOrt()));
            if (b.isZoll() && b.getFreigegebenAmMs() > 0) {
                ereignisse.add(new Ereignis(b.getFreigegebenAmMs(), Ereignis.Art.FREIGABE,
                        b.getKennungAnzeige() + " freigegeben in " + b.getNachOrt()));
            }
        }
        // Prognose: offene Auftraege in Terminfolge, frueher als jetzt geht nicht
        if (auftraege != null) {
            for (Auftrag a : auftraege) {
                if (a.istOffen()) {
                    offene.add(a);
                }
            }
        }
        Collections.sort(offene, new Comparator<Auftrag>() {
            @Override
            public int compare(Auftrag a, Auftrag b) {
                int c = Long.compare(a.getFaelligMs(), b.getFaelligMs());
                return c != 0 ? c : Long.compare(a.getAuftragId(), b.getAuftragId());
            }
        });
        Map<Integer, Long> frei = new HashMap<Integer, Long>();
        for (Auftrag a : offene) {
            Long vorher = frei.get(a.getContainerId());
            // mindestens eine Sekunde nach dem vorigen Schritt desselben Containers
            long ms = Math.max(Math.max(a.getFaelligMs(), jetztMs + 1000), vorher == null ? 0 : vorher + 1000);
            frei.put(a.getContainerId(), ms);
            plan.add(new Plan(ms, a));
            ereignisse.add(new Ereignis(ms, Ereignis.Art.AUFTRAG,
                    "Auftrag #" + a.getAuftragId() + ": " + a.getKennungAnzeige() + " → " + a.getNachOrt()));
        }
        Collections.sort(plan, new Comparator<Plan>() {
            @Override
            public int compare(Plan a, Plan b) {
                return Long.compare(a.ms, b.ms);
            }
        });
        Collections.sort(ereignisse, new Comparator<Ereignis>() {
            @Override
            public int compare(Ereignis a, Ereignis b) {
                return Long.compare(a.ms, b.ms);
            }
        });
    }

    public long getJetztMs() {
        return jetztMs;
    }

    /** Die erste Fahrt, oder jetzt, wenn es keine gibt. */
    public long getErsteMs() {
        return fahrten.isEmpty() ? jetztMs : fahrten.get(0).getZeitpunktMs();
    }

    /** Der letzte geplante Schritt, mindestens jetzt. */
    public long getLetzteMs() {
        return plan.isEmpty() ? jetztMs : Math.max(jetztMs, plan.get(plan.size() - 1).ms);
    }

    public List<Ereignis> getEreignisse() {
        return Collections.unmodifiableList(ereignisse);
    }

    /** Wie viele Fahrten es insgesamt gibt. */
    public int getFahrtenAnzahl() {
        return fahrten.size();
    }

    public boolean istPrognose(long t) {
        return t > jetztMs;
    }

    /** Wie viele geplante Schritte bis t schon gefahren w&auml;ren. */
    public int planBis(long t) {
        int n = 0;
        for (Plan p : plan) {
            n += p.ms <= t ? 1 : 0;
        }
        return n;
    }

    /** Die offenen Auftr&auml;ge, die bis t noch nicht gefahren w&auml;ren (f&uuml;r die gestrichelte Vorschau). */
    public List<Auftrag> offenNach(long t) {
        List<Auftrag> out = new ArrayList<Auftrag>();
        for (Plan p : plan) {
            if (p.ms > t) {
                out.add(p.auftrag);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ Stand

    /** Der Stand aller Container um t - Vergangenheit rekonstruiert, Zukunft als Prognose. */
    public List<ContainerInfo> standUm(long t) {
        List<ContainerInfo> out = new ArrayList<ContainerInfo>();
        Map<Integer, ContainerInfo> prog = t > jetztMs ? prognose(t) : null;
        for (ContainerInfo c : heute.values()) {
            out.add(prog != null ? prog.get(c.getContainerId()) : damals(c, t));
        }
        return out;
    }

    /** Welche Container um t nur in der Prognose stehen, wo sie stehen. */
    public List<Integer> prognostiziert(long t) {
        List<Integer> out = new ArrayList<Integer>();
        for (Plan p : plan) {
            if (p.ms <= t && !out.contains(p.auftrag.getContainerId())) {
                out.add(p.auftrag.getContainerId());
            }
        }
        return out;
    }

    private ContainerInfo damals(ContainerInfo c, long t) {
        List<Bewegung> l = jeContainer.get(c.getContainerId());
        if (l == null || l.isEmpty()) {
            return c;
        }
        int letzte = -1;
        for (int i = 0; i < l.size() && l.get(i).getZeitpunktMs() <= t; i++) {
            letzte = i;
        }
        if (letzte < 0) {
            return mit(c, l.get(0).getVonOrtId(), "BEREIT");
        }
        return mit(c, l.get(letzte).getNachOrtId(), zollfreiBis(l, letzte, t) ? "BEREIT" : "ZOLL");
    }

    /**
     * Ist die Fahrt i um t (noch) nicht zollpflichtig wartend? Eine Zollfahrt
     * ohne Freigabezeit, auf die noch eine Fahrt folgt, war sp&auml;testens bei
     * dieser Fahrt frei.
     */
    private static boolean zollfreiBis(List<Bewegung> l, int i, long t) {
        Bewegung b = l.get(i);
        if (!b.isZoll()) {
            return true;
        }
        long frei = b.getFreigegebenAmMs();
        if (frei <= 0 && i + 1 < l.size()) {
            frei = l.get(i + 1).getZeitpunktMs();
        }
        if (frei <= 0) {
            return false;
        }
        return frei <= t;
    }

    private Map<Integer, ContainerInfo> prognose(long t) {
        Map<Integer, ContainerInfo> m = new LinkedHashMap<Integer, ContainerInfo>(heute);
        for (Plan p : plan) {
            if (p.ms > t) {
                break;
            }
            ContainerInfo c = m.get(p.auftrag.getContainerId());
            if (c != null) {
                m.put(c.getContainerId(), mit(c, p.auftrag.getNachOrtId(), "BEREIT"));
            }
        }
        return m;
    }

    private ContainerInfo mit(ContainerInfo c, int ortId, String status) {
        if (c.getOrtId() == ortId && status.equals(c.getStatus())) {
            return c;
        }
        String name = ortName.containsKey(ortId) ? ortName.get(ortId) : "#" + ortId;
        return new ContainerInfo(c.getContainerId(), c.getKennung(), c.getGroesseFuss(), c.getTypCode(),
                c.getWareCode(), c.getWare(), c.getFarbe(), ortId, name, status);
    }

    /**
     * Container, bei denen die Historie nicht am heutigen Stand endet (Ort
     * oder Zollstatus). Leer, wenn alles zusammenpasst - der Durchstich pr&uuml;ft das.
     */
    public List<String> abweichungen() {
        List<String> out = new ArrayList<String>();
        for (ContainerInfo c : heute.values()) {
            List<Bewegung> l = jeContainer.get(c.getContainerId());
            if (l == null || l.isEmpty()) {
                continue;
            }
            Bewegung b = l.get(l.size() - 1);
            boolean wartet = b.isZoll() && b.getFreigegebenAmMs() <= 0;
            if (b.getNachOrtId() != c.getOrtId() || wartet != "ZOLL".equals(c.getStatus())) {
                out.add(Iso6346.anzeige(c.getKennung()) + ": Historie endet in " + b.getNachOrt()
                        + (wartet ? " beim Zoll" : "") + ", steht in " + c.getOrt() + " " + c.getStatus());
            }
            for (int i = 1; i < l.size(); i++) {
                if (l.get(i).getVonOrtId() != l.get(i - 1).getNachOrtId()) {
                    out.add(Iso6346.anzeige(c.getKennung()) + ": Lücke vor Fahrt " + l.get(i).getBewegungId());
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------ Fahrten, Kosten, Zoll um t

    /** Alle Fahrten bis t, die neueste zuerst. Standgeld nur, wenn die Freigabe bis t war. */
    public List<Bewegung> fahrtenBis(long t) {
        List<Bewegung> out = new ArrayList<Bewegung>();
        for (int i = fahrten.size() - 1; i >= 0; i--) {
            Bewegung b = fahrten.get(i);
            if (b.getZeitpunktMs() > t) {
                continue;
            }
            boolean frei = b.getFreigegebenAmMs() > 0 && b.getFreigegebenAmMs() <= t;
            if (b.isZoll() && !frei) {
                out.add(kopie(b).mitKosten(b.getFrachtEur(), b.getZollEur(), null, 0, b.getAusgeloest()));
            } else {
                out.add(b);
            }
        }
        return out;
    }

    private static Bewegung kopie(Bewegung b) {
        return new Bewegung(b.getBewegungId(), b.getContainerId(), b.getKennungAnzeige(), b.getVonOrtId(),
                b.getVonOrt(), b.getNachOrtId(), b.getNachOrt(), b.getVonZone(), b.getNachZone(), b.isZoll(),
                b.getDistanzM(), b.getZeitpunktMs());
    }

    /**
     * Wer um t beim Zoll steht - wie LOG_ZOLL_V damals: Wartezeit ab der
     * Fahrt bis t, der am l&auml;ngsten Wartende zuerst.
     */
    public List<ZollFall> zollUm(long t, Map<Integer, Land> landJeOrt) {
        List<ZollFall> out = new ArrayList<ZollFall>();
        for (ContainerInfo c : standUm(t)) {
            if (!"ZOLL".equals(c.getStatus())) {
                continue;
            }
            Bewegung b = letzteBis(c.getContainerId(), t);
            if (b == null) {
                continue;
            }
            Land lv = landJeOrt.get(b.getVonOrtId());
            Land ln = landJeOrt.get(b.getNachOrtId());
            out.add(new ZollFall(c.getContainerId(), b.getKennungAnzeige(), c.getWare(), c.getFarbe(), c.getOrtId(),
                    c.getOrt(), b.getVonOrt(), lv == null ? "?" : lv.getIso2(), ln == null ? "?" : ln.getIso2(),
                    b.getVonZone(), b.getNachZone(), Math.max(0, (t - b.getZeitpunktMs()) / 1000), t));
        }
        Collections.sort(out, new Comparator<ZollFall>() {
            @Override
            public int compare(ZollFall a, ZollFall b) {
                return Long.compare(b.wartetSek(0), a.wartetSek(0));
            }
        });
        return out;
    }

    private Bewegung letzteBis(int containerId, long t) {
        List<Bewegung> l = jeContainer.get(containerId);
        Bewegung r = null;
        if (l != null) {
            for (Bewegung b : l) {
                if (b.getZeitpunktMs() <= t) {
                    r = b;
                }
            }
        }
        return r;
    }

    // ------------------------------------------------------------ Wiedergabe

    /**
     * Wer um t gerade f&auml;hrt: jede Fahrt (und in der Zukunft jeder geplante
     * Schritt) ist {@code dauerMs} lang unterwegs. Mit 0 steht alles.
     */
    public List<Unterwegs> unterwegs(long t, long dauerMs) {
        List<Unterwegs> out = new ArrayList<Unterwegs>();
        if (dauerMs <= 0) {
            return out;
        }
        if (t <= jetztMs) {
            for (Bewegung b : fahrten) {
                long d = t - b.getZeitpunktMs();
                if (d >= 0 && d < dauerMs) {
                    ContainerInfo c = heute.get(b.getContainerId());
                    if (c != null) {
                        out.add(new Unterwegs(mit(c, b.getNachOrtId(), b.isZoll() ? "ZOLL" : "BEREIT"),
                                b.getVonOrtId(), b.getNachOrtId(), d / (double) dauerMs, false));
                    }
                }
            }
            return out;
        }
        Map<Integer, Integer> wo = new HashMap<Integer, Integer>();
        for (ContainerInfo c : heute.values()) {
            wo.put(c.getContainerId(), c.getOrtId());
        }
        for (Plan p : plan) {
            if (p.ms > t) {
                break;
            }
            int von = wo.get(p.auftrag.getContainerId()) == null ? -1 : wo.get(p.auftrag.getContainerId());
            wo.put(p.auftrag.getContainerId(), p.auftrag.getNachOrtId());
            long d = t - p.ms;
            ContainerInfo c = heute.get(p.auftrag.getContainerId());
            if (d < dauerMs && c != null && von >= 0 && von != p.auftrag.getNachOrtId()) {
                out.add(new Unterwegs(mit(c, p.auftrag.getNachOrtId(), "BEREIT"), von, p.auftrag.getNachOrtId(),
                        d / (double) dauerMs, true));
            }
        }
        return out;
    }

    /** Zeitpunkte, an denen sich etwas &auml;ndert (Fahrten, Freigaben, Plan), aufsteigend, ohne Doppelte. */
    public List<Long> wechsel() {
        TreeSet<Long> s = new TreeSet<Long>();
        for (Ereignis e : ereignisse) {
            s.add(e.getMs());
        }
        return new ArrayList<Long>(s);
    }
}
