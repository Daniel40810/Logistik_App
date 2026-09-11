package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.KartenFarben;
import com.dan.logistikapp.karte.KartenModell;
import com.dan.logistikapp.model.ContainerAnlage;
import com.dan.logistikapp.model.Ort;
import com.dan.logistikapp.model.Ware;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Eingaben f&uuml;r einen neuen Container. */
public final class ContainerAnleger extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Color GRUND = new Color(0x0C1A23);
    private static final Color FELD = new Color(0x13262F);

    private final JComboBox<Integer> groesse = new JComboBox<Integer>(new Integer[] {20, 40});
    private final JComboBox<Ware> ware = new JComboBox<Ware>();
    private final JComboBox<Ort> ort = new JComboBox<Ort>();
    private final JTextField ortSuche = new JTextField();
    private final List<Ort> alleOrte = new ArrayList<Ort>();
    private final KartenModell modell;
    private final JLabel meldung = new JLabel(" ");
    private boolean filterLaeuft;

    public ContainerAnleger(KartenModell modell) {
        super(new BorderLayout(0, 12));
        this.modell = modell;
        setBackground(GRUND);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        setPreferredSize(new java.awt.Dimension(540, 260));

        JLabel titel = new JLabel("Neuen Container anlegen");
        titel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        titel.setForeground(KartenFarben.TEXT);
        add(titel, BorderLayout.NORTH);

        for (Ware w : modell.getWaren()) {
            ware.addItem(w);
        }
        for (KartenModell.OrtPunkt p : modell.getPunkte()) {
            alleOrte.add(p.getOrt());
        }
        filterOrte();

        groesse.setUI(new StilComboBoxUI());
        ware.setUI(new StilComboBoxUI());
        ort.setUI(new StilComboBoxUI());
        style(groesse);
        style(ware);
        style(ort);
        style(ortSuche);
        ortSuche.setToolTipText("Nach Stadt oder Länderkürzel filtern");
        ware.setRenderer(renderer());
        ort.setRenderer(renderer());

        ortSuche.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterOrte();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterOrte();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterOrte();
            }
        });

        JPanel felder = new JPanel(new GridBagLayout());
        felder.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 0, 5, 10);
        g.anchor = GridBagConstraints.WEST;
        addZeile(felder, g, 0, "Größe", groesse);
        addZeile(felder, g, 1, "Ware", ware);
        addZeile(felder, g, 2, "Suche", ortSuche);
        addZeile(felder, g, 3, "Standort", ort);
        add(felder, BorderLayout.CENTER);

        meldung.setForeground(KartenFarben.TEXT_LEISE);
        meldung.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        meldung.setText("Die Kennung wird automatisch nach ISO 6346 vergeben.");
        add(meldung, BorderLayout.SOUTH);
    }

    private static void style(javax.swing.JComponent c) {
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
                if (value instanceof Ware) {
                    Ware w = (Ware) value;
                    setText(w.getName() + "  (" + w.getCode() + ")");
                } else if (value instanceof Ort) {
                    Ort o = (Ort) value;
                    setText(o.getName() + "  (" + o.getIso2() + ")");
                }
                setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                return this;
            }
        };
    }

    private void filterOrte() {
        if (filterLaeuft) {
            return;
        }
        filterLaeuft = true;
        try {
            String suche = ortSuche == null ? "" : ortSuche.getText().trim().toLowerCase();
            Ort vorher = (Ort) ort.getSelectedItem();
            ort.removeAllItems();
            for (Ort o : alleOrte) {
                String text = (o.getName() + " " + o.getIso2()).toLowerCase();
                if (suche.isEmpty() || text.contains(suche)) {
                    ort.addItem(o);
                }
            }
            if (vorher != null) {
                ort.setSelectedItem(vorher);
            }
            if (ort.getSelectedIndex() < 0 && ort.getItemCount() > 0) {
                ort.setSelectedIndex(0);
            }
        } finally {
            filterLaeuft = false;
        }
    }

    private static void addZeile(JPanel panel, GridBagConstraints g, int y, String text,
            javax.swing.JComponent feld) {
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

    public ContainerAnlage getAnlage() {
        Ware w = (Ware) ware.getSelectedItem();
        Ort o = (Ort) ort.getSelectedItem();
        if (w == null) {
            return null;
        }
        if (o == null) {
            return null;
        }
        return new ContainerAnlage(null, ((Integer) groesse.getSelectedItem()).intValue(),
                w.getCode(), o.getOrtId());
    }

    /** Warnung, wenn der neue Container den Stellplatz rechnerisch überfüllt. */
    public String getKapazitaetswarnung() {
        Ort o = (Ort) ort.getSelectedItem();
        Integer frei = o == null || modell == null ? null : modell.freiTeu(o.getOrtId());
        int teu = groesse.getSelectedItem() == null ? 0 : ((Integer) groesse.getSelectedItem()).intValue() == 40 ? 2 : 1;
        if (frei != null && frei < teu) {
            return o.getName() + " hat nur noch " + Math.max(0, frei) + " TEU frei, der Container braucht " + teu + " TEU.";
        }
        return null;
    }

    public void setFehler(String text) {
        meldung.setForeground(new Color(0xE6A36B));
        meldung.setText(text == null || text.isEmpty() ? " " : text);
    }

    public void setGroesseFuss(int fuss) {
        groesse.setSelectedItem(Integer.valueOf(fuss));
    }

    public void setWareCode(String code) {
        for (int i = 0; i < ware.getItemCount(); i++) {
            if (ware.getItemAt(i).getCode().equals(code)) {
                ware.setSelectedIndex(i);
                return;
            }
        }
    }

    public void setOrtId(int ortId) {
        for (Ort o : alleOrte) {
            if (o.getOrtId() == ortId) {
                ortSuche.setText("");
                ort.setSelectedItem(o);
                return;
            }
        }
    }

    public void setStandortSuche(String text) {
        ortSuche.setText(text == null ? "" : text);
    }
}
