package com.dan.logistikapp.ui;

import com.dan.fdal.FDatabaseManager;
import com.dan.fframe.FFrame;
import com.dan.logistikapp.db.DbContainerDienst;
import com.dan.logistikapp.db.DbKartenQuelle;
import com.dan.logistikapp.db.LogDb;
import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.karte.KartenAnsicht;
import com.dan.logistikapp.karte.KartenModell;
import com.dan.logistikapp.karte.KartenPanel;
import com.dan.logistikapp.model.ContainerAnlage;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.Bewegung;
import com.dan.logistikapp.model.Auftrag;
import com.dan.logistikapp.model.Iso6346;
import com.dan.logistikapp.model.OrtAnlage;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutionException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Einstieg der Logistik-App: FFrame mit der Europa-Karte und einer
 * Statuszeile. Die Daten kommen im Hintergrund aus DEMO.
 *
 * @author Dan
 */
public final class LogistikApp {

    private final FFrame frame = new FFrame("Logistik — Europa");
    private final KartenPanel karte = new KartenPanel();
    private final JLabel status = new JLabel(" ");
    private final Leitstand leitstand = new Leitstand(karte, 1400);
    private String ladeText = " ";
    /** Bleibt offen, solange das Fenster lebt: die Karte schreibt ab Phase 4. */
    private FDatabaseManager db;
    private final java.util.prefs.Preferences einstellungen =
            java.util.prefs.Preferences.userNodeForPackage(LogistikApp.class);
    private java.awt.Rectangle letzteNormaleGroesse;

    private LogistikApp() {
        frame.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        frame.setTaskbarColors(new Color(0x0F2A38), new Color(0x1F5E63));
        frame.setComponentPaneColor(KartenFarben.MEER_OBEN);

        status.setOpaque(true);
        status.setBackground(new Color(0x0A1B26));
        status.setForeground(KartenFarben.TEXT_LEISE);
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        status.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JPanel pane = frame.getComponentPane();
        pane.setLayout(new BorderLayout());
        JPanel mitte = new JPanel(new BorderLayout());
        mitte.add(karte, BorderLayout.CENTER);
        mitte.add(leitstand.getZeitleiste(), BorderLayout.SOUTH);
        pane.add(mitte, BorderLayout.CENTER);
        pane.add(leitstand.getSeitenLeiste(), BorderLayout.EAST);
        leitstand.setPlanerFenster(new Leitstand.PlanerFenster() {
            @Override
            public AuftragPlaner zeige(AuftragPlaner planer) {
                return planerDialog(planer);
            }
        });
        leitstand.setContainerFenster(new Leitstand.ContainerFenster() {
            @Override
            public ContainerAnlage zeige(KartenModell modell) {
                return containerDialog(modell);
            }
        });
        leitstand.setStadtFenster(new Leitstand.StadtFenster() {
            @Override
            public OrtAnlage zeige(KartenModell modell) {
                return stadtDialog(modell);
            }
        });
        leitstand.setContainerDetailFenster(new Leitstand.ContainerDetailFenster() {
            @Override
            public void zeige(ContainerInfo container, List<Bewegung> historie, List<Auftrag> auftraege,
                    boolean favorit, Runnable favoritAktion) {
                containerDetailDialog(container, historie, auftraege, favorit, favoritAktion);
            }
        });
        leitstand.setCsvFenster(new Leitstand.CsvFenster() {
            @Override
            public java.io.File exportOrdner() {
                javax.swing.JFileChooser wahl = new javax.swing.JFileChooser();
                wahl.setDialogTitle("Exportordner für Stammdaten auswählen");
                wahl.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
                return wahl.showSaveDialog(frame) == javax.swing.JFileChooser.APPROVE_OPTION
                        ? wahl.getSelectedFile() : null;
            }

            @Override
            public java.io.File importDatei() {
                javax.swing.JFileChooser wahl = new javax.swing.JFileChooser();
                wahl.setDialogTitle("stammdaten_import.csv zum Import auswählen");
                wahl.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV-Dateien", "csv"));
                return wahl.showOpenDialog(frame) == javax.swing.JFileChooser.APPROVE_OPTION
                        ? wahl.getSelectedFile() : null;
            }
        });
        leitstand.setMeldung(new Leitstand.Meldung() {
            @Override
            public void fehler(String text) {
                status.setText(text);
            }
        });
        pane.add(status, BorderLayout.SOUTH);

        karte.addPropertyChangeListener("hinweis", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                String h = (String) e.getNewValue();
                status.setText(h == null || h.isEmpty() ? ladeText : h);
            }
        });
        karte.addPropertyChangeListener("zeitreise", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                status.setText(Boolean.TRUE.equals(e.getNewValue())
                        ? "Zeitreise · nur Ansicht · Esc oder „Jetzt“ = zurück zum heutigen Stand" : ladeText);
            }
        });
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                einstellungenSpeichern();
                leitstand.stoppeHintergrund();
                if (db != null) {
                    db.close();
                }
            }
        });
        int breite = einstellungen.getInt("fenster.breite", 1560);
        int hoehe = einstellungen.getInt("fenster.hoehe", 900);
        int x = einstellungen.getInt("fenster.x", -1);
        int y = einstellungen.getInt("fenster.y", -1);
        letzteNormaleGroesse = new java.awt.Rectangle(x, y, breite, hoehe);
        frame.setPreferredFrameSize(new Dimension(breite, hoehe));
        if (x >= 0 && y >= 0) {
            frame.setLocation(x, y);
        } else {
            frame.setLocationRelativeTo(null);
        }
        frame.setExtendedState(einstellungen.getBoolean("fenster.maximiert", true)
                ? java.awt.Frame.MAXIMIZED_BOTH : java.awt.Frame.NORMAL);
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                normaleGroesseMerken();
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                normaleGroesseMerken();
            }
        });
    }

    private void normaleGroesseMerken() {
        if (frame.getExtendedState() == java.awt.Frame.NORMAL && frame.getWidth() > 0 && frame.getHeight() > 0) {
            letzteNormaleGroesse = frame.getBounds();
        }
    }

    private void einstellungenSpeichern() {
        normaleGroesseMerken();
        if (letzteNormaleGroesse != null) {
            einstellungen.putInt("fenster.breite", letzteNormaleGroesse.width);
            einstellungen.putInt("fenster.hoehe", letzteNormaleGroesse.height);
            einstellungen.putInt("fenster.x", letzteNormaleGroesse.x);
            einstellungen.putInt("fenster.y", letzteNormaleGroesse.y);
        }
        einstellungen.putBoolean("fenster.maximiert",
                (frame.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) != 0);
        KartenAnsicht a = karte.getAnsicht();
        einstellungen.putDouble("karte.massstab", a.getMassstab());
        einstellungen.putDouble("karte.mitte_x", a.getMitteX());
        einstellungen.putDouble("karte.mitte_y", a.getMitteY());
    }

    private void gespeicherteKarteWiederherstellen() {
        if (einstellungen.get("karte.massstab", null) == null) {
            return;
        }
        karte.setGespeicherteAnsicht(einstellungen.getDouble("karte.massstab", 1 / 4000.0),
                einstellungen.getDouble("karte.mitte_x", 4321000),
                einstellungen.getDouble("karte.mitte_y", 3210000));
    }

    private void start() {
        frame.setVisible(true);
        status.setText("Verbinde mit " + LogDb.URL + " …");
        new SwingWorker<KartenModell, Void>() {
            private long ms;

            @Override
            protected KartenModell doInBackground() {
                long t0 = System.currentTimeMillis();
                db = LogDb.oeffnen();
                try {
                    KartenModell m = KartenModell.aus(new DbKartenQuelle(db));
                    // Containerbilder im Hintergrund rendern, nicht beim ersten Zeichnen
                    karte.getContainerEbene().vorwaermen(m.getContainer());
                    return m;
                } finally {
                    ms = System.currentTimeMillis() - t0;
                }
            }

            @Override
            protected void done() {
                try {
                    final KartenModell m = get();
                    karte.setModell(m);
                    gespeicherteKarteWiederherstellen();
                    karte.setContainerDienst(new DbContainerDienst(db));
                    leitstand.neuLaden();
                    // Auftraege faellig fahren, Aenderungen anderer (Job, zweiter Platz) alle 10 s sehen
                    leitstand.starteHintergrund(10000);
                    // Alle Drehstufen fuer die Fahrt: leise im Hintergrund
                    Thread w = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            karte.getContainerEbene().vorwaermenAlleStufen(m.getContainer());
                        }
                    }, "Container-Drehstufen");
                    w.setDaemon(true);
                    w.setPriority(Thread.MIN_PRIORITY);
                    w.start();
                    ladeText = m.getFormen().size() + " Länder, " + m.getPunkte().size()
                            + " Städte, " + m.getContainer().size() + " Container geladen in " + ms + " ms  ·  Container ziehen = verschieben, "
                            + "Rechtsklick = Zoll freigeben / Historie, Esc = abbrechen, Zeitleiste unten = Zeitreise";
                    status.setText(ladeText);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    karte.setMeldung("Datenbank nicht erreichbar: " + ersteZeile(t));
                    status.setText(String.valueOf(t));
                }
            }
        }.execute();
    }

    /** Der Planer als dunkler, modaler Dialog; {@code null} bei Abbrechen. */
    private AuftragPlaner planerDialog(AuftragPlaner planer) {
        final javax.swing.JDialog d = new javax.swing.JDialog(frame, "Auftrag planen", true);
        final boolean[] ok = new boolean[1];
        JPanel knoepfe = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 10));
        knoepfe.setBackground(new Color(0x0A1B26));
        javax.swing.JButton planen = new javax.swing.JButton("Planen");
        javax.swing.JButton abbrechen = new javax.swing.JButton("Abbrechen");
        planen.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                ok[0] = true;
                d.dispose();
            }
        });
        abbrechen.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                d.dispose();
            }
        });
        knoepfe.add(abbrechen);
        knoepfe.add(planen);
        d.getRootPane().setDefaultButton(planen);
        d.getContentPane().setLayout(new BorderLayout());
        d.getContentPane().add(planer, BorderLayout.CENTER);
        d.getContentPane().add(knoepfe, BorderLayout.SOUTH);
        d.pack();
        d.setMinimumSize(new Dimension(560, d.getHeight()));
        d.setLocationRelativeTo(frame);
        d.setVisible(true);
        return ok[0] ? planer : null;
    }

    /** Der Dialog zum Anlegen eines Containers; {@code null} bei Abbrechen. */
    private ContainerAnlage containerDialog(KartenModell modell) {
        final javax.swing.JDialog d = new javax.swing.JDialog(frame, "Neuer Container", true);
        final ContainerAnleger eingabe = new ContainerAnleger(modell);
        final ContainerAnlage[] ergebnis = new ContainerAnlage[1];
        final boolean[] warnungBestaetigt = new boolean[1];
        d.setUndecorated(true);

        JPanel rahmen = new JPanel(new BorderLayout());
        rahmen.setBackground(new Color(0x0C1A23));
        rahmen.setBorder(BorderFactory.createLineBorder(new Color(0x1E3440)));

        JPanel kopf = new JPanel(new BorderLayout());
        kopf.setBackground(new Color(0x0F2A38));
        kopf.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 6));
        kopf.setPreferredSize(new Dimension(560, 42));
        JLabel titel = new JLabel("Neuer Container");
        titel.setForeground(KartenFarben.TEXT);
        titel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        kopf.add(titel, BorderLayout.CENTER);
        javax.swing.JButton schliessen = new javax.swing.JButton("×");
        schliessen.setToolTipText("Schließen");
        schliessen.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        schliessen.setPreferredSize(new Dimension(34, 32));
        stilKnopf(schliessen);
        schliessen.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                d.dispose();
            }
        });
        kopf.add(schliessen, BorderLayout.EAST);
        rahmen.add(kopf, BorderLayout.NORTH);

        final java.awt.Point[] start = new java.awt.Point[1];
        java.awt.event.MouseAdapter verschieben = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                start[0] = e.getPoint();
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (start[0] != null) {
                    d.setLocation(d.getX() + e.getX() - start[0].x,
                            d.getY() + e.getY() - start[0].y);
                }
            }
        };
        kopf.addMouseListener(verschieben);
        kopf.addMouseMotionListener(verschieben);
        titel.addMouseListener(verschieben);
        titel.addMouseMotionListener(verschieben);

        JPanel knoepfe = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 10));
        knoepfe.setBackground(new Color(0x0A1B26));
        javax.swing.JButton anlegen = new javax.swing.JButton("Anlegen");
        javax.swing.JButton abbrechen = new javax.swing.JButton("Abbrechen");
        stilKnopf(anlegen);
        stilKnopf(abbrechen);
        anlegen.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                ContainerAnlage a = eingabe.getAnlage();
                if (a == null) {
                    eingabe.setFehler("Bitte Ware und Standort auswählen.");
                    return;
                }
                String warnung = eingabe.getKapazitaetswarnung();
                if (warnung != null && !warnungBestaetigt[0]) {
                    eingabe.setFehler("Warnung: " + warnung + " Erneut klicken, um trotzdem anzulegen.");
                    warnungBestaetigt[0] = true;
                    return;
                }
                ergebnis[0] = a;
                d.dispose();
            }
        });
        abbrechen.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                d.dispose();
            }
        });
        knoepfe.add(abbrechen);
        knoepfe.add(anlegen);
        rahmen.add(eingabe, BorderLayout.CENTER);
        rahmen.add(knoepfe, BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(anlegen);
        d.setContentPane(rahmen);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(frame);
        d.setVisible(true);
        return ergebnis[0];
    }

    /** Der Dialog zum Anlegen einer Stadt; {@code null} bei Abbrechen. */
    private OrtAnlage stadtDialog(KartenModell modell) {
        final javax.swing.JDialog d = new javax.swing.JDialog(frame, "Neue Stadt", true);
        final OrtAnleger eingabe = new OrtAnleger(modell);
        final OrtAnlage[] ergebnis = new OrtAnlage[1];
        d.setUndecorated(true);

        JPanel rahmen = new JPanel(new BorderLayout());
        rahmen.setBackground(new Color(0x0C1A23));
        rahmen.setBorder(BorderFactory.createLineBorder(new Color(0x1E3440)));
        JPanel kopf = new JPanel(new BorderLayout());
        kopf.setBackground(new Color(0x0F2A38));
        kopf.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 6));
        kopf.setPreferredSize(new Dimension(560, 42));
        JLabel titel = new JLabel("Neue Stadt");
        titel.setForeground(KartenFarben.TEXT);
        titel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        kopf.add(titel, BorderLayout.CENTER);
        javax.swing.JButton schliessen = new javax.swing.JButton("×");
        schliessen.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        schliessen.setPreferredSize(new Dimension(34, 32));
        stilKnopf(schliessen);
        schliessen.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { d.dispose(); }
        });
        kopf.add(schliessen, BorderLayout.EAST);
        rahmen.add(kopf, BorderLayout.NORTH);

        final java.awt.Point[] start = new java.awt.Point[1];
        java.awt.event.MouseAdapter verschieben = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) { start[0] = e.getPoint(); }
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (start[0] != null) {
                    d.setLocation(d.getX() + e.getX() - start[0].x, d.getY() + e.getY() - start[0].y);
                }
            }
        };
        kopf.addMouseListener(verschieben);
        kopf.addMouseMotionListener(verschieben);
        titel.addMouseListener(verschieben);
        titel.addMouseMotionListener(verschieben);

        JPanel knoepfe = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 10));
        knoepfe.setBackground(new Color(0x0A1B26));
        javax.swing.JButton anlegen = new javax.swing.JButton("Anlegen");
        javax.swing.JButton abbrechen = new javax.swing.JButton("Abbrechen");
        stilKnopf(anlegen);
        stilKnopf(abbrechen);
        anlegen.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                OrtAnlage a = eingabe.getAnlage();
                if (a != null) {
                    ergebnis[0] = a;
                    d.dispose();
                } else {
                    eingabe.setFehler(eingabe.getFehler());
                }
            }
        });
        abbrechen.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { d.dispose(); }
        });
        knoepfe.add(abbrechen);
        knoepfe.add(anlegen);
        rahmen.add(eingabe, BorderLayout.CENTER);
        rahmen.add(knoepfe, BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(anlegen);
        d.setContentPane(rahmen);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(frame);
        d.setVisible(true);
        return ergebnis[0];
    }

    /** Detailfenster für einen Container mit Historie und offenen Aufträgen. */
    private void containerDetailDialog(ContainerInfo c, List<Bewegung> historie, List<Auftrag> auftraege,
            boolean favorit, final Runnable favoritAktion) {
        final javax.swing.JDialog d = new javax.swing.JDialog(frame, "Containerdetails", true);
        d.setUndecorated(true);
        JPanel rahmen = new JPanel(new BorderLayout(0, 0));
        rahmen.setBackground(new Color(0x0C1A23));
        rahmen.setBorder(BorderFactory.createLineBorder(new Color(0x1E3440)));

        JPanel kopf = new JPanel(new BorderLayout());
        kopf.setBackground(new Color(0x0F2A38));
        kopf.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 6));
        kopf.setPreferredSize(new Dimension(620, 42));
        JLabel titel = new JLabel("Containerdetails  ·  " + Iso6346.anzeige(c.getKennung()));
        titel.setForeground(KartenFarben.TEXT);
        titel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        kopf.add(titel, BorderLayout.CENTER);
        javax.swing.JButton schliessen = new javax.swing.JButton("×");
        schliessen.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        schliessen.setPreferredSize(new Dimension(34, 32));
        stilKnopf(schliessen);
        schliessen.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { d.dispose(); }
        });
        kopf.add(schliessen, BorderLayout.EAST);
        rahmen.add(kopf, BorderLayout.NORTH);

        final java.awt.Point[] start = new java.awt.Point[1];
        java.awt.event.MouseAdapter verschieben = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) { start[0] = e.getPoint(); }
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (start[0] != null) {
                    d.setLocation(d.getX() + e.getX() - start[0].x, d.getY() + e.getY() - start[0].y);
                }
            }
        };
        kopf.addMouseListener(verschieben);
        kopf.addMouseMotionListener(verschieben);
        titel.addMouseListener(verschieben);
        titel.addMouseMotionListener(verschieben);

        JPanel inhalt = new JPanel(new BorderLayout(0, 12));
        inhalt.setBackground(new Color(0x0C1A23));
        inhalt.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        JPanel info = new JPanel(new java.awt.GridLayout(2, 4, 10, 5));
        info.setOpaque(false);
        info.add(detailFeld("Ware", c.getWare() + " (" + c.getWareCode() + ")"));
        info.add(detailFeld("Größe", c.getGroesseFuss() + " Fuß · " + c.getTeu() + " TEU"));
        info.add(detailFeld("Standort", c.getOrt()));
        info.add(detailFeld("Status", "ZOLL".equals(c.getStatus()) ? "Beim Zoll" : "Bereit"));
        info.add(detailFeld("Typ", c.getTypCode()));
        info.add(detailFeld("Kennung", Iso6346.anzeige(c.getKennung())));
        inhalt.add(info, BorderLayout.NORTH);

        JPanel listen = new JPanel(new java.awt.GridLayout(1, 2, 12, 0));
        listen.setOpaque(false);
        listen.add(detailListe("Historie", historieText(historie, c)));
        listen.add(detailListe("Geplante Aufträge", auftraegeText(auftraege, c)));
        inhalt.add(listen, BorderLayout.CENTER);
        rahmen.add(inhalt, BorderLayout.CENTER);

        JPanel unten = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 10));
        unten.setBackground(new Color(0x0A1B26));
        final boolean[] markiert = new boolean[] {favorit};
        javax.swing.JButton stern = new javax.swing.JButton(markiert[0] ? "★ Favorit" : "☆ Favorit");
        stilKnopf(stern);
        stern.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (favoritAktion != null) {
                    favoritAktion.run();
                    markiert[0] = !markiert[0];
                    ((javax.swing.JButton) e.getSource()).setText(markiert[0] ? "★ Favorit" : "☆ Favorit");
                }
            }
        });
        javax.swing.JButton ok = new javax.swing.JButton("Schließen");
        stilKnopf(ok);
        ok.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { d.dispose(); }
        });
        unten.add(stern);
        unten.add(ok);
        rahmen.add(unten, BorderLayout.SOUTH);
        d.setContentPane(rahmen);
        d.pack();
        d.setMinimumSize(new Dimension(680, 360));
        d.setResizable(false);
        d.setLocationRelativeTo(frame);
        d.setVisible(true);
    }

    private static JLabel detailFeld(String label, String wert) {
        JLabel l = new JLabel("<html><font color='#86AFC0'>" + label + "</font><br>"
                + escapeHtml(wert) + "</html>");
        l.setForeground(KartenFarben.TEXT);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return l;
    }

    private static javax.swing.JComponent detailListe(String titel, String text) {
        JPanel p = new JPanel(new BorderLayout(0, 5));
        p.setOpaque(false);
        JLabel l = new JLabel(titel);
        l.setForeground(KartenFarben.TEXT);
        l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        p.add(l, BorderLayout.NORTH);
        javax.swing.JTextArea a = new javax.swing.JTextArea(text);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBackground(new Color(0x13262F));
        a.setForeground(KartenFarben.TEXT_LEISE);
        a.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        a.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x1E3440)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        javax.swing.JScrollPane rolle = new javax.swing.JScrollPane(a);
        rolle.setBorder(BorderFactory.createLineBorder(new Color(0x1E3440)));
        rolle.setBackground(new Color(0x13262F));
        rolle.getViewport().setBackground(new Color(0x13262F));
        rolle.getVerticalScrollBar().setUI(new StilScrollBarUI());
        rolle.getHorizontalScrollBar().setUI(new StilScrollBarUI());
        rolle.getVerticalScrollBar().setOpaque(false);
        rolle.getHorizontalScrollBar().setOpaque(false);
        p.add(rolle, BorderLayout.CENTER);
        return p;
    }

    private static String historieText(List<Bewegung> historie, ContainerInfo c) {
        if (historie == null || historie.isEmpty()) {
            return "Noch keine Fahrten.";
        }
        StringBuilder s = new StringBuilder();
        for (Bewegung b : historie) {
            s.append("#").append(b.getBewegungId()).append("  ")
                    .append(b.getVonOrt()).append(" → ").append(b.getNachOrt())
                    .append(String.format(java.util.Locale.GERMANY, "  ·  %,.0f km", b.getDistanzM() / 1000.0));
            if (b.isZoll()) {
                s.append("  ·  Zoll");
            }
            s.append("\n");
        }
        return s.toString().trim();
    }

    private static String auftraegeText(List<Auftrag> auftraege, ContainerInfo c) {
        StringBuilder s = new StringBuilder();
        if (auftraege != null) {
            for (Auftrag a : auftraege) {
                if (a.getContainerId() == c.getContainerId() && a.istOffen()) {
                    s.append("#").append(a.getAuftragId()).append("  → ").append(a.getNachOrt())
                            .append("  ·  ").append(new java.util.Date(a.getFaelligMs())).append("\n");
                }
            }
        }
        return s.length() == 0 ? "Keine offenen Aufträge." : s.toString().trim();
    }

    private static String escapeHtml(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void stilKnopf(javax.swing.JButton b) {
        b.setBackground(new Color(0x13262F));
        b.setForeground(KartenFarben.TEXT);
        b.setBorder(BorderFactory.createLineBorder(new Color(0x1E3440)));
        b.setFocusPainted(false);
    }

    private static String ersteZeile(Throwable t) {
        String s = t.getMessage() != null ? t.getMessage() : t.toString();
        int i = s.indexOf('\n');
        return i > 0 ? s.substring(0, i) : s;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new LogistikApp().start();
            }
        });
    }
}
