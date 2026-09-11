package com.dan.logistikapp.dienst;

/**
 * Ein abgewiesener Auftrag, &uuml;bersetzt aus der ORA-Nummer von LOG_API.
 *
 * @author Dan
 */
public final class DienstFehler extends Exception {

    private static final long serialVersionUID = 1L;

    /** Was schiefging - die Oberfl&auml;che reagiert darauf, nicht auf Texte. */
    public enum Art {
        SCHON_DA(20020), BEIM_ZOLL(20021), CONTAINER_UNBEKANNT(20022), ORT_UNBEKANNT(20023),
        NICHTS_FREIZUGEBEN(20024), ZIEL_VOLL(20026), AUFTRAG_UNBEKANNT(20027),
        AUFTRAG_NICHT_OFFEN(20028),
        CONTAINER_GROESSE(20030), WARE_UNBEKANNT(20031),
        ORT_NAME(20032), LAND_UNBEKANNT(20033), KOORDINATEN(20034), ORT_KAPAZITAET(20035),
        /** Ein Auftrag ist nicht gefahren (beim Zoll, Ziel voll, wartet) - kein ORA-Fehler. */
        AUFTRAG_NICHT_GEFAHREN(-1),
        DATENBANK(0);

        private final int ora;

        Art(int ora) {
            this.ora = ora;
        }

        public int getOra() {
            return ora;
        }

        public static Art ausOra(int code) {
            for (Art a : values()) {
                if (a.ora == code && code != 0) {
                    return a;
                }
            }
            return DATENBANK;
        }
    }

    private final Art art;

    public DienstFehler(Art art, String meldung, Throwable ursache) {
        super(meldung, ursache);
        this.art = art;
    }

    public Art getArt() {
        return art;
    }
}
