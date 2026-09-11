package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.KartenModell;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.OrtAnlage;
import com.dan.logistikapp.model.Ware;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** CSV-Import und -Export der sichtbaren Stamm- und Standortdaten. */
final class StammdatenCsv {

    static final class ImportDaten {
        final List<OrtAnlage> orte = new ArrayList<OrtAnlage>();
        final List<ContainerImport> container = new ArrayList<ContainerImport>();
    }

    static final class ContainerImport {
        private final String ortName;
        private final int groesseFuss;
        private final String wareCode;
        private final int anzahl;

        ContainerImport(String ortName, int groesseFuss, String wareCode, int anzahl) {
            this.ortName = ortName;
            this.groesseFuss = groesseFuss;
            this.wareCode = wareCode;
            this.anzahl = anzahl;
        }

        String getOrtName() { return ortName; }
        int getGroesseFuss() { return groesseFuss; }
        String getWareCode() { return wareCode; }
        int getAnzahl() { return anzahl; }
    }

    private StammdatenCsv() {
    }

    static void exportiere(File ordner, KartenModell modell) throws IOException {
        if (!ordner.exists() && !ordner.mkdirs()) {
            throw new IOException("Exportordner konnte nicht angelegt werden: " + ordner);
        }
        exportStaedte(new File(ordner, "staedte.csv"), modell);
        exportWaren(new File(ordner, "waren.csv"), modell);
        exportContainer(new File(ordner, "container.csv"), modell);
    }

    private static void exportStaedte(File datei, KartenModell modell) throws IOException {
        BufferedWriter out = Files.newBufferedWriter(datei.toPath(), StandardCharsets.UTF_8);
        try {
            out.write("name;iso2;laenge;breite;kapazitaet_teu\n");
            for (com.dan.logistikapp.model.Ort o : modell.getOrte()) {
                zeile(out, o.getName(), o.getIso2(), format(o.getLaenge()), format(o.getBreite()),
                        o.getKapazitaetTeu() == null ? "" : String.valueOf(o.getKapazitaetTeu()));
            }
        } finally {
            out.close();
        }
    }

    private static void exportWaren(File datei, KartenModell modell) throws IOException {
        BufferedWriter out = Files.newBufferedWriter(datei.toPath(), StandardCharsets.UTF_8);
        try {
            out.write("code;name;farbe_hex;kuehlpflichtig;sortierung\n");
            for (Ware w : modell.getWaren()) {
                zeile(out, w.getCode(), w.getName(), farbe(w.getFarbe()), w.isKuehlpflichtig() ? "J" : "N",
                        String.valueOf(w.getSortierung()));
            }
        } finally {
            out.close();
        }
    }

    private static void exportContainer(File datei, KartenModell modell) throws IOException {
        BufferedWriter out = Files.newBufferedWriter(datei.toPath(), StandardCharsets.UTF_8);
        try {
            out.write("container_id;kennung;groesse_fuss;ware_code;ware;ort_id;ort;status\n");
            for (ContainerInfo c : modell.getContainer()) {
                zeile(out, String.valueOf(c.getContainerId()), c.getKennung(), String.valueOf(c.getGroesseFuss()),
                        c.getWareCode(), c.getWare(), String.valueOf(c.getOrtId()), c.getOrt(), c.getStatus());
            }
        } finally {
            out.close();
        }
    }

    static List<OrtAnlage> importiereStaedte(File datei) throws IOException {
        return importiere(datei).orte;
    }

    /** Liest eine gemeinsame Importdatei mit STADT- und CONTAINER-Zeilen. */
    static ImportDaten importiere(File datei) throws IOException {
        ImportDaten out = new ImportDaten();
        BufferedReader in = Files.newBufferedReader(datei.toPath(), StandardCharsets.UTF_8);
        try {
            String line;
            int zeile = 0;
            boolean alteStaedteCsv = false;
            while ((line = in.readLine()) != null) {
                zeile++;
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                List<String> f = felder(line);
                if (zeile == 1 && f.get(0).equalsIgnoreCase("name")) {
                    alteStaedteCsv = true;
                    continue;
                }
                if (zeile == 1 && f.get(0).equalsIgnoreCase("typ")) {
                    continue;
                }
                if (alteStaedteCsv) {
                    if (f.size() < 4) {
                        throw new IOException("Zeile " + zeile + ": mindestens 4 Stadtspalten erwartet");
                    }
                    Integer kap = f.size() < 5 || f.get(4).trim().isEmpty() ? null : Integer.valueOf(f.get(4).trim());
                    out.orte.add(new OrtAnlage(f.get(0).trim(), f.get(1).trim(), zahl(f.get(2)), zahl(f.get(3)), kap));
                    continue;
                }
                String typ = f.get(0).trim().toUpperCase(java.util.Locale.GERMANY);
                try {
                    if ("STADT".equals(typ)) {
                        if (f.size() < 6) {
                            throw new IOException("Zeile " + zeile + ": STADT braucht 6 Spalten");
                        }
                        Integer kap = f.get(5).trim().isEmpty() ? null : Integer.valueOf(f.get(5).trim());
                        out.orte.add(new OrtAnlage(f.get(1).trim(), f.get(2).trim(),
                                zahl(f.get(3)), zahl(f.get(4)), kap));
                    } else if ("CONTAINER".equals(typ)) {
                        if (f.size() < 9) {
                            throw new IOException("Zeile " + zeile + ": CONTAINER braucht 9 Spalten");
                        }
                        int anzahl = f.get(8).trim().isEmpty() ? 1 : Integer.parseInt(f.get(8).trim());
                        out.container.add(new ContainerImport(f.get(1).trim(), Integer.parseInt(f.get(6).trim()),
                                f.get(7).trim(), anzahl));
                    } else if ("NAME".equalsIgnoreCase(typ)) {
                        continue;
                    } else {
                        throw new IOException("Zeile " + zeile + ": Typ muss STADT oder CONTAINER sein");
                    }
                } catch (NumberFormatException e) {
                    throw new IOException("Zeile " + zeile + ": Zahl ungültig", e);
                }
            }
        } finally {
            in.close();
        }
        return out;
    }

    private static double zahl(String text) {
        return Double.parseDouble(text.trim().replace(',', '.'));
    }

    private static List<OrtAnlage> importiereAlteStaedte(File datei) throws IOException {
        List<OrtAnlage> out = new ArrayList<OrtAnlage>();
        BufferedReader in = Files.newBufferedReader(datei.toPath(), StandardCharsets.UTF_8);
        try {
            String line;
            int zeile = 0;
            while ((line = in.readLine()) != null) {
                zeile++;
                if (line.trim().isEmpty() || line.startsWith("#") || zeile == 1 && line.toLowerCase().startsWith("name;")) {
                    continue;
                }
                List<String> f = felder(line);
                if (f.size() < 4) {
                    throw new IOException("staedte.csv Zeile " + zeile + ": mindestens 4 Spalten erwartet");
                }
                try {
                    Integer kap = f.size() < 5 || f.get(4).trim().isEmpty() ? null : Integer.valueOf(f.get(4).trim());
                    out.add(new OrtAnlage(f.get(0).trim(), f.get(1).trim(),
                            Double.parseDouble(f.get(2).trim().replace(',', '.')),
                            Double.parseDouble(f.get(3).trim().replace(',', '.')), kap));
                } catch (NumberFormatException e) {
                    throw new IOException("staedte.csv Zeile " + zeile + ": Zahl ungültig", e);
                }
            }
        } finally {
            in.close();
        }
        return out;
    }

    private static void zeile(BufferedWriter out, String... felder) throws IOException {
        for (int i = 0; i < felder.length; i++) {
            if (i > 0) {
                out.write(';');
            }
            out.write(quote(felder[i]));
        }
        out.write('\n');
    }

    private static String quote(String s) {
        String v = s == null ? "" : s;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static List<String> felder(String line) {
        List<String> out = new ArrayList<String>();
        StringBuilder s = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    s.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ';' && !quoted) {
                out.add(s.toString());
                s.setLength(0);
            } else {
                s.append(c);
            }
        }
        out.add(s.toString());
        return out;
    }

    private static String format(double wert) {
        return String.format(java.util.Locale.US, "%.8f", wert);
    }

    private static String farbe(Color farbe) {
        return String.format("#%02X%02X%02X", farbe.getRed(), farbe.getGreen(), farbe.getBlue());
    }
}
