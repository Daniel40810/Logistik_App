package com.dan.logistikapp.model;

import java.awt.Color;

/**
 * Eine geplante Fahrt aus LOG_AUFTRAG_V.
 *
 * <p>{@link #getRang()} ist der Platz in der Kette des Containers: nur
 * Rang 1 eines offenen Auftrags ist ausf&uuml;hrbar (Terminfolge).</p>
 *
 * @author Dan
 */
public final class Auftrag {

    /** Status wie in LOG_AUFTRAG. */
    public enum Status { OFFEN, ERLEDIGT, STORNIERT, GESCHEITERT }

    private final long auftragId;
    private final int containerId;
    private final String kennungAnzeige;
    private final String ware;
    private final Color farbe;
    private final int ortJetztId;
    private final String ortJetzt;
    private final String containerStatus;
    private final int nachOrtId;
    private final String nachOrt;
    private final long faelligMs;
    private final Status status;
    private final String grund;
    private final int versuche;
    private final long bewegungId;
    private final String ausgefuehrtVon;
    private final long erledigtMs;
    private final int rang;

    public Auftrag(long auftragId, int containerId, String kennungAnzeige, String ware, Color farbe,
            int ortJetztId, String ortJetzt, String containerStatus, int nachOrtId, String nachOrt,
            long faelligMs, Status status, String grund, int versuche, long bewegungId,
            String ausgefuehrtVon, long erledigtMs, int rang) {
        this.auftragId = auftragId;
        this.containerId = containerId;
        this.kennungAnzeige = kennungAnzeige;
        this.ware = ware;
        this.farbe = farbe;
        this.ortJetztId = ortJetztId;
        this.ortJetzt = ortJetzt;
        this.containerStatus = containerStatus;
        this.nachOrtId = nachOrtId;
        this.nachOrt = nachOrt;
        this.faelligMs = faelligMs;
        this.status = status;
        this.grund = grund;
        this.versuche = versuche;
        this.bewegungId = bewegungId;
        this.ausgefuehrtVon = ausgefuehrtVon;
        this.erledigtMs = erledigtMs;
        this.rang = rang;
    }

    public long getAuftragId() {
        return auftragId;
    }

    public int getContainerId() {
        return containerId;
    }

    public String getKennungAnzeige() {
        return kennungAnzeige;
    }

    public String getWare() {
        return ware;
    }

    public Color getFarbe() {
        return farbe;
    }

    public int getOrtJetztId() {
        return ortJetztId;
    }

    public String getOrtJetzt() {
        return ortJetzt;
    }

    public String getContainerStatus() {
        return containerStatus;
    }

    public int getNachOrtId() {
        return nachOrtId;
    }

    public String getNachOrt() {
        return nachOrt;
    }

    public long getFaelligMs() {
        return faelligMs;
    }

    public Status getStatus() {
        return status;
    }

    /** Warum er (noch) nicht fahren konnte, sonst {@code null}. */
    public String getGrund() {
        return grund;
    }

    public int getVersuche() {
        return versuche;
    }

    /** Die ausgef&uuml;hrte Fahrt, 0 ohne. */
    public long getBewegungId() {
        return bewegungId;
    }

    /** APP oder JOB, {@code null} solange offen. */
    public String getAusgefuehrtVon() {
        return ausgefuehrtVon;
    }

    public long getErledigtMs() {
        return erledigtMs;
    }

    /** Platz in der Kette offener Auftr&auml;ge des Containers, 0 wenn nicht offen. */
    public int getRang() {
        return rang;
    }

    public boolean istOffen() {
        return status == Status.OFFEN;
    }

    /** Offen, Termin erreicht und als N&auml;chster seines Containers dran. */
    public boolean istDran(long jetztMs) {
        return status == Status.OFFEN && rang == 1 && faelligMs <= jetztMs;
    }

    @Override
    public String toString() {
        return "Auftrag " + auftragId + ": " + kennungAnzeige + " -> " + nachOrt + " (" + status + ")";
    }
}
