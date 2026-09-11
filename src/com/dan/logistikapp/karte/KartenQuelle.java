package com.dan.logistikapp.karte;

import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.Land;
import com.dan.logistikapp.model.Ort;
import com.dan.logistikapp.model.Ware;

import java.util.List;

/**
 * Woher die Karte ihre Daten bekommt. Die Karte selbst kennt keine
 * Datenbank - so l&auml;sst sie sich auch aus einer Datei oder im Test f&uuml;llen.
 *
 * @author Dan
 */
public interface KartenQuelle {

    List<Land> laender();

    List<Ort> orte();

    /** Warenarten f&uuml;r die Legende, in ihrer Sortierung. */
    List<Ware> waren();

    /** Alle Container mit Standort. */
    List<ContainerInfo> container();

    /** Tarife f&uuml;r die Kostenvorschau (Runde 2); {@code null} = keine Kosten zeigen. */
    default com.dan.logistikapp.model.Tarif tarif() {
        return null;
    }
}
