package com.dan.logistikapp.dienst;

import com.dan.logistikapp.geo.Geodaesie;
import com.dan.logistikapp.model.Bewegung;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.ContainerAnlage;
import com.dan.logistikapp.model.Iso6346;
import com.dan.logistikapp.model.Land;
import com.dan.logistikapp.model.Ort;
import com.dan.logistikapp.model.OrtAnlage;
import com.dan.logistikapp.model.Regeln;
import com.dan.logistikapp.model.ZollFall;
import com.dan.logistikapp.model.Ware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container-Dienst im Speicher, mit denselben Regeln wie LOG_API:
 * Zoll nach {@link Regeln#zollNoetig}, Entfernung nach Vincenty (wie
 * SDO_GEOM.SDO_DISTANCE auf Meter genau), Zonen als Schnappschuss.
 *
 * <p>Dient der Demo-Szene - sie spielt auf einer Kopie des Stands und
 * schreibt nichts in die Datenbank - und den Tests ohne Datenbank.
 * Der Durchstich pr&uuml;ft, dass er f&uuml;r dieselbe Fahrt dasselbe liefert
 * wie LOG_API.</p>
 *
 * @author Dan
 */
public final class SpeicherDienst implements ContainerDienst {

    private final List<ContainerInfo> liste = new ArrayList<ContainerInfo>();
    private final Map<Integer, Ort> orte = new HashMap<Integer, Ort>();
    private final Map<String, Land> laender = new HashMap<String, Land>();
    private final Map<String, Ware> waren = new HashMap<String, Ware>();
    private final List<Bewegung> bewegungen = new ArrayList<Bewegung>();
    private long naechsteBewegung = 1;
    private int aufrufe;
    private boolean naechsterScheitert;
    private long verzoegerungMs;
    private long marke;
    private final List<Plan> plaene = new ArrayList<Plan>();
    private long naechsterAuftrag = 1;

    /** Ein Auftrag im Speicher - dieselben Felder wie LOG_AUFTRAG. */
    private static final class Plan {
        long id;
        int containerId;
        int nach;
        long faellig;
        String status = "OFFEN";
        String grund;
        int versuche;
        long bewegungId;
        String von;
        long erledigt;
    }
    private com.dan.logistikapp.model.Tarif tarif;

    /** Woher die Zeit kommt - Tests stellen sie, um eine Historie &uuml;ber Stunden zu bauen. */
    public interface Uhr {
        long jetzt();
    }

    private Uhr uhr = new Uhr() {
        @Override
        public long jetzt() {
            return System.currentTimeMillis();
        }
    };

    /**
     * @param orte     alle St&auml;dte
     * @param laender  mindestens die L&auml;nder dieser St&auml;dte
     * @param stand    Containerstand, wird kopiert
     */
    public SpeicherDienst(List<Ort> orte, List<Land> laender, List<ContainerInfo> stand) {
        for (Ort o : orte) {
            this.orte.put(o.getOrtId(), o);
        }
        for (Land l : laender) {
            if (l != null) {
                this.laender.put(l.getIso2(), l);
            }
        }
        liste.addAll(stand);
        for (ContainerInfo c : stand) {
            if (c.getWareCode() != null && !waren.containsKey(c.getWareCode())) {
                waren.put(c.getWareCode(), new Ware(c.getWareCode(), c.getWare(), c.getFarbe(), c.isReefer(), 0));
            }
        }
    }

    /** Variante f&uuml;r Tests und die Demo mit vollst&auml;ndigen Warenstammdaten. */
    public SpeicherDienst(List<Ort> orte, List<Land> laender, List<ContainerInfo> stand, List<Ware> waren) {
        this(orte, laender, stand);
        if (waren != null) {
            for (Ware w : waren) {
                this.waren.put(w.getCode(), w);
            }
        }
    }

    /**
     * &Uuml;bernimmt, wer schon beim Zoll wartet: je Fall eine Bewegung mit
     * derselben Strecke und derselben Wartezeit. Ohne das st&uuml;nde ein
     * Container mit Status ZOLL da, aber in keiner Zoll-Liste.
     */
    public synchronized SpeicherDienst wartende(List<ZollFall> faelle) {
        if (faelle == null) {
            return this;
        }
        long jetzt = uhr.jetzt();
        for (ZollFall f : faelle) {
            Ort v = null;
            for (Ort o : orte.values()) {
                if (o.getName().equals(f.getVonOrt())) {
                    v = o;
                }
            }
            Ort n = orte.get(f.getOrtId());
            if (v == null || n == null) {
                continue;
            }
            long m = Math.round(Geodaesie.entfernungM(v.getLaenge(), v.getBreite(), n.getLaenge(), n.getBreite()));
            ContainerInfo c = finde(f.getContainerId());
            bewegungen.add(new Bewegung(naechsteBewegung++, f.getContainerId(), f.getKennungAnzeige(), v.getOrtId(),
                    v.getName(), n.getOrtId(), n.getName(), f.getVonZone(), f.getNachZone(), true, m,
                    jetzt - f.wartetSek(jetzt) * 1000).mitKosten(
                            tarif == null || c == null ? null : tarif.fracht(m, c.getTeu(), c.getWareCode()),
                            tarif == null ? null : tarif.zoll(true), null, 0, "HAND"));
        }
        return this;
    }

    /**
     * Mit Tarif rechnet der Dienst Kosten und Standgeld wie LOG_API; ohne
     * bleiben die Betr&auml;ge leer. Die Kapazit&auml;t kommt aus den Orten.
     */
    public synchronized SpeicherDienst setTarif(com.dan.logistikapp.model.Tarif t) {
        this.tarif = t;
        return this;
    }

    /** Belegte TEU an einem Ort, Zoll-Wartende eingeschlossen (wie LOG_API.belegt_teu). */
    public synchronized int belegtTeu(int ortId) {
        int teu = 0;
        for (ContainerInfo c : liste) {
            if (c.getOrtId() == ortId) {
                teu += c.getTeu();
            }
        }
        return teu;
    }

    /** Eigene Uhr statt der Systemzeit (Tests der Zeitreise). */
    public synchronized SpeicherDienst setUhr(Uhr u) {
        this.uhr = u;
        return this;
    }

    @Override
    public synchronized List<Bewegung> alleBewegungen() {
        return new ArrayList<Bewegung>(bewegungen);
    }

    /** Wie lange jeder schreibende Aufruf dauert - damit die Karte eine Datenbank sp&uuml;rt. */
    public SpeicherDienst setVerzoegerungMs(long ms) {
        this.verzoegerungMs = ms;
        return this;
    }

    /** Anzahl der Aufrufe von {@link #verschieben}, auch der gescheiterten. */
    public synchronized int getAufrufe() {
        return aufrufe;
    }

    /** F&uuml;r Tests: der n&auml;chste Auftrag scheitert wie eine abgerissene Verbindung. */
    public synchronized void scheitereBeimNaechsten() {
        naechsterScheitert = true;
    }

    @Override
    public synchronized Fahrt verschieben(int id, int nach) throws DienstFehler {
        return fahren(id, nach, "HAND");
    }

    @Override
    public synchronized ContainerInfo containerAnlegen(ContainerAnlage anlage) throws DienstFehler {
        if (anlage == null || (anlage.getGroesseFuss() != 20 && anlage.getGroesseFuss() != 40)) {
            throw new DienstFehler(DienstFehler.Art.CONTAINER_GROESSE, "Containergröße muss 20 oder 40 Fuß sein", null);
        }
        Ort ort = orte.get(anlage.getOrtId());
        if (ort == null) {
            throw new DienstFehler(DienstFehler.Art.ORT_UNBEKANNT, "Ort " + anlage.getOrtId() + " unbekannt", null);
        }
        String code = anlage.getWareCode() == null ? "" : anlage.getWareCode().trim().toUpperCase();
        Ware ware = waren.get(code);
        if (ware == null) {
            throw new DienstFehler(DienstFehler.Art.WARE_UNBEKANNT, "Ware " + code + " unbekannt", null);
        }
        int id = 0;
        for (ContainerInfo c : liste) {
            id = Math.max(id, c.getContainerId());
        }
        id++;
        String kennung = anlage.getKennung() == null ? "" : anlage.getKennung().trim();
        if (kennung.isEmpty()) {
            kennung = Iso6346.neueKennung(Iso6346.PRAEFIX, 100000 + id);
        } else if (!Iso6346.gueltig(kennung)) {
            throw new DienstFehler(DienstFehler.Art.DATENBANK, "Kennung verstößt gegen ISO 6346", null);
        }
        String typ = (anlage.getGroesseFuss() == 40 ? "42" : "22") + (ware.isKuehlpflichtig() ? "R1" : "G1");
        ContainerInfo neu = new ContainerInfo(id, Iso6346.kompakt(kennung), anlage.getGroesseFuss(), typ,
                ware.getCode(), ware.getName(), ware.getFarbe(), ort.getOrtId(), ort.getName(), "BEREIT");
        liste.add(neu);
        marke++;
        return neu;
    }

    @Override
    public synchronized Ort ortAnlegen(OrtAnlage anlage) throws DienstFehler {
        if (anlage == null || anlage.getName() == null || anlage.getName().trim().isEmpty()) {
            throw new DienstFehler(DienstFehler.Art.ORT_NAME, "Bitte einen Stadtnamen eingeben", null);
        }
        String iso2 = anlage.getIso2() == null ? "" : anlage.getIso2().trim().toUpperCase();
        Land land = laender.get(iso2);
        if (land == null) {
            throw new DienstFehler(DienstFehler.Art.LAND_UNBEKANNT, "Land " + iso2 + " unbekannt", null);
        }
        if (Double.isNaN(anlage.getLaenge()) || Double.isNaN(anlage.getBreite())
                || anlage.getLaenge() < -180 || anlage.getLaenge() > 180
                || anlage.getBreite() < -90 || anlage.getBreite() > 90) {
            throw new DienstFehler(DienstFehler.Art.KOORDINATEN, "Länge muss zwischen -180 und 180, Breite zwischen -90 und 90 liegen", null);
        }
        if (anlage.getKapazitaetTeu() != null && anlage.getKapazitaetTeu() <= 0) {
            throw new DienstFehler(DienstFehler.Art.ORT_KAPAZITAET, "Die Kapazität muss größer als 0 sein", null);
        }
        String name = anlage.getName().trim();
        for (Ort alt : orte.values()) {
            if (alt.getName().equalsIgnoreCase(name)) {
                throw new DienstFehler(DienstFehler.Art.ORT_NAME, "Die Stadt gibt es bereits", null);
            }
        }
        int id = 0;
        for (Integer alt : orte.keySet()) {
            id = Math.max(id, alt.intValue());
        }
        Ort neu = new Ort(id + 1, name, iso2, land.getName(), land.getZone(),
                anlage.getLaenge(), anlage.getBreite(), anlage.getKapazitaetTeu());
        orte.put(neu.getOrtId(), neu);
        marke++;
        return neu;
    }

    @Override
    public synchronized List<Ort> orte() {
        List<Ort> out = new ArrayList<Ort>(orte.values());
        Collections.sort(out, new java.util.Comparator<Ort>() {
            @Override
            public int compare(Ort a, Ort b) {
                return Integer.compare(a.getOrtId(), b.getOrtId());
            }
        });
        return out;
    }

    /** Die eigentliche Fahrt; {@code ausgeloest} wie in LOG_BEWEGUNG (HAND oder AUFTRAG). */
    private Fahrt fahren(int id, int nach, String ausgeloest) throws DienstFehler {
        aufrufe++;
        warte();
        if (naechsterScheitert) {
            naechsterScheitert = false;
            throw new DienstFehler(DienstFehler.Art.DATENBANK, "ORA-03113 (gespielt)", null);
        }
        Ort z = orte.get(nach);
        if (z == null) {
            throw new DienstFehler(DienstFehler.Art.ORT_UNBEKANNT, "Ort " + nach + " unbekannt", null);
        }
        for (int i = 0; i < liste.size(); i++) {
            ContainerInfo c = liste.get(i);
            if (c.getContainerId() != id) {
                continue;
            }
            if ("ZOLL".equals(c.getStatus())) {
                throw new DienstFehler(DienstFehler.Art.BEIM_ZOLL,
                        "Container " + id + " steht beim Zoll und muss erst freigegeben werden", null);
            }
            if (c.getOrtId() == nach) {
                throw new DienstFehler(DienstFehler.Art.SCHON_DA, "Container " + id + " steht bereits am Zielort", null);
            }
            Ort v = orte.get(c.getOrtId());
            if (z.getKapazitaetTeu() != null) {
                int b = belegtTeu(nach);
                if (b + c.getTeu() > z.getKapazitaetTeu()) {
                    throw new DienstFehler(DienstFehler.Art.ZIEL_VOLL, z.getName() + " ist voll: " + b + " von "
                            + z.getKapazitaetTeu() + " TEU belegt, der Container braucht " + c.getTeu(), null);
                }
            }
            boolean zoll = Regeln.zollNoetig(laender.get(v.getIso2()), laender.get(z.getIso2()));
            long m = Math.round(Geodaesie.entfernungM(v.getLaenge(), v.getBreite(), z.getLaenge(), z.getBreite()));
            java.math.BigDecimal fracht = tarif == null ? null : tarif.fracht(m, c.getTeu(), c.getWareCode());
            java.math.BigDecimal zollEur = tarif == null ? null : tarif.zoll(zoll);
            liste.set(i, new ContainerInfo(id, c.getKennung(), c.getGroesseFuss(), c.getTypCode(),
                    c.getWareCode(), c.getWare(), c.getFarbe(), nach, z.getName(), zoll ? "ZOLL" : "BEREIT"));
            long bid = naechsteBewegung++;
            bewegungen.add(new Bewegung(bid, id, Iso6346.anzeige(c.getKennung()), v.getOrtId(), v.getName(),
                    z.getOrtId(), z.getName(), v.getZone().name(), z.getZone().name(), zoll, m,
                    uhr.jetzt()).mitKosten(fracht, zollEur, null, 0, ausgeloest));
            marke++;
            return new Fahrt(bid, v.getZone().name(), z.getZone().name(), zoll, m,
                    fracht == null || zollEur == null ? null : fracht.add(zollEur));
        }
        throw new DienstFehler(DienstFehler.Art.CONTAINER_UNBEKANNT, "Container " + id + " unbekannt", null);
    }

    @Override
    public synchronized void zollFreigeben(int id) throws DienstFehler {
        warte();
        for (int i = 0; i < liste.size(); i++) {
            ContainerInfo c = liste.get(i);
            if (c.getContainerId() == id && "ZOLL".equals(c.getStatus())) {
                liste.set(i, new ContainerInfo(id, c.getKennung(), c.getGroesseFuss(), c.getTypCode(),
                        c.getWareCode(), c.getWare(), c.getFarbe(), c.getOrtId(), c.getOrt(), "BEREIT"));
                Bewegung b = letzte(id);
                if (b != null && b.isZoll() && b.getFreigegebenAmMs() == 0) {
                    long jetzt = uhr.jetzt();
                    b.mitKosten(b.getFrachtEur(), b.getZollEur(),
                            tarif == null ? null : tarif.standgeld(jetzt - b.getZeitpunktMs()), jetzt, b.getAusgeloest());
                }
                marke++;
                return;
            }
        }
        throw new DienstFehler(DienstFehler.Art.NICHTS_FREIZUGEBEN, "nichts freizugeben", null);
    }

    @Override
    public synchronized List<ContainerInfo> container() {
        return new ArrayList<ContainerInfo>(liste);
    }

    /** Wie LOG_ZOLL_V: Status ZOLL, dazu die letzte Fahrt; die am l&auml;ngsten Wartenden zuerst. */
    @Override
    public synchronized List<ZollFall> zollFaelle() {
        long jetzt = uhr.jetzt();
        List<ZollFall> out = new ArrayList<ZollFall>();
        List<Bewegung> sortiert = new ArrayList<Bewegung>();
        for (ContainerInfo c : liste) {
            if (!"ZOLL".equals(c.getStatus())) {
                continue;
            }
            Bewegung b = letzte(c.getContainerId());
            if (b != null) {
                sortiert.add(b);
            }
        }
        // Bewegungsnummern steigen mit der Zeit: die kleinste wartet am laengsten
        Collections.sort(sortiert, new java.util.Comparator<Bewegung>() {
            @Override
            public int compare(Bewegung a, Bewegung b) {
                return Long.compare(a.getBewegungId(), b.getBewegungId());
            }
        });
        for (Bewegung b : sortiert) {
            ContainerInfo c = finde(b.getContainerId());
            Ort v = orte.get(b.getVonOrtId());
            Ort n = orte.get(b.getNachOrtId());
            out.add(new ZollFall(c.getContainerId(), b.getKennungAnzeige(), c.getWare(), c.getFarbe(), n.getOrtId(),
                    n.getName(), v.getName(), v.getIso2(), n.getIso2(), b.getVonZone(), b.getNachZone(),
                    Math.max(0, (jetzt - b.getZeitpunktMs()) / 1000), jetzt));
        }
        return out;
    }

    @Override
    public synchronized List<Bewegung> letzteBewegungen(int max) {
        List<Bewegung> out = new ArrayList<Bewegung>();
        for (int i = bewegungen.size() - 1; i >= 0 && out.size() < max; i--) {
            out.add(bewegungen.get(i));
        }
        return out;
    }

    @Override
    public synchronized List<com.dan.logistikapp.model.KostenZeile> kosten() {
        Map<Integer, String[]> ware = new HashMap<Integer, String[]>();
        for (ContainerInfo c : liste) {
            ware.put(c.getContainerId(), new String[] {c.getWareCode(), c.getWare()});
        }
        return com.dan.logistikapp.model.KostenZeile.aus(bewegungen, ware);
    }

    @Override
    public synchronized List<Bewegung> historie(int containerId) {
        List<Bewegung> out = new ArrayList<Bewegung>();
        for (Bewegung b : bewegungen) {
            if (b.getContainerId() == containerId) {
                out.add(b);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ Auftraege

    @Override
    public synchronized long auftragAnlegen(int containerId, int nachOrtId, long faelligMs) throws DienstFehler {
        if (finde(containerId) == null) {
            throw new DienstFehler(DienstFehler.Art.CONTAINER_UNBEKANNT, "Container " + containerId + " gibt es nicht", null);
        }
        if (!orte.containsKey(nachOrtId)) {
            throw new DienstFehler(DienstFehler.Art.ORT_UNBEKANNT, "Zielort " + nachOrtId + " gibt es nicht", null);
        }
        Plan p = new Plan();
        p.id = naechsterAuftrag++;
        p.containerId = containerId;
        p.nach = nachOrtId;
        p.faellig = faelligMs;
        plaene.add(p);
        marke++;
        return p.id;
    }

    @Override
    public synchronized void auftragStornieren(long auftragId) throws DienstFehler {
        Plan p = plan(auftragId);
        if (!"OFFEN".equals(p.status)) {
            throw new DienstFehler(DienstFehler.Art.AUFTRAG_NICHT_OFFEN,
                    "Auftrag " + auftragId + " ist nicht mehr offen (" + p.status + ")", null);
        }
        p.status = "STORNIERT";
        p.erledigt = uhr.jetzt();
        marke++;
    }

    /** Wie LOG_API.auftrag_ausfuehren - dieselbe Reihenfolge der Pr&uuml;fungen. */
    @Override
    public synchronized AuftragErgebnis auftragAusfuehren(long auftragId) throws DienstFehler {
        Plan p = plan(auftragId);
        if (!"OFFEN".equals(p.status)) {
            throw new DienstFehler(DienstFehler.Art.AUFTRAG_NICHT_OFFEN,
                    "Auftrag " + auftragId + " ist nicht mehr offen (" + p.status + ")", null);
        }
        long jetzt = uhr.jetzt();
        if (p.faellig > jetzt) {
            return new AuftragErgebnis("NICHT_FAELLIG", null, p.grund);
        }
        for (Plan x : plaene) {
            if (x != p && x.containerId == p.containerId && "OFFEN".equals(x.status)
                    && (x.faellig < p.faellig || (x.faellig == p.faellig && x.id < p.id))) {
                return new AuftragErgebnis("WARTET", null, p.grund);
            }
        }
        ContainerInfo c = finde(p.containerId);
        if (c.getOrtId() == p.nach) {
            p.status = "ERLEDIGT";
            p.grund = "stand schon am Ziel - ohne Fahrt";
            p.von = "APP";
            p.erledigt = jetzt;
            marke++;
            return new AuftragErgebnis("ERLEDIGT", null, p.grund);
        }
        try {
            Fahrt f = fahren(p.containerId, p.nach, "AUFTRAG");
            p.status = "ERLEDIGT";
            p.grund = null;
            p.bewegungId = f.getBewegungId();
            p.versuche++;
            p.von = "APP";
            p.erledigt = uhr.jetzt();
            marke++;
            return new AuftragErgebnis("ERLEDIGT", f, null);
        } catch (DienstFehler e) {
            p.versuche++;
            p.grund = e.getMessage();
            marke++;
            if (e.getArt() == DienstFehler.Art.BEIM_ZOLL || e.getArt() == DienstFehler.Art.ZIEL_VOLL) {
                return new AuftragErgebnis("BLOCKIERT", null, p.grund);
            }
            p.status = "GESCHEITERT";
            p.von = "APP";
            p.erledigt = uhr.jetzt();
            return new AuftragErgebnis("GESCHEITERT", null, p.grund);
        }
    }

    /** Wie LOG_AUFTRAG_V/SQL_LISTE: offene nach Termin, dann die j&uuml;ngsten 40 abgeschlossenen. */
    @Override
    public synchronized List<com.dan.logistikapp.model.Auftrag> auftraege() {
        List<Plan> offen = new ArrayList<Plan>();
        List<Plan> fertig = new ArrayList<Plan>();
        for (Plan p : plaene) {
            ("OFFEN".equals(p.status) ? offen : fertig).add(p);
        }
        java.util.Comparator<Plan> termin = new java.util.Comparator<Plan>() {
            @Override
            public int compare(Plan a, Plan b) {
                int c = Long.compare(a.faellig, b.faellig);
                return c != 0 ? c : Long.compare(a.id, b.id);
            }
        };
        Collections.sort(offen, termin);
        Collections.sort(fertig, new java.util.Comparator<Plan>() {
            @Override
            public int compare(Plan a, Plan b) {
                int c = Long.compare(b.erledigt, a.erledigt);
                return c != 0 ? c : Long.compare(b.id, a.id);
            }
        });
        Map<Integer, Integer> rang = new HashMap<Integer, Integer>();
        List<com.dan.logistikapp.model.Auftrag> out = new ArrayList<com.dan.logistikapp.model.Auftrag>();
        for (Plan p : offen) {
            Integer r = rang.get(p.containerId);
            r = r == null ? 1 : r + 1;
            rang.put(p.containerId, r);
            out.add(modell(p, r));
        }
        for (int i = 0; i < fertig.size() && i < 40; i++) {
            out.add(modell(fertig.get(i), 0));
        }
        return out;
    }

    @Override
    public synchronized String standMarke() {
        return "S" + marke;
    }

    private com.dan.logistikapp.model.Auftrag modell(Plan p, int rang) {
        ContainerInfo c = finde(p.containerId);
        Ort j = orte.get(c.getOrtId());
        Ort n = orte.get(p.nach);
        return new com.dan.logistikapp.model.Auftrag(p.id, p.containerId, Iso6346.anzeige(c.getKennung()), c.getWare(),
                c.getFarbe(), c.getOrtId(), j.getName(), c.getStatus(), p.nach, n.getName(), p.faellig,
                com.dan.logistikapp.model.Auftrag.Status.valueOf(p.status), p.grund, p.versuche, p.bewegungId, p.von,
                p.erledigt, rang);
    }

    private Plan plan(long id) throws DienstFehler {
        for (Plan p : plaene) {
            if (p.id == id) {
                return p;
            }
        }
        throw new DienstFehler(DienstFehler.Art.AUFTRAG_UNBEKANNT, "Auftrag " + id + " gibt es nicht", null);
    }

    private Bewegung letzte(int containerId) {
        for (int i = bewegungen.size() - 1; i >= 0; i--) {
            if (bewegungen.get(i).getContainerId() == containerId) {
                return bewegungen.get(i);
            }
        }
        return null;
    }

    private ContainerInfo finde(int id) {
        for (ContainerInfo c : liste) {
            if (c.getContainerId() == id) {
                return c;
            }
        }
        return null;
    }

    private void warte() {
        if (verzoegerungMs <= 0) {
            return;
        }
        try {
            Thread.sleep(verzoegerungMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
