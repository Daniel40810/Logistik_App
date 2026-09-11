package com.dan.logistikapp.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bestand je Stadt und Ware - dieselbe Rechnung wie LOG_BESTAND_V, aber
 * aus dem Containerstand, den die Karte ohnehin hat. Der Durchstich
 * pr&uuml;ft, dass beide dasselbe ergeben.
 *
 * <p>Anders als die Sicht enth&auml;lt die Liste auch St&auml;dte ohne Container:
 * ein leerer Stellplatz ist eine Auskunft.</p>
 *
 * @author Dan
 */
public final class Bestand {

    /** Eine Ware in einer Stadt. */
    public static final class Anteil {
        private final String wareCode;
        private final String ware;
        private final Color farbe;
        private int anzahl;
        private int teu;
        private int beimZoll;

        Anteil(String wareCode, String ware, Color farbe) {
            this.wareCode = wareCode;
            this.ware = ware;
            this.farbe = farbe;
        }

        public String getWareCode() {
            return wareCode;
        }

        public String getWare() {
            return ware;
        }

        public Color getFarbe() {
            return farbe;
        }

        public int getAnzahl() {
            return anzahl;
        }

        public int getTeu() {
            return teu;
        }

        public int getBeimZoll() {
            return beimZoll;
        }
    }

    /** Eine Stadt mit ihren Waren, in der Reihenfolge der Warenliste. */
    public static final class Stadt {
        private final Ort ort;
        private final List<Anteil> anteile = new ArrayList<Anteil>();
        private int anzahl;
        private int teu;
        private int beimZoll;

        Stadt(Ort ort) {
            this.ort = ort;
        }

        public Ort getOrt() {
            return ort;
        }

        public List<Anteil> getAnteile() {
            return Collections.unmodifiableList(anteile);
        }

        public int getAnzahl() {
            return anzahl;
        }

        public int getTeu() {
            return teu;
        }

        public int getBeimZoll() {
            return beimZoll;
        }
    }

    private Bestand() {
    }

    /**
     * @param orte    alle St&auml;dte - jede erscheint, auch ohne Container
     * @param waren   bestimmt die Reihenfolge der Anteile (Sortierung)
     * @param stand   der aktuelle Containerstand
     * @return St&auml;dte nach Zone (Inland, EU, Drittland), darin nach Name
     */
    public static List<Stadt> aus(List<Ort> orte, List<Ware> waren, List<ContainerInfo> stand) {
        final Map<String, Integer> rang = new HashMap<String, Integer>();
        for (Ware w : waren) {
            rang.put(w.getCode(), w.getSortierung());
        }
        Map<Integer, Stadt> je = new LinkedHashMap<Integer, Stadt>();
        for (Ort o : orte) {
            je.put(o.getOrtId(), new Stadt(o));
        }
        Map<String, Anteil> anteil = new HashMap<String, Anteil>();
        for (ContainerInfo c : stand) {
            Stadt s = je.get(c.getOrtId());
            if (s == null) {
                continue;
            }
            String k = c.getOrtId() + "|" + c.getWareCode();
            Anteil a = anteil.get(k);
            if (a == null) {
                a = new Anteil(c.getWareCode(), c.getWare(), c.getFarbe());
                anteil.put(k, a);
                s.anteile.add(a);
            }
            boolean zoll = "ZOLL".equals(c.getStatus());
            a.anzahl++;
            a.teu += c.getTeu();
            a.beimZoll += zoll ? 1 : 0;
            s.anzahl++;
            s.teu += c.getTeu();
            s.beimZoll += zoll ? 1 : 0;
        }
        List<Stadt> out = new ArrayList<Stadt>(je.values());
        for (Stadt s : out) {
            Collections.sort(s.anteile, new Comparator<Anteil>() {
                @Override
                public int compare(Anteil a, Anteil b) {
                    Integer ra = rang.get(a.wareCode);
                    Integer rb = rang.get(b.wareCode);
                    int c = Integer.compare(ra == null ? 999 : ra, rb == null ? 999 : rb);
                    return c != 0 ? c : a.wareCode.compareTo(b.wareCode);
                }
            });
        }
        Collections.sort(out, new Comparator<Stadt>() {
            @Override
            public int compare(Stadt a, Stadt b) {
                int c = a.ort.getZone().compareTo(b.ort.getZone());
                return c != 0 ? c : a.ort.getName().compareTo(b.ort.getName());
            }
        });
        return out;
    }
}
