package com.dan.logistikapp.dienst;

/**
 * Was beim Ausf&uuml;hren eines Auftrags herauskam - so wie
 * LOG_API.auftrag_ausfuehren es meldet.
 *
 * @author Dan
 */
public final class AuftragErgebnis {

    /** ERLEDIGT, BLOCKIERT, GESCHEITERT, WARTET, NICHT_FAELLIG oder GESPERRT. */
    private final String ergebnis;
    private final Fahrt fahrt;
    private final String grund;

    public AuftragErgebnis(String ergebnis, Fahrt fahrt, String grund) {
        this.ergebnis = ergebnis;
        this.fahrt = fahrt;
        this.grund = grund;
    }

    public String getErgebnis() {
        return ergebnis;
    }

    /** Die gefahrene Strecke, {@code null} ohne Fahrt (auch: stand schon am Ziel). */
    public Fahrt getFahrt() {
        return fahrt;
    }

    /** Warum nicht gefahren wurde (Zoll, Ziel voll, ...), sonst {@code null}. */
    public String getGrund() {
        return grund;
    }

    public boolean istGefahren() {
        return "ERLEDIGT".equals(ergebnis) && fahrt != null;
    }

    @Override
    public String toString() {
        return ergebnis + (grund != null ? ": " + grund : "");
    }
}
