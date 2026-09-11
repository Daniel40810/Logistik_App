package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.karte.KartenModell;
import com.dan.logistikapp.model.Land;
import com.dan.logistikapp.model.OrtAnlage;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.DefaultListCellRenderer;

/** Eingaben für eine neue Stadt. */
public final class OrtAnleger extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Color GRUND = new Color(0x0C1A23);
    private static final Color FELD = new Color(0x13262F);
    private final JTextField name = new JTextField();
    private final JComboBox<Land> land = new JComboBox<Land>();
    private final JTextField laenge = new JTextField();
    private final JTextField breite = new JTextField();
    private final JTextField kapazitaet = new JTextField();
    private final JCheckBox unbegrenzt = new JCheckBox("unbegrenzt");
    private final JLabel meldung = new JLabel(" ");
    private String letzterFehler = "Bitte alle Stadtangaben prüfen.";

    public OrtAnleger(KartenModell modell) {
        super(new BorderLayout(0, 12));
        setBackground(GRUND);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        setPreferredSize(new java.awt.Dimension(540, 300));

        JLabel titel = new JLabel("Neue Stadt anlegen");
        titel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        titel.setForeground(KartenFarben.TEXT);
        add(titel, BorderLayout.NORTH);

        for (Land l : modell.getLaender()) {
            land.addItem(l);
        }
        land.setUI(new StilComboBoxUI());
        land.setRenderer(renderer());
        style(land);
        style(name);
        style(laenge);
        style(breite);
        style(kapazitaet);
        unbegrenzt.setOpaque(false);
        unbegrenzt.setForeground(KartenFarben.TEXT_LEISE);
        unbegrenzt.setSelected(true);
        kapazitaet.setEnabled(false);
        unbegrenzt.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                kapazitaet.setEnabled(!unbegrenzt.isSelected());
            }
        });

        name.setToolTipText("Zum Beispiel: Leipzig");
        laenge.setText("12.37");
        breite.setText("51.34");
        kapazitaet.setToolTipText("Stellplätze in TEU");

        JPanel felder = new JPanel(new GridBagLayout());
        felder.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 0, 5, 10);
        g.anchor = GridBagConstraints.WEST;
        addZeile(felder, g, 0, "Stadt", name);
        addZeile(felder, g, 1, "Land", land);
        addZeile(felder, g, 2, "Länge", laenge);
        addZeile(felder, g, 3, "Breite", breite);
        addZeile(felder, g, 4, "Kapazität", kapazitaet);
        g.gridx = 1;
        g.gridy = 5;
        g.fill = GridBagConstraints.NONE;
        felder.add(unbegrenzt, g);
        add(felder, BorderLayout.CENTER);

        meldung.setForeground(KartenFarben.TEXT_LEISE);
        meldung.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        meldung.setText("Koordinaten in Grad · Ost/Nord positiv · die Stadt erscheint danach sofort auf der Karte.");
        add(meldung, BorderLayout.SOUTH);
    }

    private static void style(JComponent c) {
        c.setOpaque(true);
        c.setBackground(FELD);
        c.setForeground(KartenFarben.TEXT);
        c.setBorder(BorderFactory.createLineBorder(new Color(0x1E3440)));
    }

    private static ListCellRenderer<Object> renderer() {
        return new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setBackground(selected ? new Color(0x1F5E63) : FELD);
                setForeground(KartenFarben.TEXT);
                if (value instanceof Land) {
                    Land l = (Land) value;
                    setText(l.getIso2() + "  " + l.getName());
                }
                setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                return this;
            }
        };
    }

    private static void addZeile(JPanel panel, GridBagConstraints g, int y, String text, JComponent feld) {
        g.gridx = 0;
        g.gridy = y;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        panel.add(etikett(text), g);
        g.gridx = 1;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        panel.add(feld, g);
    }

    private static JLabel etikett(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(KartenFarben.TEXT_LEISE);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return l;
    }

    /** {@code null}, wenn die Eingabe noch nicht gültig ist. */
    public OrtAnlage getAnlage() {
        letzterFehler = "Bitte alle Stadtangaben prüfen.";
        String n = name.getText().trim();
        if (n.isEmpty()) {
            letzterFehler = "Bitte einen Stadtnamen eingeben.";
            return null;
        }
        try {
            Land l = (Land) land.getSelectedItem();
            if (l == null) {
                letzterFehler = "Bitte ein Land auswählen.";
                return null;
            }
            double lon = Double.parseDouble(laenge.getText().trim().replace(',', '.'));
            double lat = Double.parseDouble(breite.getText().trim().replace(',', '.'));
            if (Double.isNaN(lon) || Double.isInfinite(lon) || Double.isNaN(lat) || Double.isInfinite(lat)
                    || lon < -180 || lon > 180 || lat < -90 || lat > 90) {
                letzterFehler = "Länge muss zwischen -180 und 180, Breite zwischen -90 und 90 liegen.";
                return null;
            }
            Integer k = null;
            if (!unbegrenzt.isSelected()) {
                try {
                    k = Integer.valueOf(kapazitaet.getText().trim());
                } catch (NumberFormatException e) {
                    letzterFehler = "Die Kapazität muss eine ganze Zahl sein.";
                    return null;
                }
                if (k.intValue() <= 0) {
                    letzterFehler = "Die Kapazität muss größer als 0 sein.";
                    return null;
                }
            }
            return new OrtAnlage(n, l.getIso2(), lon, lat, k);
        } catch (NumberFormatException e) {
            letzterFehler = "Länge und Breite müssen Zahlen sein, zum Beispiel 12,37 und 51,34.";
            return null;
        }
    }

    public String getFehler() {
        return letzterFehler;
    }

    public void setFehler(String text) {
        meldung.setForeground(new Color(0xE6A36B));
        meldung.setText(text == null || text.isEmpty() ? " " : text);
    }
}
