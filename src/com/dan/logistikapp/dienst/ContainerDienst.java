package com.dan.logistikapp.dienst;

import com.dan.logistikapp.model.Bewegung;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.ContainerAnlage;
import com.dan.logistikapp.model.Ort;
import com.dan.logistikapp.model.OrtAnlage;
import com.dan.logistikapp.model.ZollFall;

import java.util.List;

/**
 * Was die Karte schreibend braucht. Die Datenbank-Umsetzung ruft LOG_API;
 * f&uuml;r Tests ohne Datenbank gibt es eine im Speicher.
 *
 * <p>Alle Methoden d&uuml;rfen lange dauern und werden deshalb nie auf dem EDT
 * aufgerufen.</p>
 *
 * @author Dan
 */
public interface ContainerDienst {

    /** Legt einen neuen Container am gew&auml;hlten Standort an. */
    ContainerInfo containerAnlegen(ContainerAnlage anlage) throws DienstFehler;

    /** Legt eine neue Stadt am gewählten Land und Standort an. */
    Ort ortAnlegen(OrtAnlage anlage) throws DienstFehler;

    /** Der aktuelle Stand aller Städte. */
    List<Ort> orte() throws DienstFehler;

    /** Verschiebt einen Container. Wirft bei allem, was LOG_API ablehnt. */
    Fahrt verschieben(int containerId, int nachOrtId) throws DienstFehler;

    /** Gibt einen Container frei, der beim Zoll steht. */
    void zollFreigeben(int containerId) throws DienstFehler;

    /** Der aktuelle Stand aller Container. */
    List<ContainerInfo> container() throws DienstFehler;

    /** Wer beim Zoll wartet, die am l&auml;ngsten Wartenden zuerst. */
    List<ZollFall> zollFaelle() throws DienstFehler;

    /** Die j&uuml;ngsten Fahrten aller Container, die neueste zuerst. */
    List<Bewegung> letzteBewegungen(int max) throws DienstFehler;

    /** Alle Fahrten aller Container, die &auml;lteste zuerst - f&uuml;r die Zeitreise. */
    List<Bewegung> alleBewegungen() throws DienstFehler;

    /** Alle Fahrten eines Containers, die &auml;lteste zuerst. */
    List<Bewegung> historie(int containerId) throws DienstFehler;

    /** Kosten je Tag, Zonenpaar und Ware (wie LOG_KOSTEN_V). */
    List<com.dan.logistikapp.model.KostenZeile> kosten() throws DienstFehler;

    /** Offene Auftr&auml;ge nach Termin, dazu die zuletzt abgeschlossenen. */
    List<com.dan.logistikapp.model.Auftrag> auftraege() throws DienstFehler;

    /** Plant eine Fahrt; ein Termin in der Vergangenheit hei&szlig;t: sofort f&auml;llig. */
    long auftragAnlegen(int containerId, int nachOrtId, long faelligMs) throws DienstFehler;

    void auftragStornieren(long auftragId) throws DienstFehler;

    /** F&uuml;hrt einen Auftrag aus, wenn er dran ist (Terminfolge, Zoll, Kapazit&auml;t wie LOG_API). */
    AuftragErgebnis auftragAusfuehren(long auftragId) throws DienstFehler;

    /** &Auml;ndert sich bei jeder Fahrt, Freigabe und Auftrags&auml;nderung - auch fremder Sitzungen. */
    String standMarke() throws DienstFehler;
}
