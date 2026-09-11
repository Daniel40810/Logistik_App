package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.model.Ware;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Kompakter Filter f&uuml;r die Containerdarstellung auf der Karte. */
public final class KartenFilter extends JPanel {

    private static final long serialVersionUID = 1L;
    public static final String ALLE = "ALLE";
    public static final String ZOLL = "ZOLL";
    private static final Color GRUND = new Color(0x0A1B26);
    private static final Color FELD = new Color(0x13262F);

    private final JComboBox<Option> auswahl = new JComboBox<Option>();
    private final List<Option> warenOptionen = new ArrayList<Option>();
    private FilterAktion aktion;

    public interface FilterAktion {
        void filter(String wert);
    }

    private static final class Option {
        private final String wert;
        private final String text;

        Option(String wert, String text) {
            this.wert = wert;
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public KartenFilter() {
        super(new BorderLayout(8, 0));
        setOpaque(true);
        setBackground(GRUND);
        setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        setPreferredSize(new Dimension(SeitenLeisteBreite(), 40));

        JLabel label = new JLabel("Karte filtern");
        label.setForeground(KartenFarben.TEXT_LEISE);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        add(label, BorderLayout.WEST);

        auswahl.setUI(new StilComboBoxUI());
        auswahl.setOpaque(true);
        auswahl.setBackground(FELD);
        auswahl.setForeground(KartenFarben.TEXT);
        auswahl.setBorder(BorderFactory.createLineBorder(new Color(0x1E3440)));
        auswahl.setModel(new DefaultComboBoxModel<Option>(new Option[] {
            new Option(ALLE, "Alle Container"),
            new Option(ZOLL, "Nur beim Zoll")
        }));
        auswahl.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (aktion != null && auswahl.getSelectedItem() != null) {
                    aktion.filter(((Option) auswahl.getSelectedItem()).wert);
                }
            }
        });
        add(auswahl, BorderLayout.CENTER);
    }

    private static int SeitenLeisteBreite() {
        return ZollTafel.BREITE - 24;
    }

    public void setAktion(FilterAktion a) {
        this.aktion = a;
    }

    public void setWaren(List<Ware> waren) {
        String vorher = wert();
        warenOptionen.clear();
        if (waren != null) {
            for (Ware w : waren) {
                warenOptionen.add(new Option("WARE:" + w.getCode(), "Nur " + w.getName()));
            }
        }
        DefaultComboBoxModel<Option> model = new DefaultComboBoxModel<Option>();
        model.addElement(new Option(ALLE, "Alle Container"));
        model.addElement(new Option(ZOLL, "Nur beim Zoll"));
        for (Option o : warenOptionen) {
            model.addElement(o);
        }
        auswahl.setModel(model);
        for (int i = 0; i < model.getSize(); i++) {
            if (model.getElementAt(i).wert.equals(vorher)) {
                auswahl.setSelectedIndex(i);
                return;
            }
        }
        auswahl.setSelectedIndex(0);
    }

    public String wert() {
        Object o = auswahl.getSelectedItem();
        return o instanceof Option ? ((Option) o).wert : ALLE;
    }
}
