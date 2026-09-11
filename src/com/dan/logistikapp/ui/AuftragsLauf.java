package com.dan.logistikapp.ui;

import com.dan.logistikapp.karte.KartenPanel;
import com.dan.logistikapp.model.Auftrag;
import com.dan.logistikapp.model.ContainerInfo;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Timer;

/**
 * F&auml;hrt f&auml;llige Auftr&auml;ge, solange die App l&auml;uft - sichtbar, mit
 * derselben Animation wie nach dem Ziehen. Ein Auftrag ist dran, wenn er
 * offen und f&auml;llig ist und als erster seines Containers kommt
 * (Terminfolge, {@link Auftrag#istDran(long)}).
 *
 * <p>Kann die Karte ihn nicht fahren (beim Zoll, Ziel voll, steht schon
 * dort), fragt sie trotzdem LOG_API - still, h&ouml;chstens einmal je
 * Wiederholzeit. So stehen Versuch und Grund am Auftrag, genau wie beim Job.
 * Doppelt gefahren wird nie: LOG_API sperrt die Auftragszeile.</p>
 *
 * <p>W&auml;hrend der Demo ruht der Lauf; &uuml;berf&auml;llige Auftr&auml;ge f&auml;ngt
 * dann nach der Karenz der Job in der Datenbank auf.</p>
 *
 * @author Dan
 */
public final class AuftragsLauf {

    private final KartenPanel karte;
    private final Leitstand leitstand;
    private final Timer takt;
    private List<Auftrag> liste = Collections.emptyList();
    private final Map<Long, Long> versucht = new HashMap<Long, Long>();
    private long wiederholenMs = 60000;

    AuftragsLauf(KartenPanel karte, Leitstand leitstand) {
        this.karte = karte;
        this.leitstand = leitstand;
        this.takt = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tick();
            }
        });
    }

    public void start() {
        takt.start();
    }

    public void stopp() {
        takt.stop();
    }

    public boolean laeuft() {
        return takt.isRunning();
    }

    void setAuftraege(List<Auftrag> l) {
        liste = l == null ? Collections.<Auftrag>emptyList() : l;
    }

    /** Wie oft ein blockierter Auftrag erneut versucht wird (Standard 60 s). */
    public void setWiederholenMs(long ms) {
        wiederholenMs = ms;
    }

    /** Ein Takt: Countdown neu zeichnen, dann h&ouml;chstens einen Auftrag anfassen. */
    public void tick() {
        karte.repaint();
        leitstand.getAuftragsTafel().repaint();
        if (leitstand.getDemo().laeuft() || karte.istZeitreise() || karte.getContainerDienst() == null
                || karte.getModell() == null
                || !karte.istRuhig() || !leitstand.istRuhig()) {
            return;
        }
        long jetzt = System.currentTimeMillis();
        for (Auftrag a : liste) {
            if (!a.istDran(jetzt)) {
                continue;
            }
            Long t = versucht.get(a.getAuftragId());
            if (t != null && jetzt - t < wiederholenMs) {
                continue;
            }
            versucht.put(a.getAuftragId(), jetzt);
            ContainerInfo c = karte.container(a.getContainerId());
            if (c == null) {
                continue;
            }
            Integer frei = karte.getModell().freiTeu(a.getNachOrtId());
            boolean faehrt = "BEREIT".equals(c.getStatus()) && c.getOrtId() != a.getNachOrtId()
                    && (frei == null || frei >= c.getTeu());
            if (faehrt && karte.fahreAuftrag(a)) {
                return;
            }
            leitstand.ausfuehrenStill(a.getAuftragId());
            return;
        }
    }
}
