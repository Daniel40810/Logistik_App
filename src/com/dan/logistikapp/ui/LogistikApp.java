package com.dan.logistikapp.ui;

import com.dan.fdal.FDatabaseManager;
import com.dan.fframe.FFrame;
import com.dan.logistikapp.db.DbContainerDienst;
import com.dan.logistikapp.db.DbKartenQuelle;
import com.dan.logistikapp.db.LogDb;
import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.karte.KartenModell;
import com.dan.logistikapp.karte.KartenPanel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutionException;
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
                leitstand.stoppeHintergrund();
                if (db != null) {
                    db.close();
                }
            }
        });
        AppIcon.install(frame);
        frame.setPreferredFrameSize(new Dimension(1560, 900));
        frame.setLocationRelativeTo(null);
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
