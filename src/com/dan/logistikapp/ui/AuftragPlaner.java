package com.dan.logistikapp.ui;

import com.dan.logistikapp.geo.Geodaesie;
import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.karte.KartenModell;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.Iso6346;
import com.dan.logistikapp.model.Land;
import com.dan.logistikapp.model.Ort;
import com.dan.logistikapp.model.Regeln;
import com.dan.logistikapp.model.Tarif;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Inhalt des Dialogs "Auftrag planen": Ziel, Termin (Minuten ab jetzt,
 * mit Schnellwahl) und eine Vorschau mit Kosten, Zoll und Platz am Ziel -
 * dieselbe Rechnung wie das Etikett beim Ziehen.
 *
 * <p>Ohne Fenster benutzbar: die Tests setzen Ziel und Minuten direkt.</p>
 *
 * @author Dan
 */
public final class AuftragPlaner extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Color GRUND = new Color(0x0C1A23);
    private static final Color FELD = new Color(0x13262F);

    private final KartenModell modell;
    private final ContainerInfo container;
    private final List<Integer> zielIds = new ArrayList<Integer>();
    private final JComboBox<String> ziel = new JComboBox<String>();
    private final JSpinner minuten = new JSpinner(new SpinnerNumberModel(5, 0, 7 * 24 * 60, 1));
    private final JLabel vorschau = new JLabel(" ");

    /**
     * @param zielOrtId vorgew&auml;hltes Ziel (Shift+Ziehen), -1 = erste andere Stadt
     */
    public AuftragPlaner(KartenModell modell, ContainerInfo c, int zielOrtId) {
        super(new BorderLayout(0, 12));
        this.modell = modell;
        this.container = c;
        setBackground(GRUND);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));

        JLabel titel = new JLabel(Iso6346.anzeige(c.getKennung()) + "  ·  " + c.getWare() + "  ·  steht in " + c.getOrt());
        titel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        titel.setForeground(KartenFarben.TEXT);
        add(titel, BorderLayout.NORTH);

        for (KartenModell.OrtPunkt p : modell.getPunkte()) {
            if (p.getOrt().getOrtId() != c.getOrtId()) {
                zielIds.add(p.getOrt().getOrtId());
                ziel.addItem(p.getOrt().getName() + "  (" + p.getOrt().getZone().anzeige() + ")");
            }
        }
        int vor = zielIds.indexOf(zielOrtId);
        ziel.setSelectedIndex(vor >= 0 ? vor : 0);

        JPanel mitte = new JPanel(new GridBagLayout());
        mitte.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 0, 4, 10);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        g.gridy = 0;
        mitte.add(etikett("Ziel"), g);
        g.gridx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        mitte.add(ziel, g);
        g.gridx = 0;
        g.gridy = 1;
        g.fill = GridBagConstraints.NONE;
        g.weightx = 0;
        mitte.add(etikett("Termin"), g);
        JPanel termin = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        termin.setOpaque(false);
        minuten.setPreferredSize(new Dimension(70, minuten.getPreferredSize().height));
        termin.add(minuten);
        termin.add(etikett("min ab jetzt"));
        for (final int[] s : new int[][] {{0}, {5}, {15}, {60}}) {
            JButton b = new JButton(s[0] == 0 ? "sofort" : s[0] == 60 ? "+1 h" : "+" + s[0]);
            b.setFocusable(false);
            b.setBackground(FELD);
            b.setForeground(KartenFarben.TEXT);
            b.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    setMinuten(s[0]);
                }
            });
            termin.add(b);
        }
        g.gridx = 1;
        mitte.add(termin, g);
        add(mitte, BorderLayout.CENTER);

        vorschau.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        add(vorschau, BorderLayout.SOUTH);

        ziel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                aktualisiere();
            }
        });
        minuten.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                aktualisiere();
            }
        });
        aktualisiere();
    }

    private static JLabel etikett(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(KartenFarben.TEXT_LEISE);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return l;
    }

    public int getContainerId() {
        return container.getContainerId();
    }

    public int getZielOrtId() {
        return zielIds.isEmpty() ? -1 : zielIds.get(ziel.getSelectedIndex());
    }

    public void setZiel(int ortId) {
        int i = zielIds.indexOf(ortId);
        if (i >= 0) {
            ziel.setSelectedIndex(i);
        }
    }

    public int getMinuten() {
        return ((Number) minuten.getValue()).intValue();
    }

    public void setMinuten(int m) {
        minuten.setValue(m);
    }

    /** Termin in Millisekunden, gerechnet ab {@code jetztMs}. */
    public long faelligMs(long jetztMs) {
        return jetztMs + getMinuten() * 60000L;
    }

    public String getVorschau() {
        return vorschau.getText();
    }

    /** Kosten, Zoll, Platz am Ziel - wie das Etikett beim Ziehen. */
    private void aktualisiere() {
        KartenModell.OrtPunkt v = modell.punkt(container.getOrtId());
        KartenModell.OrtPunkt z = modell.punkt(getZielOrtId());
        if (v == null || z == null) {
            vorschau.setText(" ");
            return;
        }
        Ort a = v.getOrt();
        Ort b = z.getOrt();
        Land la = modell.land(a.getIso2());
        Land lb = modell.land(b.getIso2());
        boolean zoll = la != null && lb != null && Regeln.zollNoetig(la, lb);
        long m = Math.round(Geodaesie.entfernungM(a.getLaenge(), a.getBreite(), b.getLaenge(), b.getBreite()));
        Tarif t = modell.getTarif();
        java.math.BigDecimal k = t == null ? null : t.vorschau(m, container.getTeu(), container.getWareCode(), zoll);
        Integer frei = modell.freiTeu(b.getOrtId());
        StringBuilder s = new StringBuilder();
        s.append(String.format(Locale.GERMANY, "%,.0f km", m / 1000.0));
        if (k != null) {
            s.append(String.format(Locale.GERMANY, "  ·  ≈ %,.2f €", k));
        }
        s.append(zoll ? "  ·  Zoll" : "  ·  zollfrei");
        boolean voll = frei != null && frei < container.getTeu();
        if (frei != null) {
            s.append(voll ? "  ·  Ziel heute voll — würde blockieren" : "  ·  frei " + frei + " TEU");
        }
        s.append("  ·  fällig ").append(getMinuten() == 0 ? "sofort"
                : "um " + AuftragsTafel.uhr(faelligMs(System.currentTimeMillis())));
        if ("ZOLL".equals(container.getStatus())) {
            s.append("  ·  steht beim Zoll: erst nach der Freigabe");
        }
        vorschau.setText(s.toString());
        vorschau.setForeground(voll ? new Color(0xE0524A) : zoll ? new Color(0xE8A33C) : KartenFarben.TEXT);
    }
}
