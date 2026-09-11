import com.dan.logistikapp.geo.GeoFlaeche;
import com.dan.logistikapp.geo.Laea3035;
import com.dan.logistikapp.geo.Wkt;
import com.dan.logistikapp.karte.KartenAnsicht;
import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.karte.KartenModell;
import com.dan.logistikapp.karte.KartenPanel;
import com.dan.logistikapp.karte.KartenQuelle;
import com.dan.logistikapp.karte.ContainerEbene;
import com.dan.logistikapp.mesh.ContainerMesh;
import com.dan.logistikapp.mesh.ContainerRenderer;
import com.dan.logistikapp.mesh.ContainerSprite;
import com.dan.logistikapp.mesh.SpriteCache;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.Iso6346;
import com.dan.logistikapp.model.Land;
import com.dan.logistikapp.model.Ort;
import com.dan.logistikapp.model.Ware;
import com.dan.logistikapp.model.Zone;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Karte ohne Datenbank pruefen: Projektion, WKT, Flaechen, Ansicht und ein
 * Probebild. Die Laender kommen aus db/07_grenzen.tsv, ihre Zonen aus
 * den Skripten 05/06 - dieselben Daten, die auch in DEMO landen.
 *
 * <p>Ergebnis: karte_test.log und karte_vorschau.png.</p>
 *
 * @author Dan
 */
public final class LogKarteTest {

    private static PrintWriter log;
    private static int ok;
    private static int rot;

    private LogKarteTest() {
    }

    public static void main(String[] args) throws Exception {
        File base = new File(".").getCanonicalFile();
        log = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(base, "karte_test.log")),
                StandardCharsets.UTF_8), true);
        say("Logistik_App - Karte ohne Datenbank");
        say("");
        try {
            DateiQuelle q = new DateiQuelle(new File(base, "db"));
            // -Dnur=kapazitaet: nur den juengsten Abschnitt (schneller beim Entwickeln)
            if ("kapazitaet".equals(System.getProperty("nur"))) {
                kapazitaetKosten(q, base);
                throw new IllegalStateException("nur Abschnitt kapazitaet gelaufen (-Dnur)");
            }
            if ("politur".equals(System.getProperty("nur"))) {
                politur(q, base);
                throw new IllegalStateException("nur Abschnitt politur gelaufen (-Dnur)");
            }
            if ("zeitreise".equals(System.getProperty("nur"))) {
                zeitreise(q, base);
                throw new IllegalStateException("nur Abschnitt zeitreise gelaufen (-Dnur)");
            }
            if ("auftraege".equals(System.getProperty("nur"))) {
                auftraege(q, base);
                throw new IllegalStateException("nur Abschnitt auftraege gelaufen (-Dnur)");
            }
            projektion();
            daten(q);
            ansicht();
            container(q, new File(base, "container_vorschau.png"));
            bild(q, new File(base, "karte_vorschau.png"));
            geodaesie();
            ziehen(q, new File(base, "ziehen_vorschau.png"));
            zonenregeln(q, new File(base, "zoll_vorschau.png"));
            politur(q, base);
            kapazitaetKosten(q, base);
            auftraege(q, base);
            zeitreise(q, base);
        } catch (Throwable t) {
            rot++;
            say("[ABBRUCH] " + t);
            t.printStackTrace();
        }
        say("");
        say("Ergebnis: " + ok + "/" + (ok + rot) + " gruen" + (rot == 0 ? "" : "   (" + rot + " ROT)"));
        log.close();
        // Swing-Timer (Zoll-Sekunde, Ruhe) halten sonst den Ereignis-Thread und damit das Programm am Leben
        System.exit(0);
    }

    // ------------------------------------------------------------ Projektion

    private static void projektion() {
        say("Projektion ETRS89-LAEA");
        double[] p = Laea3035.vor(5, 50);
        check("EPSG-Beispiel 50N 5O -> 3962799,45 / 2999718,85",
                Math.abs(p[0] - 3962799.45) < 0.01 && Math.abs(p[1] - 2999718.85) < 0.01,
                String.format("%.3f / %.3f", p[0], p[1]));
        double[] m = Laea3035.vor(10, 52);
        check("Mittelpunkt 52N 10O liegt auf dem falschen Ursprung",
                Math.abs(m[0] - 4321000) < 1e-6 && Math.abs(m[1] - 3210000) < 1e-6, null);
        double maxFehler = 0;
        for (double lat = 30; lat <= 75; lat += 1.5) {
            for (double lon = -30; lon <= 50; lon += 2.5) {
                double[] v = Laea3035.vor(lon, lat);
                double[] z = Laea3035.zurueck(v[0], v[1]);
                maxFehler = Math.max(maxFehler, Math.max(Math.abs(z[0] - lon), Math.abs(z[1] - lat)));
            }
        }
        // 1e-7 Grad sind hoechstens 1 cm - die Reihe fuer die Breite ist nach dem
        // e^6-Glied abgeschnitten, genauer braucht es keine Karte
        check("Hin und zurueck ueber ganz Europa, besser als 1 cm", maxFehler < 1e-7,
                String.format("groesster Fehler %.2e Grad = %.1f mm", maxFehler, maxFehler * 111320000));
        // Flaechentreue: ein Grad-Feld bei 50N hat auf dem Ellipsoid rund 7 900 km2
        GeoFlaeche feld = Wkt.lies("POLYGON((10 50, 11 50, 11 51, 10 51, 10 50))");
        double qkm = feld.flaecheQkm();
        check("Flaechentreu: 1x1-Grad-Feld bei 50N rund 7 900 km2", qkm > 7800 && qkm < 8000,
                String.format("%.1f km2", qkm));
        say("");
    }

    // ------------------------------------------------------------ Daten

    private static void daten(DateiQuelle q) {
        say("Laenderdaten aus db/07_grenzen.tsv");
        List<Land> laender = q.laender();
        check("63 Laender gelesen", laender.size() == 63, laender.size() + " Laender");
        int vAbw = 0;
        for (Land l : laender) {
            Integer soll = q.vertexSoll.get(l.getIso2());
            if (soll == null || l.getGrenze().stuetzpunkte() != soll) {
                vAbw++;
            }
        }
        check("WKT-Leser zaehlt jeden Stuetzpunkt", vAbw == 0, vAbw + " Abweichungen");
        // So wie die Anwendung jetzt liest: SDO_ELEM_INFO + SDO_ORDINATES
        int sdoAbw = 0;
        for (Land l : laender) {
            List<Long> elem = new ArrayList<Long>();
            List<Double> ord = new ArrayList<Double>();
            for (List<double[]> poly : l.getGrenze().getPolygone()) {
                for (int k = 0; k < poly.size(); k++) {
                    elem.add((long) ord.size() + 1);
                    elem.add(k == 0 ? 1003L : 2003L);
                    elem.add(1L);
                    for (double v : poly.get(k)) {
                        ord.add(v);
                    }
                }
            }
            long[] e = new long[elem.size()];
            for (int i = 0; i < e.length; i++) {
                e[i] = elem.get(i);
            }
            double[] o = new double[ord.size()];
            for (int i = 0; i < o.length; i++) {
                o[i] = ord.get(i);
            }
            GeoFlaeche g = GeoFlaeche.ausSdo(2007, e, o);
            if (g.stuetzpunkte() != l.getGrenze().stuetzpunkte()
                    || g.getPolygone().size() != l.getGrenze().getPolygone().size()
                    || Math.abs(g.flaecheQkm() - l.getGrenze().flaecheQkm()) > 1e-6) {
                sdoAbw++;
            }
        }
        check("SDO-Leser (ELEM_INFO/ORDINATES) ergibt dieselben Flaechen", sdoAbw == 0, sdoAbw + " Abweichungen");
        boolean abgewiesen = false;
        try {
            GeoFlaeche.ausSdo(2003, new long[] {1, 1005, 2}, new double[] {0, 0, 1, 1});
        } catch (IllegalArgumentException ex) {
            abgewiesen = true;
        }
        check("SDO-Leser weist zusammengesetzte Ringe ab statt sie halb zu lesen", abgewiesen, null);
        int ohneZone = 0;
        Map<Zone, Integer> zonen = new HashMap<Zone, Integer>();
        for (Land l : laender) {
            if (l.getZone() == null) {
                ohneZone++;
            } else {
                zonen.put(l.getZone(), zonen.containsKey(l.getZone()) ? zonen.get(l.getZone()) + 1 : 1);
            }
        }
        check("Jedes Land hat eine Zone", ohneZone == 0, zonen.toString());
        Land de = null;
        for (Land l : laender) {
            if ("DE".equals(l.getIso2())) {
                de = l;
            }
        }
        double qkm = de == null ? 0 : de.getGrenze().flaecheQkm();
        check("Deutschland rund 357 000 km2", qkm > 345000 && qkm < 365000, String.format("%.0f km2", qkm));
        KartenModell m = KartenModell.aus(q);
        int drin = 0;
        for (KartenModell.OrtPunkt p : m.getPunkte()) {
            KartenModell.LandForm f = m.landBei(p.getX(), p.getY());
            if (f != null && f.getLand().getIso2().equals(p.getOrt().getIso2())) {
                drin++;
            } else {
                say("         " + p.getOrt().getName() + " liegt in " + (f == null ? "keinem Land" : f.getLand().getIso2()));
            }
        }
        check("Jede Stadt liegt in ihrem eigenen Land (projiziert)", drin == m.getPunkte().size(),
                drin + "/" + m.getPunkte().size());
        say("");
    }

    // ------------------------------------------------------------ Ansicht

    private static void ansicht() {
        say("Ansicht");
        KartenAnsicht a = new KartenAnsicht();
        a.setGroesse(1200, 800);
        a.einpassen(new java.awt.geom.Rectangle2D.Double(3e6, 1.5e6, 2e6, 2e6), 0.1);
        Point2D.Double w = a.zuWelt(300, 200);
        a.zoomUm(300, 200, 1.7);
        Point2D.Double w2 = a.zuWelt(300, 200);
        check("Zoomen haelt den Punkt unter der Maus fest", w.distance(w2) < 1e-6,
                String.format("%.2e m", w.distance(w2)));
        Point2D.Double s = a.zuBildschirm(w2.x, w2.y);
        check("Welt -> Bild -> Welt", Math.abs(s.x - 300) < 1e-9 && Math.abs(s.y - 200) < 1e-9, null);
        Point2D.Double nord = a.zuBildschirm(w2.x, w2.y + 1000);
        check("Norden ist oben", nord.y < s.y, null);
        for (int i = 0; i < 100; i++) {
            a.zoomUm(600, 400, 2);
        }
        check("Zoom bleibt begrenzt", Math.abs(a.getMassstab() - KartenAnsicht.MAX_MASSSTAB) < 1e-15, null);
        say("");
    }

    // ------------------------------------------------------------ Container

    private static void container(DateiQuelle q, File png) throws Exception {
        say("Container (RayPhong)");
        int dreiecke = 0;
        for (ContainerMesh.Teil t : ContainerMesh.baue(20, false)) {
            dreiecke += t.mesh.triangleCount();
        }
        check("20-Fuss-Mesh gebaut: Wand, Rahmen, Stahl", ContainerMesh.baue(20, false).size() == 3
                && ContainerMesh.baue(20, true).size() == 4, dreiecke + " Dreiecke, Reefer mit Aggregat");

        Color rot = new Color(0x8C3A22);
        ContainerSprite s20 = ContainerRenderer.render(rot, 20, false, 0, 12);
        ContainerSprite s40 = ContainerRenderer.render(rot, 40, false, 0, 12);
        int ecke = s20.getBild().getRGB(0, 0) >>> 24;
        check("Grund bleibt durchsichtig", ecke == 0, "Alpha oben links " + ecke);
        double verh = nichtLeer(s40.getBild()).getWidth() / nichtLeer(s20.getBild()).getWidth();
        check("40 Fuss ist doppelt so lang wie 20 Fuss (ISO 668)", verh > 1.8 && verh < 2.1,
                String.format("Verhaeltnis %.2f, soll 12,192/6,058 = 2,01 (plus Rahmen)", verh));
        // Dachmitte: ueber dem Anker, um die Hoehe versetzt - dort muss die Warenfarbe liegen
        double hoch = ContainerMesh.HOEHE * Math.cos(Math.toRadians(ContainerRenderer.ERHEBUNG_GRAD)) * 12;
        Color dach = new Color(s20.getBild().getRGB((int) s20.getAnkerX(), (int) (s20.getAnkerY() - hoch)));
        check("Dach traegt die Warenfarbe (Rot vor Gruen vor Blau wie Eisen)",
                dach.getRed() > dach.getGreen() && dach.getGreen() > dach.getBlue(),
                dach.getRed() + "," + dach.getGreen() + "," + dach.getBlue());
        ContainerSprite weiss = ContainerRenderer.render(Color.WHITE, 20, false, 45, 12);
        ContainerSprite reefer = ContainerRenderer.render(Color.WHITE, 20, true, 45, 12);
        int unterschied = 0;
        for (int y = 0; y < weiss.getBild().getHeight(); y++) {
            for (int x = 0; x < weiss.getBild().getWidth(); x++) {
                if (weiss.getBild().getRGB(x, y) != reefer.getBild().getRGB(x, y)) {
                    unterschied++;
                }
            }
        }
        check("Reefer hat sein Kuehlaggregat an der Stirnseite", unterschied > 50, unterschied + " Bildpunkte anders");
        check("Drehstufen: -22,5 -> 15, 360 -> 0, 191 -> 8",
                SpriteCache.stufe(-22.5) == 15 && SpriteCache.stufe(360) == 0 && SpriteCache.stufe(191) == 8, null);
        SpriteCache c = new SpriteCache(8);
        ContainerSprite a1 = c.get(rot, 20, false, 3);
        ContainerSprite a2 = c.get(rot, 20, false, 3);
        check("Cache rendert jedes Bild nur einmal", a1 == a2 && c.groesse() == 1, null);

        // Stellplatz Hamburg: 40, 20, 20, 20, 40 Fuss -> Reihen zu hoechstens 3 TEU
        List<ContainerInfo> ham = new ArrayList<ContainerInfo>();
        for (ContainerInfo x : q.container()) {
            if ("Hamburg".equals(x.getOrt())) {
                ham.add(x);
            }
        }
        java.lang.reflect.Method m = ContainerEbene.class.getDeclaredMethod("reihen", List.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<List<ContainerInfo>> reihen = (List<List<ContainerInfo>>) m.invoke(null, ham);
        boolean passt = true;
        int anzahl = 0;
        for (List<ContainerInfo> r : reihen) {
            int teu = 0;
            for (ContainerInfo x : r) {
                teu += x.getTeu();
                anzahl++;
            }
            passt &= teu <= ContainerEbene.REIHE_TEU;
        }
        check("Stellplatz: keine Reihe ueber 3 TEU, keiner geht verloren", passt && anzahl == ham.size(),
                ham.size() + " Container in " + reihen.size() + " Reihen");

        // Musterbild aller Waren
        BufferedImage bild = new BufferedImage(900, 190, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bild.createGraphics();
        g.setColor(KartenFarben.flaeche(Zone.INLAND));
        g.fillRect(0, 0, 900, 190);
        int x = 16;
        for (Ware w : q.waren()) {
            ContainerSprite sp = ContainerRenderer.render(w.getFarbe(), 20, w.isKuehlpflichtig(), 20, 18);
            g.drawImage(sp.getBild(), x, 20, null);
            g.setColor(KartenFarben.TEXT);
            g.drawString(w.getName(), x + 20, 175);
            x += sp.getBild().getWidth() + 16;
        }
        g.dispose();
        ImageIO.write(bild, "png", png);
        say("   Bild: " + png.getName());
        say("");
    }

    /** Rahmen um alle nicht durchsichtigen Bildpunkte. */
    private static java.awt.geom.Rectangle2D nichtLeer(BufferedImage b) {
        int x0 = b.getWidth();
        int x1 = -1;
        int y0 = b.getHeight();
        int y1 = -1;
        for (int y = 0; y < b.getHeight(); y++) {
            for (int x = 0; x < b.getWidth(); x++) {
                if ((b.getRGB(x, y) >>> 24) > 20) {
                    x0 = Math.min(x0, x);
                    x1 = Math.max(x1, x);
                    y0 = Math.min(y0, y);
                    y1 = Math.max(y1, y);
                }
            }
        }
        return new java.awt.geom.Rectangle2D.Double(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    // ------------------------------------------------------------ Bild

    private static void bild(DateiQuelle q, File png) throws Exception {
        say("Probebild");
        KartenModell m = KartenModell.aus(q);
        KartenPanel p = new KartenPanel();
        p.setSize(1400, 900);
        p.setModell(m);
        p.allesZeigen();
        BufferedImage img = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
        // Aufwaermen: die ersten Bilder messen den JIT-Uebersetzer, nicht die Karte
        Graphics2D g;
        for (int i = 0; i < 20; i++) {
            p.getAnsicht().zoomUm(700, 450, i % 2 == 0 ? 1.01 : 1 / 1.01);
            g = img.createGraphics();
            p.paint(g);
            g.dispose();
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < 3; i++) {
            p.getAnsicht().zoomUm(700, 450, i % 2 == 0 ? 1.01 : 1 / 1.01);
            g = img.createGraphics();
            p.paint(g);
            g.dispose();
        }
        long scharf = (System.nanoTime() - t0) / 3000000;
        // Stillstand: das Ebenenbild wird nur noch kopiert
        t0 = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            g = img.createGraphics();
            p.paint(g);
            g.dispose();
        }
        long ruhig = (System.nanoTime() - t0) / 10000000;
        // Ziehen: je Bild ein Stueck verschieben, das Ebenenbild wird nur versetzt
        BufferedImage zieh = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
        java.awt.event.MouseEvent druck = new java.awt.event.MouseEvent(p, java.awt.event.MouseEvent.MOUSE_PRESSED,
                0, 0, 700, 450, 1, false, java.awt.event.MouseEvent.BUTTON1);
        for (java.awt.event.MouseListener l : p.getMouseListeners()) {
            l.mousePressed(druck);
        }
        t0 = System.nanoTime();
        for (int i = 1; i <= 10; i++) {
            java.awt.event.MouseEvent zug = new java.awt.event.MouseEvent(p, java.awt.event.MouseEvent.MOUSE_DRAGGED,
                    0, 0, 700 + 6 * i, 450 + 3 * i, 0, false, java.awt.event.MouseEvent.BUTTON1);
            for (java.awt.event.MouseMotionListener l : p.getMouseMotionListeners()) {
                l.mouseDragged(zug);
            }
            g = zieh.createGraphics();
            p.paint(g);
            g.dispose();
        }
        long ziehen = (System.nanoTime() - t0) / 10000000;
        p.ruheJetzt();
        p.allesZeigen();
        g = img.createGraphics();
        p.paint(g);
        g.dispose();
        List<ContainerEbene.Platz> pl = p.getContainerEbene().getPlaetze();
        Set<Integer> ids = new HashSet<Integer>();
        boolean unterStadt = true;
        boolean ueberlappt = false;
        for (int i = 0; i < pl.size(); i++) {
            ContainerEbene.Platz a = pl.get(i);
            ids.add(a.getContainer().getContainerId());
            KartenModell.OrtPunkt o = m.punkt(a.getContainer().getOrtId());
            Point2D.Double os = p.getAnsicht().zuBildschirm(o.getX(), o.getY());
            unterStadt &= a.getAnkerY() > os.y && Math.abs(a.getAnkerX() - os.x) < 60;
            for (int j = i + 1; j < pl.size(); j++) {
                ContainerEbene.Platz b = pl.get(j);
                if (a.getContainer().getOrtId() == b.getContainer().getOrtId()
                        && Math.abs(a.getAnkerY() - b.getAnkerY()) < 1e-6
                        && a.getBild().intersects(b.getBild())) {
                    ueberlappt = true;
                }
            }
        }
        check("Jeder Container steht genau einmal auf der Karte", pl.size() == q.container().size()
                && ids.size() == pl.size(), pl.size() + " von " + q.container().size());
        check("Stellplaetze liegen unter ihrer Stadt, ohne Ueberlappung in der Reihe", unterStadt && !ueberlappt, null);
        ContainerEbene.Platz ziel = pl.get(0);
        java.awt.event.MouseEvent zeig = new java.awt.event.MouseEvent(p, java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0,
                (int) ziel.getAnkerX(), (int) ziel.getAnkerY() - 3, 0, false);
        for (java.awt.event.MouseMotionListener l : p.getMouseMotionListeners()) {
            l.mouseMoved(zeig);
        }
        String hin = p.getHinweis();
        check("Maus ueber einem Container nennt seine Kennung",
                hin.startsWith(Iso6346.anzeige(ziel.getContainer().getKennung())), hin);
        check("Scharfes Neuzeichnen nach dem Ruhen", scharf < 250, scharf + " ms (nur nach Ziehen/Zoomen)");
        check("Stillstand und Hover fluessig", ruhig < 16, ruhig + " ms je Bild");
        check("Ziehen fluessig (Ebenenbild wird nur versetzt)", ziehen < 20, ziehen + " ms je Bild");
        ImageIO.write(img, "png", png);

        // Jede Zonenfarbe muss als Flaeche im Bild vorkommen
        Set<Integer> farben = new HashSet<Integer>();
        for (int y = 0; y < img.getHeight(); y += 3) {
            for (int x = 0; x < img.getWidth(); x += 3) {
                farben.add(img.getRGB(x, y) & 0xFFFFFF);
            }
        }
        boolean alle = true;
        for (Zone z : Zone.values()) {
            boolean da = farben.contains(KartenFarben.flaeche(z).getRGB() & 0xFFFFFF);
            alle &= da;
            if (!da) {
                say("         Zonenfarbe fehlt: " + z);
            }
        }
        check("Alle drei Zonen sind als Flaeche zu sehen", alle, farben.size() + " Farben im Raster");
        // Hamburg: die Marke muss an der berechneten Stelle sitzen
        KartenModell.OrtPunkt ham = null;
        for (KartenModell.OrtPunkt o : m.getPunkte()) {
            if ("Hamburg".equals(o.getOrt().getName())) {
                ham = o;
            }
        }
        Point2D.Double hs = p.getAnsicht().zuBildschirm(ham.getX(), ham.getY());
        Color c = new Color(img.getRGB((int) Math.round(hs.x), (int) Math.round(hs.y)));
        Color soll = KartenFarben.marke(Zone.INLAND);
        int d = Math.abs(c.getRed() - soll.getRed()) + Math.abs(c.getGreen() - soll.getGreen())
                + Math.abs(c.getBlue() - soll.getBlue());
        check("Hamburgs Marke sitzt an der berechneten Stelle", d < 40,
                String.format("Bildpunkt (%d,%d) = %d,%d,%d", (int) hs.x, (int) hs.y, c.getRed(), c.getGreen(), c.getBlue()));
        say("   Bild: " + png.getName());
    }

    // ------------------------------------------------------------ Geodaesie

    private static void geodaesie() {
        say("");
        say("Entfernung und Richtung");
        // Vincentys eigenes Pruefbeispiel (1975): Flinders Peak -> Buninyong
        double m = com.dan.logistikapp.geo.Geodaesie.entfernungM(
                144 + 25 / 60.0 + 29.52440 / 3600, -(37 + 57 / 60.0 + 3.72030 / 3600),
                143 + 55 / 60.0 + 35.38390 / 3600, -(37 + 39 / 60.0 + 10.15610 / 3600));
        check("Vincenty: Flinders Peak - Buninyong = 54 972,271 m", Math.abs(m - 54972.271) < 0.01,
                String.format("%.3f m", m));
        double[][] r = com.dan.logistikapp.geo.Geodaesie.grosskreis(9.9937, 53.5511, 2.3522, 48.8566, 16);
        check("Grosskreis beginnt und endet an den Staedten",
                Math.abs(r[0][0] - 9.9937) < 1e-9 && Math.abs(r[16][1] - 48.8566) < 1e-9, r.length + " Punkte");
        double ost = gier(1, 0);
        double nord = gier(0, -1);
        check("Container faehrt nach Osten: Tueren nach Westen (180 Grad)", Math.abs(ost - 180) < 1e-9, null);
        check("Container faehrt nach Norden: 270 Grad", Math.abs(nord - 270) < 1e-9, null);
        check("Schraeg auf dem Bild ist steiler im Raum (Blick unter 52 Grad)", gier(1, -1) - 180 > 45,
                String.format("45 Grad auf dem Bild = %.1f Grad im Raum", gier(1, -1) - 180));
    }

    private static double gier(double dx, double dy) {
        try {
            java.lang.reflect.Method m = Class.forName("com.dan.logistikapp.karte.Ziehen")
                    .getDeclaredMethod("gierAusBild", double.class, double.class);
            m.setAccessible(true);
            return (Double) m.invoke(null, dx, dy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------ Ziehen

    /** Der Dienst im Speicher aus src - derselbe, mit dem die Demo spielt. */
    static com.dan.logistikapp.dienst.SpeicherDienst speicher(DateiQuelle q) {
        return new com.dan.logistikapp.dienst.SpeicherDienst(q.orte(), q.laender(), q.container())
                .setVerzoegerungMs(300).setTarif(q.tarif());
    }

    private static void ziehen(DateiQuelle q, File png) throws Exception {
        say("");
        say("Drag and Drop (ohne Datenbank, Dienst im Speicher)");
        final com.dan.logistikapp.dienst.SpeicherDienst d = speicher(q);
        final KartenPanel p = new KartenPanel();
        final BufferedImage img = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                p.setSize(1400, 900);
                p.setModell(KartenModell.aus(q));
                p.allesZeigen();
                p.setContainerDienst(d);
                male(p, img);
            }
        });

        // 1) Eisen aus Hamburg nach Paris
        ContainerInfo eisen = finde(p, "Hamburg", "EISEN");
        ziehe(p, eisen, "Paris", img, png);
        warte(p, img);
        ContainerInfo nachher = nachId(p, eisen.getContainerId());
        check("Hamburg -> Paris: Container steht danach in Paris", "Paris".equals(nachher.getOrt())
                && "BEREIT".equals(nachher.getStatus()) && d.getAufrufe() == 1, nachher.getOrt() + ", " + nachher.getStatus());
        check("Meldung nennt Strecke, Kilometer und Zonen",
                p.getToastText() != null && p.getToastText().contains("Hamburg \u2192 Paris")
                && p.getToastText().contains("Inland \u2192 EU-Ausland"), p.getToastText());

        // 2) In die Nordsee fallen lassen: federt zurueck, kein Auftrag
        ContainerInfo kohle = finde(p, "Hamburg", "KOHLE");
        druecke(p, kohle);
        double[] see = com.dan.logistikapp.geo.Laea3035.vor(3.0, 56.0);
        java.awt.geom.Point2D.Double s = p.getAnsicht().zuBildschirm(see[0], see[1]);
        zieheNach(p, s.x, s.y);
        lasseLos(p);
        String modusNachLos = p.getZiehModus();
        warte(p, img);
        check("In die Nordsee losgelassen: federt zurueck, kein Auftrag",
                "RUECK".equals(modusNachLos) && d.getAufrufe() == 1
                && "Hamburg".equals(nachId(p, kohle.getContainerId()).getOrt()), modusNachLos);

        // 3) Esc bricht ab
        druecke(p, kohle);
        zieheNach(p, s.x, s.y);
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                p.getActionMap().get("abbrechen").actionPerformed(null);
            }
        });
        String nachEsc = p.getZiehModus();
        warte(p, img);
        check("Esc waehrend des Ziehens: zurueck an den Platz", "RUECK".equals(nachEsc) && d.getAufrufe() == 1, nachEsc);

        // 4) Koeln -> Oslo: Zoll
        ContainerInfo k = finde(p, "Köln", "KOHLE");
        ziehe(p, k, "Oslo", img, null);
        warte(p, img);
        ContainerInfo ko = nachId(p, k.getContainerId());
        check("Koeln -> Oslo: Container steht beim Zoll", "Oslo".equals(ko.getOrt()) && "ZOLL".equals(ko.getStatus())
                && p.getToastText().contains("beim Zoll"), ko.getStatus() + " / " + p.getToastText());
        int vorher = d.getAufrufe();
        druecke(p, ko);
        String m4 = p.getZiehModus();
        lasseLos(p);
        check("Container beim Zoll laesst sich nicht aufnehmen", "RUHE".equals(m4) && d.getAufrufe() == vorher
                && p.getToastText().contains("steht beim Zoll"), p.getToastText());
        final ContainerInfo koF = ko;
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                p.zollFreigeben(koF);
            }
        });
        warte(p, img);
        check("Freigabe ueber das Kontextmenue: wieder bereit",
                "BEREIT".equals(nachId(p, k.getContainerId()).getStatus()), p.getToastText());

        // 5) Datenbank lehnt ab: zurueck zum Start, Meldung
        ContainerInfo masch = finde(p, "Berlin", "MASCHINEN");
        d.scheitereBeimNaechsten();
        ziehe(p, masch, "Warschau", img, null);
        warte(p, img);
        check("Auftrag scheitert: Container bleibt in Berlin, Meldung sichtbar",
                "Berlin".equals(nachId(p, masch.getContainerId()).getOrt())
                && p.getToastText() != null && p.getToastText().startsWith("Datenbank:"), p.getToastText());
        check("Nach allem: alle Container da, keiner doppelt",
                p.getModell().getContainer().size() == q.container().size(), p.getModell().getContainer().size() + "");
    }

    // ------------------------------------------------------------ Zonenregeln

    private static double[][] route(DateiQuelle q, String von, String nach) {
        Ort a = null;
        Ort b = null;
        for (Ort o : q.orte()) {
            if (von.equals(o.getName())) {
                a = o;
            }
            if (nach.equals(o.getName())) {
                b = o;
            }
        }
        return com.dan.logistikapp.geo.Geodaesie.grosskreis(a.getLaenge(), a.getBreite(), b.getLaenge(), b.getBreite(), 64);
    }

    private static String marken(List<com.dan.logistikapp.karte.Grenzen.Uebergang> l) {
        StringBuilder sb = new StringBuilder();
        for (com.dan.logistikapp.karte.Grenzen.Uebergang u : l) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(u.getVon().getIso2()).append("\u2192").append(u.getNach().getIso2())
                    .append(u.isZoll() ? " Zoll" : "").append(u.isZonenwechsel() ? " Zone" : "")
                    .append(String.format(java.util.Locale.ROOT, " @%.2f", u.getAnteil()));
        }
        return sb.length() == 0 ? "keine" : sb.toString();
    }

    private static void zonenregeln(final DateiQuelle q, File png) throws Exception {
        say("");
        say("Zonenregeln: Grenzen, Zoll-Liste, Freigabe");
        KartenModell m = KartenModell.aus(q);

        // Grenzmarken auf den Grosskreisen - nur Zonenwechsel oder Zoll
        List<com.dan.logistikapp.karte.Grenzen.Uebergang> hp =
                com.dan.logistikapp.karte.Grenzen.finde(m, route(q, "Hamburg", "Paris"));
        check("Hamburg -> Paris: eine Marke, DE verlassen, Zonenwechsel ohne Zoll",
                hp.size() == 1 && "DE".equals(hp.get(0).getVon().getIso2()) && hp.get(0).isZonenwechsel()
                && !hp.get(0).isZoll(), marken(hp));
        List<com.dan.logistikapp.karte.Grenzen.Uebergang> ko =
                com.dan.logistikapp.karte.Grenzen.finde(m, route(q, "Köln", "Oslo"));
        com.dan.logistikapp.karte.Grenzen.Uebergang kl = ko.isEmpty() ? null : ko.get(ko.size() - 1);
        check("Koeln -> Oslo: letzte Marke ist die Zollgrenze nach NO",
                kl != null && "NO".equals(kl.getNach().getIso2()) && kl.isZoll() && !ko.get(0).isZoll(), marken(ko));
        List<com.dan.logistikapp.karte.Grenzen.Uebergang> pb =
                com.dan.logistikapp.karte.Grenzen.finde(m, route(q, "Paris", "Barcelona"));
        check("Paris -> Barcelona: innerhalb der EU keine Marke", pb.isEmpty(), marken(pb));
        List<com.dan.logistikapp.karte.Grenzen.Uebergang> mz =
                com.dan.logistikapp.karte.Grenzen.finde(m, route(q, "München", "Zürich"));
        com.dan.logistikapp.karte.Grenzen.Uebergang ml = mz.isEmpty() ? null : mz.get(mz.size() - 1);
        check("Muenchen -> Zuerich: Zollgrenze nach CH", ml != null && "CH".equals(ml.getNach().getIso2())
                && ml.isZoll(), marken(mz));
        boolean steigend = true;
        for (List<com.dan.logistikapp.karte.Grenzen.Uebergang> l : java.util.Arrays.asList(hp, ko, mz)) {
            double vor = -1;
            for (com.dan.logistikapp.karte.Grenzen.Uebergang u : l) {
                steigend &= u.getAnteil() > vor && u.getAnteil() > 0 && u.getAnteil() < 1;
                vor = u.getAnteil();
            }
        }
        check("Marken liegen auf der Strecke, in Fahrtrichtung", steigend, "");
        // die Marke liegt wirklich auf der Grenze: kurz davor noch im alten Land
        double[] vorher = Laea3035.vor(ml.getLaenge() + 0.02 * Math.signum(11.58 - 8.54),
                ml.getBreite() + 0.02 * Math.signum(48.14 - 47.37));
        KartenModell.LandForm lv = m.landBei(vorher[0], vorher[1]);
        check("Zollmarke sitzt auf der Grenzlinie (2 km davor noch nicht CH)",
                lv == null || !"CH".equals(lv.getLand().getIso2()),
                String.format(java.util.Locale.ROOT, "%.4f / %.4f", ml.getLaenge(), ml.getBreite()));

        // In der Karte: Fahrt Muenchen -> Zuerich laesst CH aufleuchten
        final com.dan.logistikapp.dienst.SpeicherDienst d = speicher(q);
        final KartenPanel p = new KartenPanel();
        final BufferedImage img = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                p.setSize(1400, 900);
                p.setModell(KartenModell.aus(q));
                p.allesZeigen();
                p.setContainerDienst(d);
                male(p, img);
            }
        });
        ContainerInfo mu = p.getModell().getContainer().get(0);
        for (ContainerInfo c : p.getModell().getContainer()) {
            if ("München".equals(c.getOrt())) {
                mu = c;
                break;
            }
        }
        ziehe(p, mu, "Zürich", img, null);
        warte(p, img);
        check("Fahrt Muenchen -> Zuerich: Grenze CH hat aufgeleuchtet",
                p.getGeblitzteLaender().contains("CH") && "ZOLL".equals(nachId(p, mu.getContainerId()).getStatus()),
                p.getGeblitzteLaender() + ", " + nachId(p, mu.getContainerId()).getStatus());

        // Zweiter Fall: Koeln -> Oslo
        Thread.sleep(1100);
        ContainerInfo k = finde(p, "Köln", "KOHLE");
        ziehe(p, k, "Oslo", img, null);
        warte(p, img);

        List<com.dan.logistikapp.model.ZollFall> f = d.zollFaelle();
        check("Zoll-Liste: zwei Faelle, der aeltere zuerst",
                f.size() == 2 && f.get(0).getContainerId() == mu.getContainerId()
                && f.get(1).getContainerId() == k.getContainerId(), f.size() + "");
        com.dan.logistikapp.model.ZollFall fk = f.size() == 2 ? f.get(1) : null;
        check("Fall Koeln -> Oslo: Grenze DE \u2192 NO, Zonen INLAND -> DRITTLAND",
                fk != null && "DE \u2192 NO".equals(fk.grenze()) && "Köln".equals(fk.getVonOrt())
                && "Oslo".equals(fk.getOrt()) && "INLAND".equals(fk.getVonZone())
                && "DRITTLAND".equals(fk.getNachZone()),
                fk == null ? "-" : fk.grenze() + " " + fk.getVonZone() + "->" + fk.getNachZone());
        check("Wartezeit zaehlt mit der eigenen Uhr weiter",
                fk != null && fk.wartetSek(System.currentTimeMillis() + 5000) >= fk.wartetSek(System.currentTimeMillis()) + 5,
                "");

        // Die Tafel: wie in LogistikApp verdrahtet
        final com.dan.logistikapp.ui.ZollTafel tafel = new com.dan.logistikapp.ui.ZollTafel();
        final List<String> gezeigt = new ArrayList<String>();
        tafel.setAktion(new com.dan.logistikapp.ui.ZollTafel.Aktion() {
            @Override
            public void freigeben(int id) {
                p.zollFreigeben(p.container(id));
            }

            @Override
            public void alleFreigeben(List<Integer> ids) {
                List<ContainerInfo> l = new ArrayList<ContainerInfo>();
                for (int id : ids) {
                    l.add(p.container(id));
                }
                p.zollFreigeben(l);
            }

            @Override
            public void zeigen(int id) {
                gezeigt.add(String.valueOf(id));
                p.zeigeContainer(id);
            }
        });
        final List<com.dan.logistikapp.model.ZollFall> ff = f;
        final double[] vorZoom = new double[1];
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                tafel.setFaelle(ff);
                tafel.setSize(tafel.getPreferredSize());
                vorZoom[0] = p.getAnsicht().getMassstab();
            }
        });
        check("Tafel waechst mit der Liste", tafel.getHeight() > 64 + 2 * 60, tafel.getHeight() + " px");

        // Klick auf die Zeile (nicht den Knopf): Karte holt den Container heran
        final java.awt.Rectangle kn1 = tafel.knopf(1);
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                tafel.klick(30, kn1.y);
            }
        });
        KartenModell.OrtPunkt oslo = p.getModell().punkt(nachId(p, k.getContainerId()).getOrtId());
        java.awt.geom.Point2D.Double os = p.getAnsicht().zuBildschirm(oslo.getX(), oslo.getY());
        check("Zeile anklicken: Karte zoomt auf Oslo",
                gezeigt.size() == 1 && p.getAnsicht().getMassstab() > vorZoom[0] * 1.5
                && Math.abs(os.x - 700) < 60 && os.y > 300 && os.y < 700,
                String.format(java.util.Locale.ROOT, "Oslo bei %.0f/%.0f", os.x, os.y));

        // Bild der Karte samt Tafel fuer das Werkbuch
        if (png != null) {
            final BufferedImage bild = new BufferedImage(1400 + tafel.getWidth(), 900, BufferedImage.TYPE_INT_RGB);
            javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    p.allesZeigen();
                    Graphics2D g = bild.createGraphics();
                    p.paint(g);
                    g.translate(1400, 0);
                    tafel.setSize(tafel.getWidth(), 900);
                    tafel.paint(g);
                    g.dispose();
                }
            });
            ImageIO.write(bild, "png", png);
            say("   Bild: " + png.getName());
        }

        // Freigeben-Knopf der ersten Zeile
        final java.awt.Rectangle kn0 = tafel.knopf(0);
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                tafel.klick((int) kn0.getCenterX(), (int) kn0.getCenterY());
            }
        });
        warte(p, img);
        List<com.dan.logistikapp.model.ZollFall> f2 = d.zollFaelle();
        check("Knopf 'Freigeben': Muenchen/Zuerich frei, Oslo wartet noch",
                f2.size() == 1 && f2.get(0).getContainerId() == k.getContainerId()
                && "BEREIT".equals(nachId(p, mu.getContainerId()).getStatus()), f2.size() + " / " + p.getToastText());

        // "Alle freigeben"
        final List<com.dan.logistikapp.model.ZollFall> f2f = f2;
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                tafel.setFaelle(f2f);
                java.awt.Rectangle a = tafel.alleKnopf();
                tafel.klick((int) a.getCenterX(), (int) a.getCenterY());
            }
        });
        warte(p, img);
        check("'Alle freigeben': Liste leer, alle Container bereit",
                d.zollFaelle().isEmpty() && "BEREIT".equals(nachId(p, k.getContainerId()).getStatus()),
                d.zollFaelle().size() + " / " + p.getToastText());
        final boolean[] leerKlick = new boolean[1];
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                tafel.setFaelle(null);
                int vor = gezeigt.size();
                java.awt.Rectangle a = tafel.alleKnopf();
                tafel.klick((int) a.getCenterX(), (int) a.getCenterY());
                tafel.klick(30, 64 + 20);
                leerKlick[0] = gezeigt.size() == vor;
            }
        });
        check("Leere Tafel: Klicks loesen nichts aus", leerKlick[0] && d.zollFaelle().isEmpty(), "");
    }

    // ------------------------------------------------------------ Politur

    /** Wartet, bis Karte und Seitenleiste nichts mehr zu tun haben. */
    private static void warteAlles(final KartenPanel p, final com.dan.logistikapp.ui.Leitstand l,
            final BufferedImage img) throws Exception {
        for (int runde = 0; runde < 3; runde++) {
            warte(p, img);
            long bis = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < bis) {
                final boolean[] ruhig = new boolean[1];
                javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        ruhig[0] = l.istRuhig() && p.istRuhig();
                    }
                });
                if (ruhig[0]) {
                    break;
                }
                Thread.sleep(30);
            }
        }
    }

    private static void edt(Runnable r) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(r);
    }

    /** Stand als Text: id@ort/status je Container, sortiert. */
    private static String stand(List<ContainerInfo> l) {
        List<String> t = new ArrayList<String>();
        for (ContainerInfo c : l) {
            t.add(c.getContainerId() + "@" + c.getOrtId() + "/" + c.getStatus());
        }
        java.util.Collections.sort(t);
        return t.toString();
    }

    /** Karte samt Seitenleiste in ein Bild. */
    private static void bildMitLeiste(final KartenPanel p, final com.dan.logistikapp.ui.Leitstand l, File png)
            throws Exception {
        final int lb = l.getSeitenLeiste().getPreferredSize().width;
        final BufferedImage bild = new BufferedImage(1400 + lb, 900, BufferedImage.TYPE_INT_RGB);
        edt(new Runnable() {
            @Override
            public void run() {
                javax.swing.JPanel s = l.getSeitenLeiste();
                s.setSize(lb, 900);
                s.doLayout();
                layoutTief(s);
                Graphics2D g = bild.createGraphics();
                p.paint(g);
                g.translate(1400, 0);
                s.paint(g);
                g.dispose();
            }
        });
        ImageIO.write(bild, "png", png);
        say("   Bild: " + png.getName());
    }

    private static void layoutTief(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof java.awt.Container) {
                layoutTief((java.awt.Container) k);
            }
        }
    }

    private static void politur(final DateiQuelle q, File base) throws Exception {
        say("");
        say("Politur: Bestand, Historie, Demo");

        // Bestand je Stadt - dieselbe Rechnung wie LOG_BESTAND_V
        List<com.dan.logistikapp.model.Bestand.Stadt> b =
                com.dan.logistikapp.model.Bestand.aus(q.orte(), q.waren(), q.container());
        int n = 0;
        int teu = 0;
        int teuSoll = 0;
        boolean sortiert = true;
        Zone vor = null;
        for (com.dan.logistikapp.model.Bestand.Stadt s : b) {
            n += s.getAnzahl();
            teu += s.getTeu();
            int summe = 0;
            for (com.dan.logistikapp.model.Bestand.Anteil a : s.getAnteile()) {
                summe += a.getAnzahl();
            }
            sortiert &= summe == s.getAnzahl();
            if (vor != null && s.getOrt().getZone().compareTo(vor) < 0) {
                sortiert = false;
            }
            vor = s.getOrt().getZone();
        }
        for (ContainerInfo c : q.container()) {
            teuSoll += c.getTeu();
        }
        check("Bestand: alle " + q.orte().size() + " Staedte, " + q.container().size() + " Container, TEU stimmen",
                b.size() == q.orte().size() && n == q.container().size() && teu == teuSoll,
                b.size() + " Staedte, " + n + " Container, " + teu + " TEU");
        check("Bestand nach Zone geordnet, Anteile summieren sich zur Stadt", sortiert
                && b.get(0).getOrt().getZone() == Zone.INLAND, b.get(0).getOrt().getName());

        // Karte + Leitstand wie in der App, Dienst im Speicher
        final com.dan.logistikapp.dienst.SpeicherDienst d = speicher(q);
        final KartenPanel p = new KartenPanel();
        final com.dan.logistikapp.ui.Leitstand l = new com.dan.logistikapp.ui.Leitstand(p, 150);
        final BufferedImage img = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
        edt(new Runnable() {
            @Override
            public void run() {
                p.setSize(1400, 900);
                p.setModell(KartenModell.aus(q));
                p.allesZeigen();
                p.setContainerDienst(d);
                l.neuLaden();
                l.getBestandTafel().setSize(290, l.getBestandTafel().getPreferredSize().height);
                l.getFahrtenTafel().setSize(290, 700);
            }
        });
        warteAlles(p, l, img);
        final String vorher = stand(d.container());
        final double m0 = p.getAnsicht().getMassstab();
        final int yOslo = l.getBestandTafel().zeileMitte("Oslo");
        edt(new Runnable() {
            @Override
            public void run() {
                l.getBestandTafel().klick(100, yOslo);
            }
        });
        KartenModell.OrtPunkt oslo = null;
        for (KartenModell.OrtPunkt o : p.getModell().getPunkte()) {
            if ("Oslo".equals(o.getOrt().getName())) {
                oslo = o;
            }
        }
        java.awt.geom.Point2D.Double os = p.getAnsicht().zuBildschirm(oslo.getX(), oslo.getY());
        check("Bestandsliste: Klick auf Oslo holt Oslo in die Bildmitte",
                yOslo > 0 && p.getAnsicht().getMassstab() > m0 * 1.5 && Math.abs(os.x - 700) < 40,
                String.format(java.util.Locale.ROOT, "Zeile y=%d, Oslo bei %.0f/%.0f", yOslo, os.x, os.y));
        edt(new Runnable() {
            @Override
            public void run() {
                p.allesZeigen();
            }
        });

        // Koeln -> Oslo, freigeben, Oslo -> London
        ContainerInfo k = finde(p, "Köln", "KOHLE");
        ziehe(p, k, "Oslo", img, null);
        warteAlles(p, l, img);
        com.dan.logistikapp.model.Bestand.Stadt bo = null;
        for (com.dan.logistikapp.model.Bestand.Stadt s : l.getBestandTafel().getStand()) {
            if ("Oslo".equals(s.getOrt().getName())) {
                bo = s;
            }
        }
        check("Nach Koeln -> Oslo: Bestand Oslo zaehlt ihn beim Zoll, Reiter zeigt 1",
                bo != null && bo.getBeimZoll() == 1 && l.getSeitenLeiste().getZollAnzahl() == 1,
                bo == null ? "-" : bo.getAnzahl() + " in Oslo, " + bo.getBeimZoll() + " beim Zoll");
        check("Fahrtenliste: die Fahrt steht oben",
                !l.getFahrtenTafel().getFahrten().isEmpty()
                && "Oslo".equals(l.getFahrtenTafel().getFahrten().get(0).getNachOrt())
                && l.getFahrtenTafel().getFahrten().get(0).isZoll(),
                l.getFahrtenTafel().getFahrten().size() + " Fahrten");
        edt(new Runnable() {
            @Override
            public void run() {
                java.awt.Rectangle r = l.getZollTafel().knopf(0);
                l.getZollTafel().klick((int) r.getCenterX(), (int) r.getCenterY());
            }
        });
        warteAlles(p, l, img);
        ziehe(p, nachId(p, k.getContainerId()), "London", img, null);
        warteAlles(p, l, img);
        final int kid = k.getContainerId();
        edt(new Runnable() {
            @Override
            public void run() {
                p.zeigeHistorie(kid);
            }
        });
        warteAlles(p, l, img);
        List<com.dan.logistikapp.model.Bewegung> sp = p.getSpur();
        boolean kette = sp.size() == 2;
        for (int i = 1; i < sp.size(); i++) {
            kette &= sp.get(i - 1).getNachOrtId() == sp.get(i).getVonOrtId();
        }
        check("Historie: Spur Koeln -> Oslo -> London, lueckenlos, aelteste zuerst",
                kette && "Köln".equals(sp.get(0).getVonOrt()) && "London".equals(sp.get(1).getNachOrt()),
                sp.toString());
        check("Historie oeffnet den Reiter Fahrten mit derselben Nummerierung",
                l.getSeitenLeiste().getAktiv() == com.dan.logistikapp.ui.SeitenLeiste.FAHRTEN
                && l.getFahrtenTafel().istHistorie() && l.getFahrtenTafel().getFahrten().equals(sp)
                && p.getToastText() != null && p.getToastText().contains("2 Fahrten"),
                "Reiter " + l.getSeitenLeiste().getAktiv() + ", Historie " + l.getFahrtenTafel().istHistorie()
                + ", Liste " + l.getFahrtenTafel().getFahrten());
        bildMitLeiste(p, l, new File(base, "historie_vorschau.png"));

        edt(new Runnable() {
            @Override
            public void run() {
                l.getFahrtenTafel().klick(100, l.getFahrtenTafel().zeileMitte(0));
            }
        });
        warteAlles(p, l, img);
        check("Klick auf Fahrt 1 der Historie: Spur bleibt ganz, Karte zeigt Oslo",
                p.getSpur().size() == 2 && p.getAnsicht().getMassstab() > m0 * 1.5,
                p.getSpur() + " / " + l.getFahrtenTafel().istHistorie() + " " + l.getFahrtenTafel().getFahrten());
        edt(new Runnable() {
            @Override
            public void run() {
                java.awt.Rectangle r = l.getFahrtenTafel().ausKnopf();
                l.getFahrtenTafel().klick((int) r.getCenterX(), (int) r.getCenterY());
            }
        });
        warteAlles(p, l, img);
        check("'alle Fahrten': zurueck zur Liste, Spur weg",
                !l.getFahrtenTafel().istHistorie() && p.getSpur().isEmpty()
                && l.getFahrtenTafel().getFahrten().size() == 2, l.getFahrtenTafel().getFahrten().size() + "");
        edt(new Runnable() {
            @Override
            public void run() {
                l.getFahrtenTafel().klick(100, l.getFahrtenTafel().zeileMitte(0));
            }
        });
        check("Klick auf eine Fahrt der Liste: genau diese als Spur",
                p.getSpur().size() == 1 && "London".equals(p.getSpur().get(0).getNachOrt()), p.getSpur().toString());
        edt(new Runnable() {
            @Override
            public void run() {
                p.zeigeHistorie(kid);
            }
        });
        warteAlles(p, l, img);
        edt(new Runnable() {
            @Override
            public void run() {
                p.getActionMap().get("abbrechen").actionPerformed(null);
            }
        });
        warteAlles(p, l, img);
        check("Esc blendet die Spur aus, die Liste verlaesst die Historie",
                p.getSpur().isEmpty() && !l.getFahrtenTafel().istHistorie(), "");
        edt(new Runnable() {
            @Override
            public void run() {
                l.getSeitenLeiste().klickReiter(com.dan.logistikapp.ui.SeitenLeiste.BESTAND);
            }
        });
        check("Reiter umschalten", l.getSeitenLeiste().getAktiv() == com.dan.logistikapp.ui.SeitenLeiste.BESTAND, "");

        // Demo: spielt auf einer Kopie, der echte Dienst bleibt unberuehrt
        final String standVorDemo = stand(d.container());
        final int aufrufeVorDemo = d.getAufrufe();
        final com.dan.logistikapp.ui.DemoSzene demo = l.getDemo();
        edt(new Runnable() {
            @Override
            public void run() {
                l.getSeitenLeiste().klickDemo();
            }
        });
        boolean bildGemacht = false;
        boolean spielHatteSpur = false;
        boolean sahPlan = false;
        boolean reiste = false;
        boolean bildReise = false;
        int maxSchritt = 0;
        long bis = System.currentTimeMillis() + 180000;
        while (System.currentTimeMillis() < bis) {
            final boolean[] laeuft = new boolean[1];
            final String[] text = new String[1];
            final int[] spur = new int[1];
            final boolean[] r2 = new boolean[3];
            edt(new Runnable() {
                @Override
                public void run() {
                    male(p, img);
                    laeuft[0] = demo.laeuft();
                    text[0] = p.getDemoText();
                    spur[0] = p.getSpur().size();
                    r2[0] = p.getPlanStrecken() > 0;
                    r2[1] = p.istZeitreise();
                    r2[2] = p.getUnterwegsAnzahl() > 0;
                }
            });
            if (!laeuft[0]) {
                break;
            }
            spielHatteSpur |= spur[0] >= 3;
            sahPlan |= r2[0];
            reiste |= r2[1];
            if (text[0] != null && text[0].indexOf('/') > 0) {
                try {
                    maxSchritt = Math.max(maxSchritt, Integer.parseInt(text[0].substring(0, text[0].indexOf('/'))));
                } catch (NumberFormatException x) {
                    // Text ohne Schrittnummer
                }
            }
            if (!bildReise && r2[1] && r2[2]) {
                bildMitZeit(p, l, new File(base, "demo_zeitreise.png"));
                bildReise = true;
            }
            if (!bildGemacht && text[0] != null && text[0].startsWith("5/") && !p.istRuhig()) {
                Thread.sleep(700);
                edt(new Runnable() {
                    @Override
                    public void run() {
                        l.getSeitenLeiste().klickReiter(com.dan.logistikapp.ui.SeitenLeiste.ZOLL);
                    }
                });
                final int[] zollListe = new int[1];
                edt(new Runnable() {
                    @Override
                    public void run() {
                        zollListe[0] = l.getZollTafel().getFaelle().size();
                    }
                });
                bildMitLeiste(p, l, new File(base, "demo_vorschau.png"));
                bildGemacht = true;
                check("Demo: wer vorher beim Zoll stand, steht auch in der Zoll-Liste der Demo",
                        zollListe[0] >= 1, zollListe[0] + " in der Liste");
            }
            Thread.sleep(60);
        }
        warteAlles(p, l, img);
        com.dan.logistikapp.dienst.SpeicherDienst spiel = demo.getSpielDienst();
        check("Demo lief durch: 7 Fahrten + 1 Auftrag (+1 uebernommener Zollfall), Historie mit 3 Fahrten als Spur",
                !demo.laeuft() && spiel != null && spiel.getAufrufe() == 8 && spielHatteSpur
                && spiel.letzteBewegungen(99).size() == 9,
                spiel == null ? "-" : spiel.getAufrufe() + " Fahrten im Spiel, "
                + spiel.letzteBewegungen(99).size() + " Bewegungen");
        boolean auftragErledigt = false;
        if (spiel != null) {
            for (com.dan.logistikapp.model.Auftrag a : spiel.auftraege()) {
                auftragErledigt |= a.getStatus() == com.dan.logistikapp.model.Auftrag.Status.ERLEDIGT
                        && "APP".equals(a.getAusgefuehrtVon());
            }
        }
        check("Demo Runde 2: alle " + demo.getSchritte() + " Schritte, Auftrag mit Countdown geplant und von der "
                + "Karte gefahren, zum Schluss Zeitreise mit fahrenden Containern",
                demo.getSchritte() == 16 && maxSchritt == 16 && sahPlan && auftragErledigt && reiste && bildReise,
                maxSchritt + "/" + demo.getSchritte() + ", Plan " + sahPlan + ", Auftrag " + auftragErledigt
                + ", Zeitreise " + reiste + "/" + bildReise);
        check("Nach der Demo: Zeitleiste frei, live, Zeitraffer wieder x60",
                !l.getZeitleiste().isGesperrt() && l.getZeitleiste().getZeit() == null && !p.istZeitreise()
                && l.getZeitleiste().getTempo() == 60, "gesperrt " + l.getZeitleiste().isGesperrt() + ", Tempo "
                + l.getZeitleiste().getTempo());
        check("Nach der Demo: echter Dienst unberuehrt, Stand wie vorher, Band weg",
                d.getAufrufe() == aufrufeVorDemo && stand(d.container()).equals(standVorDemo)
                && stand(p.getModell().getContainer()).equals(standVorDemo) && p.getContainerDienst() == d
                && p.getDemoText() == null && "Demo abspielen".equals(l.getSeitenLeiste().getDemoText()),
                p.getToastText());
        check("Waehrend der Demo zeigten die Listen den Spielstand, danach wieder den echten",
                l.getFahrtenTafel().getFahrten().size() == 2 && l.getSeitenLeiste().getZollAnzahl() == 1,
                l.getFahrtenTafel().getFahrten().size() + " Fahrten, " + l.getSeitenLeiste().getZollAnzahl() + " Zoll");

        // Demo abbrechen
        edt(new Runnable() {
            @Override
            public void run() {
                l.getSeitenLeiste().klickDemo();
            }
        });
        bis = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < bis) {
            final String[] text = new String[1];
            edt(new Runnable() {
                @Override
                public void run() {
                    male(p, img);
                    text[0] = p.getDemoText();
                }
            });
            if (text[0] != null && text[0].startsWith("2/")) {
                break;
            }
            Thread.sleep(40);
        }
        edt(new Runnable() {
            @Override
            public void run() {
                l.getSeitenLeiste().klickDemo();
            }
        });
        bis = System.currentTimeMillis() + 30000;
        while (demo.laeuft() && System.currentTimeMillis() < bis) {
            edt(new Runnable() {
                @Override
                public void run() {
                    male(p, img);
                }
            });
            Thread.sleep(40);
        }
        warteAlles(p, l, img);
        check("Demo abbrechen: endet nach dem laufenden Schritt, Stand wie vorher",
                !demo.laeuft() && demo.getSpielDienst().getAufrufe() <= 2
                && stand(p.getModell().getContainer()).equals(standVorDemo)
                && p.getToastText() != null && p.getToastText().startsWith("Demo abgebrochen"),
                demo.getSpielDienst().getAufrufe() + " Fahrten, " + p.getToastText());
        check("Echter Dienst hat vom Ganzen nichts gemerkt", d.getAufrufe() == aufrufeVorDemo
                && !vorher.equals(standVorDemo), d.getAufrufe() + " Aufrufe");
    }

    // ------------------------------------------------------------ Kapazitaet und Kosten (Runde 2)

    /** Dieselbe Quelle, nur eine Stadt mit anderer Kapazitaet. */
    private static KartenQuelle mitKapazitaet(final DateiQuelle q, final String stadt, final int kap) {
        final List<Ort> orte = new ArrayList<Ort>();
        for (Ort o : q.orte()) {
            orte.add(o.getName().equals(stadt) ? new Ort(o.getOrtId(), o.getName(), o.getIso2(), o.getLand(),
                    o.getZone(), o.getLaenge(), o.getBreite(), kap) : o);
        }
        return new KartenQuelle() {
            @Override
            public List<Land> laender() {
                return q.laender();
            }

            @Override
            public List<Ort> orte() {
                return orte;
            }

            @Override
            public List<Ware> waren() {
                return q.waren();
            }

            @Override
            public List<ContainerInfo> container() {
                return q.container();
            }

            @Override
            public com.dan.logistikapp.model.Tarif tarif() {
                return q.tarif();
            }
        };
    }

    private static void kapazitaetKosten(final DateiQuelle q, File base) throws Exception {
        say("");
        say("Kapazitaet und Kosten (Runde 2, Zahlen aus 10_daten2.sql)");
        com.dan.logistikapp.model.Tarif t = q.tarif();
        int mitKap = 0;
        Integer hamburgKap = null;
        for (Ort o : q.orte()) {
            mitKap += o.getKapazitaetTeu() != null ? 1 : 0;
            hamburgKap = "Hamburg".equals(o.getName()) ? o.getKapazitaetTeu() : hamburgKap;
        }
        check("Aus 10_daten2.sql gelesen: 10 Kapazitaeten (Hamburg 16), 6 Frachtsaetze, 3 Tarife",
                mitKap == 10 && Integer.valueOf(16).equals(hamburgKap) && t.frachtSatz("KUEHLWARE") != null
                && t.frachtSatz("KUEHLWARE").compareTo(new java.math.BigDecimal("1.90")) == 0
                && t.wert(com.dan.logistikapp.model.Tarif.ZOLL_PAUSCHALE).intValue() == 85 && t.jobKarenzMin() == 2,
                mitKap + " Kapazitaeten, Hamburg " + hamburgKap);
        // Der Wert aus dem echten Durchstich (Abschnitt N): Hamburg -> Oslo, Kohle, 1 TEU, 710 034 m
        check("Java-Tarif trifft den Betrag aus LOG_API: 710 034 m Kohle = 781,04 EUR, mit Zoll 866,04 EUR",
                t.fracht(710034, 1, "KOHLE").compareTo(new java.math.BigDecimal("781.04")) == 0
                && t.vorschau(710034, 1, "KOHLE", true).compareTo(new java.math.BigDecimal("866.04")) == 0
                && t.standgeld(1860000L).compareTo(new java.math.BigDecimal("9")) == 0,
                t.fracht(710034, 1, "KOHLE") + " / " + t.vorschau(710034, 1, "KOHLE", true));

        KartenModell m0 = KartenModell.aus(q);
        int hamburg = -1;
        int paris = -1;
        for (KartenModell.OrtPunkt p : m0.getPunkte()) {
            hamburg = "Hamburg".equals(p.getOrt().getName()) ? p.getOrt().getOrtId() : hamburg;
            paris = "Paris".equals(p.getOrt().getName()) ? p.getOrt().getOrtId() : paris;
        }
        check("Kartenmodell: Hamburg belegt 5 von 16 TEU, frei 11", m0.belegtTeu(hamburg) == 5
                && Integer.valueOf(11).equals(m0.freiTeu(hamburg)), m0.belegtTeu(hamburg) + " / frei " + m0.freiTeu(hamburg));

        // Paris voll: Kapazitaet = heutiger Bestand
        final KartenQuelle qv = mitKapazitaet(q, "Paris", m0.belegtTeu(paris));
        final com.dan.logistikapp.dienst.SpeicherDienst d =
                new com.dan.logistikapp.dienst.SpeicherDienst(qv.orte(), qv.laender(), qv.container())
                        .setVerzoegerungMs(200).setTarif(t);
        final KartenPanel p = new KartenPanel();
        final com.dan.logistikapp.ui.Leitstand l = new com.dan.logistikapp.ui.Leitstand(p, 150);
        final BufferedImage img = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
        edt(new Runnable() {
            @Override
            public void run() {
                p.setSize(1400, 900);
                p.setModell(KartenModell.aus(qv));
                p.allesZeigen();
                p.setContainerDienst(d);
                l.neuLaden();
            }
        });
        warteAlles(p, l, img);

        ContainerInfo kohle = finde(p, "Hamburg", "KOHLE");
        druecke(p, kohle);
        KartenModell.OrtPunkt pz = p.getModell().punkt(paris);
        java.awt.geom.Point2D.Double ps = p.getAnsicht().zuBildschirm(pz.getX(), pz.getY());
        zieheNach(p, ps.x + 12, ps.y + 8);
        edt(new Runnable() {
            @Override
            public void run() {
                male(p, img);
            }
        });
        ImageIO.write(img, "png", new File(base, "voll_vorschau.png"));
        say("   Bild: voll_vorschau.png (waehrend des Ziehens)");
        lasseLos(p);
        String modus = p.getZiehModus();
        warteAlles(p, l, img);
        check("Paris voll: der Container federt zurueck, kein Auftrag, Meldung nennt die Stadt",
                "RUECK".equals(modus) && d.getAufrufe() == 0 && p.getToastText() != null
                && p.getToastText().startsWith("Paris ist voll"), modus + " / " + p.getToastText());
        com.dan.logistikapp.dienst.DienstFehler df = null;
        try {
            d.verschieben(kohle.getContainerId(), paris);
        } catch (com.dan.logistikapp.dienst.DienstFehler e) {
            df = e;
        }
        check("Dienst im Speicher weist ebenfalls ab (ZIEL_VOLL, Text wie LOG_API)",
                df != null && df.getArt() == com.dan.logistikapp.dienst.DienstFehler.Art.ZIEL_VOLL
                && df.getMessage().contains("TEU belegt"), df == null ? "kein Fehler" : df.getMessage());

        // Hamburg -> Oslo: Zoll, Kosten in der Meldung
        ziehe(p, kohle, "Oslo", img, null);
        warteAlles(p, l, img);
        java.math.BigDecimal erwartet = null;
        List<com.dan.logistikapp.model.Bewegung> hist = d.historie(kohle.getContainerId());
        com.dan.logistikapp.model.Bewegung b0 = hist.isEmpty() ? null : hist.get(hist.size() - 1);
        if (b0 != null) {
            erwartet = t.vorschau(b0.getDistanzM(), kohle.getTeu(), "KOHLE", true);
        }
        String euro = erwartet == null ? "?" : String.format(java.util.Locale.GERMANY, "%,.2f €", erwartet);
        check("Hamburg -> Oslo: Meldung nennt die Kosten (Fracht + Zoll)",
                p.getToastText() != null && p.getToastText().contains(euro) && p.getToastText().contains("beim Zoll"),
                p.getToastText());
        final java.math.BigDecimal[] lauf = new java.math.BigDecimal[1];
        edt(new Runnable() {
            @Override
            public void run() {
                lauf[0] = l.getZollTafel().standgeldJetzt(System.currentTimeMillis());
            }
        });
        check("Zoll-Liste: Standgeld laeuft (erste Viertelstunde 3 EUR)",
                lauf[0] != null && lauf[0].compareTo(new java.math.BigDecimal("3")) == 0, lauf[0] + " EUR");
        final ContainerInfo ko = nachId(p, kohle.getContainerId());
        edt(new Runnable() {
            @Override
            public void run() {
                p.zollFreigeben(ko);
            }
        });
        warteAlles(p, l, img);
        com.dan.logistikapp.model.Bewegung b1 = d.historie(kohle.getContainerId()).get(0);
        check("Freigabe schreibt Standgeld an die Fahrt; die Fahrtenliste traegt Kosten",
                b1.getStandgeldEur() != null && b1.getStandgeldEur().compareTo(new java.math.BigDecimal("3")) == 0
                && b1.getFreigegebenAmMs() > 0 && !l.getFahrtenTafel().getFahrten().isEmpty()
                && l.getFahrtenTafel().getFahrten().get(0).getKostenEur().compareTo(erwartet.add(b1.getStandgeldEur())) == 0,
                b1.getKostenEur() + " EUR");

        // Kosten-Reiter
        ContainerInfo w = finde(p, "Berlin", "MASCHINEN");
        ziehe(p, w, "München", img, null);
        warteAlles(p, l, img);
        final com.dan.logistikapp.ui.KostenTafel kt = l.getKostenTafel();
        java.math.BigDecimal summe = java.math.BigDecimal.ZERO;
        for (com.dan.logistikapp.model.Bewegung b : d.letzteBewegungen(99)) {
            summe = summe.add(b.getKostenEur());
        }
        List<com.dan.logistikapp.ui.KostenTafel.Posten> an = kt.anteile();
        List<com.dan.logistikapp.ui.KostenTafel.Posten> zonen = kt.jeZone();
        check("Kosten-Reiter: Summe = alle Fahrten, Zoll 85 EUR, Standgeld 3 EUR, zwei Zonenpaare",
                kt.getSumme().compareTo(summe) == 0 && an.get(1).getEur().compareTo(new java.math.BigDecimal("85")) == 0
                && an.get(2).getEur().compareTo(new java.math.BigDecimal("3")) == 0 && zonen.size() == 2
                && kt.jeWare().size() == 2, kt.getSumme() + " EUR, " + zonen.size() + " Zonenpaare");
        final java.awt.Rectangle kh = kt.knopfHeute();
        edt(new Runnable() {
            @Override
            public void run() {
                kt.klick((int) kh.getCenterX(), (int) kh.getCenterY());
            }
        });
        check("Umschalten auf 'Heute': alles von heute, gleiche Summe", kt.isNurHeute()
                && kt.getSumme().compareTo(summe) == 0, kt.getSumme().toString());
        final boolean[] tafel = new boolean[1];
        edt(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                for (com.dan.logistikapp.model.Bestand.Stadt st : l.getBestandTafel().getStand()) {
                    ok |= "Hamburg".equals(st.getOrt().getName()) && Integer.valueOf(16).equals(st.getOrt().getKapazitaetTeu());
                }
                tafel[0] = ok;
                l.getSeitenLeiste().klickReiter(com.dan.logistikapp.ui.SeitenLeiste.KOSTEN);
            }
        });
        check("Bestand kennt die Kapazitaet, Reiter Kosten laesst sich waehlen",
                tafel[0] && l.getSeitenLeiste().getAktiv() == com.dan.logistikapp.ui.SeitenLeiste.KOSTEN, "");
        bildMitLeiste(p, l, new File(base, "kosten_vorschau.png"));
    }

    // ------------------------------------------------------------ Auftraege (Runde 2)

    private static int ortIdVon(DateiQuelle q, String name) {
        for (Ort o : q.orte()) {
            if (o.getName().equals(name)) {
                return o.getOrtId();
            }
        }
        return -1;
    }

    private static com.dan.logistikapp.model.Auftrag auftragNr(com.dan.logistikapp.dienst.SpeicherDienst d, long id) {
        for (com.dan.logistikapp.model.Auftrag a : d.auftraege()) {
            if (a.getAuftragId() == id) {
                return a;
            }
        }
        return null;
    }

    // ================================================================ Zeitreise (Runde 2)

    /** Karte, Zeitleiste darunter und Seitenleiste rechts in ein Bild. */
    private static void bildMitZeit(final KartenPanel p, final com.dan.logistikapp.ui.Leitstand l, File png)
            throws Exception {
        final int lb = l.getSeitenLeiste().getPreferredSize().width;
        final int zh = com.dan.logistikapp.ui.Zeitleiste.HOEHE;
        final BufferedImage bild = new BufferedImage(1400 + lb, 900, BufferedImage.TYPE_INT_RGB);
        edt(new Runnable() {
            @Override
            public void run() {
                javax.swing.JPanel s = l.getSeitenLeiste();
                s.setSize(lb, 900);
                layoutTief(s);
                com.dan.logistikapp.ui.Zeitleiste z = l.getZeitleiste();
                z.setSize(1400, zh);
                // wie im Fenster: die Karte endet ueber der Zeitleiste
                java.awt.Dimension alt = p.getSize();
                p.setSize(1400, 900 - zh);
                Graphics2D g = bild.createGraphics();
                p.paint(g);
                p.setSize(alt);
                g.translate(0, 900 - zh);
                z.paint(g);
                g.translate(1400, -(900 - zh));
                s.paint(g);
                g.dispose();
            }
        });
        ImageIO.write(bild, "png", png);
        say("   Bild: " + png.getName());
    }

    private static int ersterIn(com.dan.logistikapp.dienst.SpeicherDienst s, int ort, int ohne) {
        for (ContainerInfo c : s.container()) {
            if (c.getOrtId() == ort && c.getGroesseFuss() == 20 && c.getContainerId() != ohne
                    && "BEREIT".equals(c.getStatus())) {
                return c.getContainerId();
            }
        }
        throw new IllegalStateException("kein Container an Ort " + ort);
    }

    private static ContainerInfo in(List<ContainerInfo> l, int id) {
        for (ContainerInfo c : l) {
            if (c.getContainerId() == id) {
                return c;
            }
        }
        return null;
    }

    private static java.math.BigDecimal summe(List<com.dan.logistikapp.model.KostenZeile> l) {
        java.math.BigDecimal s = java.math.BigDecimal.ZERO;
        for (com.dan.logistikapp.model.KostenZeile z : l) {
            s = s.add(z.getKostenEur());
        }
        return s;
    }

    private static void zeitreise(final DateiQuelle q, File base) throws Exception {
        say("");
        say("Zeitreise (Runde 2): Stand von damals, Prognose, Zeitleiste, Nur-Ansicht");
        final int hamburg = ortIdVon(q, "Hamburg");
        final int koeln = ortIdVon(q, "Köln");
        final int paris = ortIdVon(q, "Paris");
        final int oslo = ortIdVon(q, "Oslo");
        final int zuerich = ortIdVon(q, "Zürich");
        final int barcelona = ortIdVon(q, "Barcelona");
        final int berlin = ortIdVon(q, "Berlin");
        final int muenchen = ortIdVon(q, "München");
        final long jetzt = System.currentTimeMillis();
        final long t0 = jetzt - 5 * 3600000L;
        final long min = 60000L;
        final long[] uhr = {t0};

        // Eine Historie ueber Stunden: die Uhr des Dienstes wird gestellt
        final com.dan.logistikapp.dienst.SpeicherDienst d = speicher(q).setVerzoegerungMs(0)
                .setUhr(new com.dan.logistikapp.dienst.SpeicherDienst.Uhr() {
                    @Override
                    public long jetzt() {
                        return uhr[0];
                    }
                });
        final List<ContainerInfo> anfangsStand = d.container();
        final int a = ersterIn(d, hamburg, -1);
        final int b = ersterIn(d, hamburg, a);
        final int c = ersterIn(d, muenchen, -1);
        d.verschieben(a, koeln);
        uhr[0] = t0 + 10 * min;
        d.verschieben(a, paris);
        uhr[0] = t0 + 20 * min;
        d.verschieben(b, oslo);
        uhr[0] = t0 + 35 * min;
        d.zollFreigeben(b);
        uhr[0] = t0 + 180 * min;
        d.verschieben(c, zuerich);
        uhr[0] = t0 + 185 * min;
        d.verschieben(a, barcelona);
        uhr[0] = jetzt;
        long auftrag = d.auftragAnlegen(a, berlin, jetzt + 10 * min);

        com.dan.logistikapp.model.Zeitreise z = new com.dan.logistikapp.model.Zeitreise(d.container(),
                d.alleBewegungen(), d.auftraege(), q.orte(), jetzt);
        check("Historie endet ueberall am heutigen Stand; rekonstruiert um jetzt = heutiger Stand",
                z.abweichungen().isEmpty() && stand(z.standUm(jetzt)).equals(stand(d.container())),
                z.abweichungen().isEmpty() ? d.alleBewegungen().size() + " Fahrten" : z.abweichungen().toString());
        check("Vor der ersten Fahrt: genau der Anfangsstand", stand(z.standUm(t0 - 1)).equals(stand(anfangsStand)),
                null);
        List<ContainerInfo> s15 = z.standUm(t0 + 15 * min);
        List<ContainerInfo> s25 = z.standUm(t0 + 25 * min);
        List<ContainerInfo> s40 = z.standUm(t0 + 40 * min);
        List<ContainerInfo> s181 = z.standUm(t0 + 181 * min);
        check("Zwischendurch: Paris nach 15 min, Oslo beim Zoll nach 25 min, frei nach 40 min, Zuerich beim Zoll",
                in(s15, a).getOrtId() == paris && in(s15, b).getOrtId() == hamburg
                && in(s25, b).getOrtId() == oslo && "ZOLL".equals(in(s25, b).getStatus())
                && "BEREIT".equals(in(s40, b).getStatus()) && in(s181, c).getOrtId() == zuerich
                && "ZOLL".equals(in(s181, c).getStatus()) && in(s181, a).getOrtId() == paris,
                in(s15, a).getOrt() + ", " + in(s25, b).getOrt() + "/" + in(s25, b).getStatus() + ", "
                + in(s40, b).getStatus() + ", " + in(s181, c).getOrt() + "/" + in(s181, c).getStatus());

        Map<Integer, String[]> ware = new HashMap<Integer, String[]>();
        for (ContainerInfo x : d.container()) {
            ware.put(x.getContainerId(), new String[] {x.getWareCode(), x.getWare()});
        }
        java.math.BigDecimal k25 = summe(com.dan.logistikapp.model.KostenZeile.aus(z.fahrtenBis(t0 + 25 * min), ware));
        java.math.BigDecimal k40 = summe(com.dan.logistikapp.model.KostenZeile.aus(z.fahrtenBis(t0 + 40 * min), ware));
        java.math.BigDecimal sg = d.historie(b).get(0).getStandgeldEur();
        java.math.BigDecimal k25soll = java.math.BigDecimal.ZERO;
        for (com.dan.logistikapp.model.Bewegung x : d.alleBewegungen()) {
            if (x.getZeitpunktMs() <= t0 + 25 * min) {
                k25soll = k25soll.add(x.getFrachtEur()).add(x.getZollEur());
            }
        }
        check("Kosten um t: nur Fahrten bis t, Standgeld erst ab der Freigabe (15 min = 1 Viertelstunde)",
                k25.compareTo(k25soll) == 0 && sg != null && sg.compareTo(new java.math.BigDecimal("3.00")) == 0
                && k40.subtract(k25).compareTo(sg) == 0, k25 + " EUR -> " + k40 + " EUR, Standgeld " + sg);

        Map<Integer, com.dan.logistikapp.model.Land> land = new HashMap<Integer, com.dan.logistikapp.model.Land>();
        for (Ort o : q.orte()) {
            for (com.dan.logistikapp.model.Land l0 : q.laender()) {
                if (l0 != null && l0.getIso2().equals(o.getIso2())) {
                    land.put(o.getOrtId(), l0);
                }
            }
        }
        List<com.dan.logistikapp.model.ZollFall> zf25 = z.zollUm(t0 + 25 * min, land);
        check("Zoll-Liste um t: wer damals wartete, seit wann (5 min), Grenze DE -> NO; danach leer",
                zf25.size() == 1 && zf25.get(0).getContainerId() == b && zf25.get(0).wartetSek(t0 + 25 * min) == 300
                && "DE → NO".equals(zf25.get(0).grenze()) && z.zollUm(t0 + 40 * min, land).isEmpty(),
                zf25.size() + " Fall, " + (zf25.isEmpty() ? "-" : zf25.get(0).wartetSek(t0 + 25 * min) + " s "
                + zf25.get(0).grenze()));

        List<ContainerInfo> p15 = z.standUm(jetzt + 15 * min);
        check("Prognose: in 15 min steht a in Berlin (Auftrag #" + auftrag + "), vorher ist er noch offen",
                in(p15, a).getOrtId() == berlin && z.prognostiziert(jetzt + 15 * min).contains(a)
                && z.offenNach(jetzt + 15 * min).isEmpty() && z.offenNach(jetzt + 5 * min).size() == 1
                && in(z.standUm(jetzt + 5 * min), a).getOrtId() == barcelona, in(p15, a).getOrt());

        List<com.dan.logistikapp.model.Zeitreise.Unterwegs> u = z.unterwegs(t0 + 10 * min + 300, 1000);
        check("Wiedergabe: 0,3 s nach der Abfahrt ist a zwischen Koeln und Paris, bei 30 %",
                u.size() == 1 && u.get(0).getContainer().getContainerId() == a && u.get(0).getVonOrtId() == koeln
                && u.get(0).getNachOrtId() == paris && Math.abs(u.get(0).getAnteil() - 0.3) < 1e-9
                && z.unterwegs(t0 + 10 * min + 300, 0).isEmpty(), u.size() + " unterwegs");

        // --- Zeitleiste ------------------------------------------------
        final com.dan.logistikapp.ui.Zeitleiste zl = new com.dan.logistikapp.ui.Zeitleiste();
        zl.setSize(1400, com.dan.logistikapp.ui.Zeitleiste.HOEHE);
        zl.setDaten(z);
        double maxAbw = 0;
        double vorX = -1;
        boolean steigt = true;
        for (long t = zl.getAnfangMs(); t <= zl.getEndeMs(); t += 7 * min) {
            double x = zl.zeitZuX(t);
            maxAbw = Math.max(maxAbw, Math.abs(zl.zeitZuX(zl.xZuZeit(x)) - x));
            steigt &= x >= vorX;
            vorX = x;
        }
        double xa = zl.zeitZuX(t0 + 35 * min);
        double xb = zl.zeitZuX(t0 + 180 * min);
        double x10 = zl.zeitZuX(t0 + 10 * min) - zl.zeitZuX(t0);
        check("Achse gestaucht: 2 h 25 ohne Ereignis = schmaler Bruch, 10 min mit Fahrten breit; hin und zurueck genau",
                zl.getGestaucht() >= 2 && steigt && maxAbw <= 1.0 && Math.round(xb - xa) == 26 && x10 > 100,
                zl.getGestaucht() + " Brueche, 2 h 25: " + Math.round(xb - xa) + " px, 10 min: " + Math.round(x10)
                + " px, Abweichung "
                + String.format(java.util.Locale.GERMANY, "%.2f", maxAbw) + " px");

        final List<Long> gesehen = new ArrayList<Long>();
        final int[] fahrend = {0};
        zl.setBeobachter(new com.dan.logistikapp.ui.Zeitleiste.Beobachter() {
            @Override
            public void zeit(Long ms, long dauer) {
                gesehen.add(ms);
            }
        });
        zl.setZeit(zl.getAnfangMs());
        zl.schritt(1);
        zl.schritt(1);
        Long nachZwei = zl.getZeit();
        zl.schritt(-1);
        Long zurueck = zl.getZeit();
        check("Schritte: vor springt kurz hinter die naechste Fahrt, zurueck auf die vorige",
                nachZwei != null && nachZwei == t0 + 10 * min + 1 && zurueck != null && zurueck == t0 + 1
                && !zl.spielt(), (nachZwei == null ? "-" : (nachZwei - t0) / 1000) + " s, " + (zurueck == null ? "-"
                : (zurueck - t0) / 1000) + " s");

        // Abspielen von vorn (x60), Leerlauf ueberspringen, in die Prognose hinein
        zl.jetzt();
        gesehen.clear();
        zl.spielen();
        int takte = 0;
        final com.dan.logistikapp.model.Zeitreise zz = z;
        boolean durchBruch = false;
        while (zl.spielt() && takte < 5000) {
            zl.tick(40);
            takte++;
            Long t = zl.getZeit();
            if (t != null && !zz.unterwegs(t, zl.getTempo() * 1300L).isEmpty()) {
                fahrend[0]++;
            }
            durchBruch |= t != null && t > t0 + 60 * min && t < t0 + 170 * min;
        }
        check("Abspielen: von vorn im Zeitraffer, Leerlauf uebersprungen, endet am Ende der Prognose",
                !zl.spielt() && zl.getZeit() != null && zl.getZeit() == zl.getEndeMs() && takte < 1500
                && fahrend[0] > 20 && gesehen.get(0) == zl.getAnfangMs() && !durchBruch,
                takte + " Takte (" + takte * 40 / 1000 + " s), " + fahrend[0] + " mit Fahrt, Ende "
                + (zl.getZeit() == null ? "live" : "Prognose"));

        // --- Karte + Leitstand -------------------------------------------
        final KartenPanel p = new KartenPanel();
        final com.dan.logistikapp.ui.Leitstand l = new com.dan.logistikapp.ui.Leitstand(p, 150);
        final BufferedImage img = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
        edt(new Runnable() {
            @Override
            public void run() {
                p.setSize(1400, 900 - com.dan.logistikapp.ui.Zeitleiste.HOEHE);
                p.setModell(KartenModell.aus(q));
                p.allesZeigen();
                p.setContainerDienst(d);
                p.setContainerStand(d.container());
                l.getZeitleiste().setSize(1400, com.dan.logistikapp.ui.Zeitleiste.HOEHE);
                l.neuLaden();
            }
        });
        warteAlles(p, l, img);
        final int zollLive = l.getZollTafel().getFaelle().size();
        edt(new Runnable() {
            @Override
            public void run() {
                l.getZeitleiste().setZeit(t0 + 25 * min);
            }
        });
        warte(p, img);
        final String[] modus = new String[1];
        final int[] aufrufe = {d.getAufrufe()};
        ContainerInfo damalsB = nachIdAnzeige(p, b);
        check("Leitstand: Zeitleiste geladen, Karte zeigt 25 min nach Beginn - b in Oslo beim Zoll, Band 'ZEITREISE'",
                l.getZeitreise() != null && l.getZeitreise().getFahrtenAnzahl() == 5 && p.istZeitreise()
                && damalsB != null && damalsB.getOrtId() == oslo && "ZOLL".equals(damalsB.getStatus()),
                (l.getZeitreise() == null ? "-" : l.getZeitreise().getFahrtenAnzahl() + " Fahrten, ")
                + (damalsB == null ? "-" : damalsB.getOrt() + "/" + damalsB.getStatus()));
        check("Seitenleiste zeigt den Stand von damals: Zoll-Liste 1 Fall ohne Knoepfe, 3 Fahrten, Bestand Oslo",
                l.getZollTafel().getFaelle().size() == 1 && l.getZollTafel().istNurAnsicht()
                && l.getFahrtenTafel().getFahrten().size() == 3 && bestandTeu(l, oslo) >= 1,
                l.getZollTafel().getFaelle().size() + " Zoll, " + l.getFahrtenTafel().getFahrten().size() + " Fahrten");
        // Nur Ansicht: Druecken auf einen Container zieht nichts, der Auftragslauf ruht
        druecke(p, damalsB);
        edt(new Runnable() {
            @Override
            public void run() {
                modus[0] = p.getZiehModus();
                l.getAuftragsLauf().tick();
            }
        });
        lasseLos(p);
        check("Nur Ansicht: Ziehen startet nicht, kein Aufruf an den Dienst, Auftragslauf ruht",
                "RUHE".equals(modus[0]) && d.getAufrufe() == aufrufe[0] && !p.fahreAuftrag(d.auftraege().get(0)),
                modus[0] + ", Aufrufe " + d.getAufrufe());

        // Bild: mitten in einer Fahrt (Wiedergabe-Dauer 1,3 s bei x60)
        edt(new Runnable() {
            @Override
            public void run() {
                l.getZeitleiste().setZeit(t0 + 180 * min + 40000L);
                l.zeigeZeit(t0 + 180 * min + 40000L, 78000L);
                l.getSeitenLeiste().klickReiter(com.dan.logistikapp.ui.SeitenLeiste.ZOLL);
            }
        });
        final int unterwegsBild = p.getUnterwegsAnzahl();
        bildMitZeit(p, l, new File(base, "zeitreise_vorschau.png"));

        // Prognose
        edt(new Runnable() {
            @Override
            public void run() {
                l.getZeitleiste().setZeit(jetzt + 15 * min);
            }
        });
        ContainerInfo progA = nachIdAnzeige(p, a);
        bildMitZeit(p, l, new File(base, "prognose_vorschau.png"));
        check("Bilder: eine Fahrt mitten im Zeitraffer, dann die Prognose - a in Berlin",
                unterwegsBild == 1 && progA != null && progA.getOrtId() == berlin, unterwegsBild + " unterwegs, a in "
                + (progA == null ? "-" : progA.getOrt()));

        // Randfaelle: Stornieren waehrend der Reise geht nicht, Esc bringt jetzt zurueck
        final int offenVorher = l.getAuftragsTafel().getOffen().size();
        edt(new Runnable() {
            @Override
            public void run() {
                l.getZeitleiste().setZeit(t0 + 40 * min);
                java.awt.Rectangle k = l.getAuftragsTafel().stornoKnopf(0);
                l.getAuftragsTafel().klick(k.x + 4, k.y + 4);
            }
        });
        warteAlles(p, l, img);
        int offenDanach = 0;
        for (com.dan.logistikapp.model.Auftrag au : d.auftraege()) {
            offenDanach += au.istOffen() ? 1 : 0;
        }
        final String hinweisStorno = p.getToastText();
        edt(new Runnable() {
            @Override
            public void run() {
                p.getActionMap().get("abbrechen").actionPerformed(null);
            }
        });
        warteAlles(p, l, img);
        check("Randfaelle: Stornieren waehrend der Reise abgewiesen (Auftrag bleibt), Esc = zurueck zu jetzt",
                offenVorher == 1 && offenDanach == 1 && hinweisStorno != null && hinweisStorno.contains("nur Ansicht")
                && !p.istZeitreise() && l.getZeitleiste().getZeit() == null,
                offenDanach + " offen, " + hinweisStorno + ", Zeitreise " + p.istZeitreise());
        edt(new Runnable() {
            @Override
            public void run() {
                l.getZeitleiste().setZeit(t0 + 40 * min);
            }
        });

        // Jetzt: alles wieder live
        edt(new Runnable() {
            @Override
            public void run() {
                l.getZeitleiste().klick(l.getZeitleiste().knopfJetzt().x + 5, l.getZeitleiste().knopfJetzt().y + 5);
            }
        });
        warteAlles(p, l, img);
        check("Jetzt: Karte wieder live, Zoll-Liste mit Knoepfen wie vorher, alle 5 Fahrten",
                !p.istZeitreise() && l.getZeitleiste().getZeit() == null && !l.getZollTafel().istNurAnsicht()
                && l.getZollTafel().getFaelle().size() == zollLive && l.getFahrtenTafel().getFahrten().size() == 5,
                l.getZollTafel().getFaelle().size() + " Zoll, " + l.getFahrtenTafel().getFahrten().size() + " Fahrten");
    }

    private static ContainerInfo nachIdAnzeige(final KartenPanel p, final int id) throws Exception {
        final ContainerInfo[] r = new ContainerInfo[1];
        edt(new Runnable() {
            @Override
            public void run() {
                for (ContainerInfo c : p.getAnzeigeModell().getContainer()) {
                    if (c.getContainerId() == id) {
                        r[0] = c;
                    }
                }
            }
        });
        return r[0];
    }

    private static int bestandTeu(com.dan.logistikapp.ui.Leitstand l, int ortId) {
        for (com.dan.logistikapp.model.Bestand.Stadt s : l.getBestandTafel().getStand()) {
            if (s.getOrt().getOrtId() == ortId) {
                return s.getTeu();
            }
        }
        return -1;
    }

    private static void auftraege(final DateiQuelle q, File base) throws Exception {
        say("");
        say("Auftraege (Runde 2): Regeln wie LOG_API, Planer, Lauf, Blockade, Abfrage");
        final int hamburg = ortIdVon(q, "Hamburg");
        final int berlin = ortIdVon(q, "Berlin");
        final int muenchen = ortIdVon(q, "München");
        final int oslo = ortIdVon(q, "Oslo");
        long jetzt = System.currentTimeMillis();

        // Dieselbe Szene wie Abschnitt N im Durchstich - im Speicher
        com.dan.logistikapp.dienst.SpeicherDienst s = speicher(q).setVerzoegerungMs(0);
        int a = 0;
        for (ContainerInfo c : s.container()) {
            if (c.getOrtId() == hamburg && c.getGroesseFuss() == 20 && a == 0) {
                a = c.getContainerId();
            }
        }
        long spaet = s.auftragAnlegen(a, muenchen, jetzt - 60000);
        long frueh = s.auftragAnlegen(a, berlin, jetzt - 120000);
        String e1 = s.auftragAusfuehren(spaet).getErgebnis();
        com.dan.logistikapp.dienst.AuftragErgebnis e2 = s.auftragAusfuehren(frueh);
        com.dan.logistikapp.dienst.AuftragErgebnis e3 = s.auftragAusfuehren(spaet);
        long zukunft = s.auftragAnlegen(a, hamburg, jetzt + 3600000);
        String e4 = s.auftragAusfuehren(zukunft).getErgebnis();
        s.auftragStornieren(zukunft);
        com.dan.logistikapp.dienst.DienstFehler f1 = null;
        try {
            s.auftragAusfuehren(zukunft);
        } catch (com.dan.logistikapp.dienst.DienstFehler x) {
            f1 = x;
        }
        s.verschieben(a, ortIdVon(q, "Zürich"));
        long block = s.auftragAnlegen(a, muenchen, jetzt - 60000);
        com.dan.logistikapp.dienst.AuftragErgebnis e5 = s.auftragAusfuehren(block);
        s.zollFreigeben(a);
        String e6 = s.auftragAusfuehren(block).getErgebnis();
        if ("ZOLL".equals(s.container().get(a - 1).getStatus())) {
            s.zollFreigeben(a);
        }
        long schon = s.auftragAnlegen(a, muenchen, jetzt - 60000);
        com.dan.logistikapp.dienst.AuftragErgebnis e8 = s.auftragAusfuehren(schon);
        check("Dienst im Speicher folgt LOG_API: WARTET, ERLEDIGT, ERLEDIGT, NICHT_FAELLIG, storniert = nicht offen",
                "WARTET".equals(e1) && e2.istGefahren() && e3.istGefahren() && "NICHT_FAELLIG".equals(e4)
                && f1 != null && f1.getArt() == com.dan.logistikapp.dienst.DienstFehler.Art.AUFTRAG_NICHT_OFFEN,
                e1 + ", " + e2 + ", " + e3 + ", " + e4 + ", " + (f1 == null ? "-" : f1.getArt()));
        check("... beim Zoll BLOCKIERT mit Grund, nach Freigabe ERLEDIGT; schon am Ziel: ERLEDIGT ohne Fahrt",
                "BLOCKIERT".equals(e5.getErgebnis()) && e5.getGrund() != null && e5.getGrund().contains("Zoll")
                && "ERLEDIGT".equals(e6) && "ERLEDIGT".equals(e8.getErgebnis()) && e8.getFahrt() == null
                && "AUFTRAG".equals(s.historie(a).get(0).getAusgeloest()),
                e5 + " / " + e6 + " / " + e8);

        // Karte + Leitstand; der Planer antwortet selbst (kein Fenster)
        final com.dan.logistikapp.dienst.SpeicherDienst d = speicher(q).setVerzoegerungMs(150);
        final KartenPanel p = new KartenPanel();
        final com.dan.logistikapp.ui.Leitstand l = new com.dan.logistikapp.ui.Leitstand(p, 150);
        final BufferedImage img = new BufferedImage(1400, 900, BufferedImage.TYPE_INT_RGB);
        final int[] minuten = {0};
        final List<String> geoeffnet = new ArrayList<String>();
        l.setPlanerFenster(new com.dan.logistikapp.ui.Leitstand.PlanerFenster() {
            @Override
            public com.dan.logistikapp.ui.AuftragPlaner zeige(com.dan.logistikapp.ui.AuftragPlaner pl) {
                geoeffnet.add(pl.getContainerId() + "->" + pl.getZielOrtId() + " | " + pl.getVorschau());
                pl.setMinuten(minuten[0]);
                return pl;
            }
        });
        l.getAuftragsLauf().setWiederholenMs(400);
        edt(new Runnable() {
            @Override
            public void run() {
                p.setSize(1400, 900);
                p.setModell(KartenModell.aus(q));
                p.allesZeigen();
                p.setContainerDienst(d);
                l.neuLaden();
            }
        });
        warteAlles(p, l, img);

        // Shift + Ziehen: plant statt zu fahren
        final ContainerInfo kohle = finde(p, "Hamburg", "KOHLE");
        druecke(p, kohle);
        KartenModell.OrtPunkt zb = p.getModell().punkt(berlin);
        java.awt.geom.Point2D.Double bs = p.getAnsicht().zuBildschirm(zb.getX(), zb.getY());
        zieheNach(p, bs.x + 10, bs.y + 8);
        edt(new Runnable() {
            @Override
            public void run() {
                java.awt.event.MouseEvent e = new java.awt.event.MouseEvent(p, java.awt.event.MouseEvent.MOUSE_RELEASED,
                        System.currentTimeMillis(), java.awt.event.InputEvent.SHIFT_DOWN_MASK,
                        (int) mx[0], (int) mx[1], 1, false, java.awt.event.MouseEvent.BUTTON1);
                for (java.awt.event.MouseListener ml : p.getMouseListeners()) {
                    ml.mouseReleased(e);
                }
            }
        });
        String modus = p.getZiehModus();
        warteAlles(p, l, img);
        check("Shift + Ziehen: Container federt zurueck, Planer oeffnet mit Ziel Berlin, noch keine Fahrt",
                "RUECK".equals(modus) && geoeffnet.size() == 1 && geoeffnet.get(0).startsWith(kohle.getContainerId()
                + "->" + berlin) && d.getAufrufe() == 0, modus + ", " + geoeffnet);
        check("Planer-Vorschau nennt Strecke, Kosten und Platz", geoeffnet.get(0).contains("€")
                && geoeffnet.get(0).contains("frei") && geoeffnet.get(0).contains("zollfrei"), geoeffnet.get(0));
        check("Auftrag steht in der Liste, im Reiter und als Vorschau auf der Karte",
                l.getAuftragsTafel().getOffen().size() == 1 && l.getSeitenLeiste().getAuftragAnzahl() == 1
                && p.getPlanStrecken() == 1 && p.getToastText() != null && p.getToastText().startsWith("Auftrag #"),
                l.getAuftragsTafel().getOffen().size() + " offen, " + p.getPlanStrecken() + " Strecken, " + p.getToastText());

        // Ein zweiter, spaeterer Auftrag fuer das Bild (Rechtsklick-Weg)
        minuten[0] = 5;
        final ContainerInfo weizen = finde(p, "Hamburg", "WEIZEN");
        edt(new Runnable() {
            @Override
            public void run() {
                l.planerOeffnen(weizen.getContainerId(), oslo);
            }
        });
        warteAlles(p, l, img);
        edt(new Runnable() {
            @Override
            public void run() {
                l.getSeitenLeiste().klickReiter(com.dan.logistikapp.ui.SeitenLeiste.AUFTRAEGE);
            }
        });
        bildMitLeiste(p, l, new File(base, "auftraege_vorschau.png"));

        // Der Lauf faehrt den faelligen - sichtbar
        final String[] m1 = new String[1];
        final boolean[] gefahren = new boolean[1];
        p.addPropertyChangeListener("auftragGefahren", new java.beans.PropertyChangeListener() {
            @Override
            public void propertyChange(java.beans.PropertyChangeEvent e) {
                gefahren[0] = true;
            }
        });
        edt(new Runnable() {
            @Override
            public void run() {
                l.getAuftragsLauf().tick();
                m1[0] = p.getZiehModus();
            }
        });
        warteAlles(p, l, img);
        com.dan.logistikapp.model.Bewegung letzte = d.historie(kohle.getContainerId()).isEmpty() ? null
                : d.historie(kohle.getContainerId()).get(0);
        check("Faelliger Auftrag: die Karte faehrt ihn mit Animation, als AUFTRAG, Meldung 'Auftrag · ...'",
                "FAHRT".equals(m1[0]) && gefahren[0] && letzte != null && "AUFTRAG".equals(letzte.getAusgeloest())
                && nachId(p, kohle.getContainerId()).getOrtId() == berlin && p.getToastText().startsWith("Auftrag"),
                m1[0] + ", " + p.getToastText());
        check("Danach: ein offener (Weizen, in 5 min), einer abgeschlossen mit Fahrt",
                l.getAuftragsTafel().getOffen().size() == 1 && l.getAuftragsTafel().getFertig().size() == 1
                && l.getAuftragsTafel().getFertig().get(0).getBewegungId() > 0, l.getAuftragsTafel().getOffen().size()
                + " offen, " + l.getAuftragsTafel().getFertig().size() + " fertig");

        // Blockiert: der Kohle-Container faehrt nach Oslo (Zoll) und bekommt einen neuen Auftrag
        ziehe(p, nachId(p, kohle.getContainerId()), "Oslo", img, null);
        warteAlles(p, l, img);
        minuten[0] = 0;
        edt(new Runnable() {
            @Override
            public void run() {
                l.planerOeffnen(kohle.getContainerId(), muenchen);
            }
        });
        warteAlles(p, l, img);
        final String[] m2 = new String[1];
        edt(new Runnable() {
            @Override
            public void run() {
                l.getAuftragsLauf().tick();
                m2[0] = p.getZiehModus();
            }
        });
        warteAlles(p, l, img);
        com.dan.logistikapp.model.Auftrag blockiert = null;
        for (com.dan.logistikapp.model.Auftrag x : l.getAuftragsTafel().getOffen()) {
            blockiert = x.getContainerId() == kohle.getContainerId() ? x : blockiert;
        }
        check("Beim Zoll: keine Animation, still versucht - BLOCKIERT, Grund und Versuch in der Liste",
                "RUHE".equals(m2[0]) && blockiert != null && blockiert.getVersuche() == 1
                && blockiert.getGrund() != null && blockiert.getGrund().contains("Zoll")
                && l.getAuftragsTafel().blockiert() == 1, blockiert == null ? "-" : blockiert.getGrund());
        edt(new Runnable() {
            @Override
            public void run() {
                l.getAuftragsLauf().tick();
            }
        });
        warteAlles(p, l, img);
        check("Sofort danach kein neuer Versuch (Wiederholzeit)", auftragNr(d, blockiert.getAuftragId()).getVersuche() == 1,
                auftragNr(d, blockiert.getAuftragId()).getVersuche() + " Versuche");
        final ContainerInfo imZoll = nachId(p, kohle.getContainerId());
        edt(new Runnable() {
            @Override
            public void run() {
                p.zollFreigeben(imZoll);
            }
        });
        warteAlles(p, l, img);
        Thread.sleep(450);
        edt(new Runnable() {
            @Override
            public void run() {
                l.getAuftragsLauf().tick();
            }
        });
        warteAlles(p, l, img);
        check("Nach Freigabe und Wiederholzeit faehrt die Karte ihn", nachId(p, kohle.getContainerId()).getOrtId() == muenchen
                && auftragNr(d, blockiert.getAuftragId()).getStatus() == com.dan.logistikapp.model.Auftrag.Status.ERLEDIGT,
                auftragNr(d, blockiert.getAuftragId()).toString());

        // Stornieren ueber den Knopf
        final com.dan.logistikapp.ui.AuftragsTafel at = l.getAuftragsTafel();
        final long weizenAuftrag = at.getOffen().get(0).getAuftragId();
        edt(new Runnable() {
            @Override
            public void run() {
                at.setSize(320, 800);
                java.awt.Rectangle k = at.stornoKnopf(0);
                at.klick((int) k.getCenterX(), (int) k.getCenterY());
            }
        });
        warteAlles(p, l, img);
        check("Stornieren-Knopf: Auftrag STORNIERT, Karte zeigt keine Vorschau mehr",
                auftragNr(d, weizenAuftrag).getStatus() == com.dan.logistikapp.model.Auftrag.Status.STORNIERT
                && at.getOffen().isEmpty() && p.getPlanStrecken() == 0 && l.getSeitenLeiste().getAuftragAnzahl() == 0,
                auftragNr(d, weizenAuftrag).toString());

        // Abfrage: eine Aenderung an der Karte vorbei (wie Job oder zweiter Arbeitsplatz)
        edt(new Runnable() {
            @Override
            public void run() {
                l.abfragen();
            }
        });
        warteAlles(p, l, img);
        ContainerInfo holz = finde(p, "Hamburg", "HOLZ");
        d.verschieben(holz.getContainerId(), berlin);
        int vorher = nachId(p, holz.getContainerId()).getOrtId();
        edt(new Runnable() {
            @Override
            public void run() {
                l.abfragen();
            }
        });
        warteAlles(p, l, img);
        check("Standmarke: eine fremde Fahrt wird bei der naechsten Abfrage sichtbar",
                vorher == hamburg && nachId(p, holz.getContainerId()).getOrtId() == berlin,
                "vorher " + vorher + ", jetzt " + nachId(p, holz.getContainerId()).getOrtId());
    }

    private static void male(KartenPanel p, BufferedImage img) {
        Graphics2D g = img.createGraphics();
        p.paint(g);
        g.dispose();
    }

    private static ContainerInfo finde(KartenPanel p, String ort, String ware) {
        for (ContainerInfo c : p.getModell().getContainer()) {
            if (ort.equals(c.getOrt()) && ware.equals(c.getWareCode())) {
                return c;
            }
        }
        throw new IllegalStateException(ware + " in " + ort + " nicht gefunden");
    }

    private static ContainerInfo nachId(KartenPanel p, int id) {
        for (ContainerInfo c : p.getModell().getContainer()) {
            if (c.getContainerId() == id) {
                return c;
            }
        }
        return null;
    }

    private static void maus(final KartenPanel p, final int typ, final double x, final double y) throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                int mod = typ == java.awt.event.MouseEvent.MOUSE_RELEASED ? 0 : java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
                java.awt.event.MouseEvent e = new java.awt.event.MouseEvent(p, typ, System.currentTimeMillis(), mod,
                        (int) Math.round(x), (int) Math.round(y), 1, false, java.awt.event.MouseEvent.BUTTON1);
                if (typ == java.awt.event.MouseEvent.MOUSE_DRAGGED) {
                    for (java.awt.event.MouseMotionListener l : p.getMouseMotionListeners()) {
                        l.mouseDragged(e);
                    }
                } else {
                    for (java.awt.event.MouseListener l : p.getMouseListeners()) {
                        if (typ == java.awt.event.MouseEvent.MOUSE_PRESSED) {
                            l.mousePressed(e);
                        } else {
                            l.mouseReleased(e);
                        }
                    }
                }
            }
        });
    }

    private static double[] mx = new double[2];

    private static void druecke(KartenPanel p, ContainerInfo c) throws Exception {
        ContainerEbene.Platz pl = p.getContainerEbene().platz(c.getContainerId());
        mx[0] = pl.getAnkerX();
        mx[1] = pl.getAnkerY() - 3;
        maus(p, java.awt.event.MouseEvent.MOUSE_PRESSED, mx[0], mx[1]);
    }

    private static void zieheNach(KartenPanel p, double x, double y) throws Exception {
        for (int i = 1; i <= 12; i++) {
            maus(p, java.awt.event.MouseEvent.MOUSE_DRAGGED, mx[0] + (x - mx[0]) * i / 12, mx[1] + (y - mx[1]) * i / 12);
        }
        mx[0] = x;
        mx[1] = y;
    }

    private static void lasseLos(KartenPanel p) throws Exception {
        maus(p, java.awt.event.MouseEvent.MOUSE_RELEASED, mx[0], mx[1]);
    }

    private static void ziehe(final KartenPanel p, ContainerInfo c, String ziel, final BufferedImage img, File png)
            throws Exception {
        druecke(p, c);
        KartenModell.OrtPunkt z = null;
        for (KartenModell.OrtPunkt o : p.getModell().getPunkte()) {
            if (ziel.equals(o.getOrt().getName())) {
                z = o;
            }
        }
        java.awt.geom.Point2D.Double s = p.getAnsicht().zuBildschirm(z.getX(), z.getY());
        // knapp neben der Stadt loslassen - der Fangradius soll greifen
        zieheNach(p, s.x + 14, s.y + 10);
        if (png != null) {
            javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    male(p, img);
                }
            });
            ImageIO.write(img, "png", png);
            say("   Bild: " + png.getName() + " (waehrend des Ziehens)");
        }
        lasseLos(p);
    }

    private static void warte(final KartenPanel p, final BufferedImage img) throws Exception {
        long bis = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < bis) {
            final boolean[] ruhig = new boolean[1];
            javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    male(p, img);
                    ruhig[0] = p.istRuhig();
                }
            });
            if (ruhig[0]) {
                return;
            }
            Thread.sleep(40);
        }
        say("         (Zeitueberschreitung beim Warten, Modus " + p.getZiehModus() + ")");
    }

    // ------------------------------------------------------------ Dateiquelle

    /** Liest Laender aus der TSV und die Zugehoerigkeiten aus den SQL-Skripten. */
    static final class DateiQuelle implements KartenQuelle {
        final Map<String, Integer> vertexSoll = new HashMap<String, Integer>();
        private final List<Land> laender = new ArrayList<Land>();
        private final List<Ort> orte = new ArrayList<Ort>();
        private final List<Ware> waren = new ArrayList<Ware>();
        private final List<ContainerInfo> container = new ArrayList<ContainerInfo>();
        private com.dan.logistikapp.model.Tarif tarif;

        DateiQuelle(File db) throws Exception {
            Map<String, String[]> attr = new HashMap<String, String[]>();
            // 05: VALUES ('DE', 'Deutschland', 'J', 'J', 'J', 'J')
            Pattern p05 = Pattern.compile("VALUES \\('([A-Z]{2})', '([^']+)', '([JN])', '([JN])', '([JN])', '([JN])'\\)");
            // 06: land('AT', 'Oesterreich', 'J', 'J', 'J');
            Pattern p06 = Pattern.compile("land\\('([A-Z]{2})',\\s*'([^']+)',\\s*'([JN])',\\s*'([JN])',\\s*'([JN])'\\)");
            Pattern pOrt = Pattern.compile("VALUES \\('([^']+)', '([A-Z]{2})',\\s*$");
            Pattern pPkt = Pattern.compile("SDO_POINT_TYPE\\(([-0-9.]+), ([-0-9.]+), NULL\\)");
            Pattern pWare = Pattern.compile("VALUES \\('([A-Z_]+)', '([^']+)', '(#[0-9A-F]{6})', '([JN])', (\\d+)\\)");
            Pattern pNeu = Pattern.compile("neu\\('([^']+)',\\s*'([A-Z_]+)',\\s*(\\d+)\\)");
            List<String[]> neu = new ArrayList<String[]>();
            String ortName = null;
            String ortIso = null;
            int ortId = 1;
            for (String datei : new String[] {"05_daten.sql", "06_laender.sql"}) {
                for (String z : zeilen(new File(db, datei))) {
                    Matcher a = p05.matcher(z);
                    if (a.find()) {
                        attr.put(a.group(1), new String[] {a.group(2), a.group(3), a.group(4), a.group(5), a.group(6)});
                    }
                    Matcher b = p06.matcher(z);
                    if (b.find()) {
                        attr.put(b.group(1), new String[] {b.group(2), "N", b.group(3), b.group(4), b.group(5)});
                    }
                    Matcher o = pOrt.matcher(z);
                    if (o.find()) {
                        ortName = o.group(1);
                        ortIso = o.group(2);
                    }
                    Matcher wm = pWare.matcher(z);
                    if (wm.find()) {
                        waren.add(new Ware(wm.group(1), wm.group(2), Ware.farbe(wm.group(3)),
                                "J".equals(wm.group(4)), Integer.parseInt(wm.group(5))));
                    }
                    Matcher nm = pNeu.matcher(z);
                    if (nm.find()) {
                        neu.add(new String[] {nm.group(1), nm.group(2), nm.group(3)});
                    }
                    Matcher k = pPkt.matcher(z);
                    if (k.find() && ortName != null) {
                        orte.add(new Ort(ortId++, ortName, ortIso, ortIso, null,
                                Double.parseDouble(k.group(1)), Double.parseDouble(k.group(2))));
                        ortName = null;
                    }
                }
            }
            for (String z : zeilen(new File(db, "07_grenzen.tsv"))) {
                if (z.startsWith("#") || z.trim().isEmpty()) {
                    continue;
                }
                String[] f = z.split("\t", 3);
                String[] a = attr.get(f[0]);
                vertexSoll.put(f[0], Integer.parseInt(f[1]));
                Zone zone = a == null ? null
                        : "J".equals(a[1]) ? Zone.INLAND : "J".equals(a[2]) ? Zone.EU : Zone.DRITTLAND;
                laender.add(new Land(f[0], a == null ? f[0] : a[0], zone,
                        a != null && "J".equals(a[2]), a != null && "J".equals(a[3]),
                        a != null && "J".equals(a[4]), Wkt.lies(f[2])));
            }
            // Runde 2 aus 10_daten2.sql: Tarife, Kapazitaeten, Frachtsaetze - dieselben Zahlen wie in DEMO
            Map<String, java.math.BigDecimal> tarife = new HashMap<String, java.math.BigDecimal>();
            Map<String, java.math.BigDecimal> saetze = new HashMap<String, java.math.BigDecimal>();
            Map<String, Integer> kapazitaet = new HashMap<String, Integer>();
            Pattern pTarif = Pattern.compile("SELECT '([A-Z_]+)'(?: schluessel)?, ([0-9.]+)");
            Pattern pWhen = Pattern.compile("WHEN '([^']+)'\\s+THEN ([0-9.]+)");
            Set<String> codes = new HashSet<String>();
            for (Ware w : waren) {
                codes.add(w.getCode());
            }
            for (String z : zeilen(new File(db, "10_daten2.sql"))) {
                Matcher t = pTarif.matcher(z);
                if (t.find()) {
                    tarife.put(t.group(1), new java.math.BigDecimal(t.group(2)));
                }
                Matcher w = pWhen.matcher(z);
                if (w.find()) {
                    if (codes.contains(w.group(1))) {
                        saetze.put(w.group(1), new java.math.BigDecimal(w.group(2)));
                    } else {
                        kapazitaet.put(w.group(1), Integer.valueOf(w.group(2)));
                    }
                }
            }
            tarif = new com.dan.logistikapp.model.Tarif(tarife, saetze);
            // Zonen der Staedte ueber ihr Land - wie in LOG_ORT_V
            List<Ort> mitZone = new ArrayList<Ort>();
            for (Ort o : orte) {
                Land l = null;
                for (Land x : laender) {
                    if (x.getIso2().equals(o.getIso2())) {
                        l = x;
                    }
                }
                mitZone.add(new Ort(o.getOrtId(), o.getName(), o.getIso2(), l.getName(), l.getZone(),
                        o.getLaenge(), o.getBreite(), kapazitaet.get(o.getName())));
            }
            orte.clear();
            orte.addAll(mitZone);
            // Startbestand wie der Trigger ihn anlegt (die drei Beispielfahrten bleiben aussen vor)
            int id = 1;
            for (String[] n : neu) {
                Ware w = null;
                for (Ware x : waren) {
                    if (x.getCode().equals(n[1])) {
                        w = x;
                    }
                }
                Ort o = null;
                for (Ort x : orte) {
                    if (x.getName().equals(n[0])) {
                        o = x;
                    }
                }
                int fuss = Integer.parseInt(n[2]);
                String typ = (fuss == 40 ? "42" : "22") + (w.isKuehlpflichtig() ? "R1" : "G1");
                container.add(new ContainerInfo(id, Iso6346.neueKennung("DANU", 100000 + id), fuss, typ,
                        w.getCode(), w.getName(), w.getFarbe(), o.getOrtId(), o.getName(), "BEREIT"));
                id++;
            }
        }

        @Override
        public List<Ware> waren() {
            return waren;
        }

        @Override
        public com.dan.logistikapp.model.Tarif tarif() {
            return tarif;
        }

        @Override
        public List<ContainerInfo> container() {
            return container;
        }

        private static List<String> zeilen(File f) throws Exception {
            List<String> out = new ArrayList<String>();
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
            try {
                String s;
                while ((s = r.readLine()) != null) {
                    out.add(s);
                }
            } finally {
                r.close();
            }
            return out;
        }

        @Override
        public List<Land> laender() {
            return laender;
        }

        @Override
        public List<Ort> orte() {
            return orte;
        }
    }

    // ------------------------------------------------------------ Hilfen

    private static void check(String name, boolean gruen, String detail) {
        if (gruen) {
            ok++;
        } else {
            rot++;
        }
        say((gruen ? "   [ok]     " : "   [ROT]    ") + name + (detail == null ? "" : "   - " + detail));
    }

    private static void say(String s) {
        System.out.println(s);
        log.println(s);
    }
}
