package com.dan.logistikapp.model;

/**
 * Die Zollregel aus LOG_API.zoll_noetig, in Java nachgebaut - f&uuml;r die
 * Vorschau beim Ziehen. Verbindlich bleibt die Datenbank; der Durchstich
 * pr&uuml;ft beide Seiten f&uuml;r alle Stadtpaare gegeneinander.
 *
 * @author Dan
 */
public final class Regeln {

    private Regeln() {
    }

    /**
     * Zoll ist n&ouml;tig, wenn die L&auml;nder verschieden sind und mindestens eines
     * au&szlig;erhalb der Zollunion liegt.
     */
    public static boolean zollNoetig(Land von, Land nach) {
        if (von.getIso2().equals(nach.getIso2())) {
            return false;
        }
        return !(von.isZollunion() && nach.isZollunion());
    }
}
