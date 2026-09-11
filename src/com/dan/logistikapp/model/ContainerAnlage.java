package com.dan.logistikapp.model;

/** Werte, die zum Anlegen eines neuen Containers ben&ouml;tigt werden. */
public final class ContainerAnlage {

    private final String kennung;
    private final int groesseFuss;
    private final String wareCode;
    private final int ortId;

    /**
     * @param kennung ISO-6346-Kennung; leer oder {@code null} = automatisch
     * @param groesseFuss 20 oder 40
     * @param wareCode Code aus {@code LOG_WARE}
     * @param ortId Standort
     */
    public ContainerAnlage(String kennung, int groesseFuss, String wareCode, int ortId) {
        this.kennung = kennung;
        this.groesseFuss = groesseFuss;
        this.wareCode = wareCode;
        this.ortId = ortId;
    }

    public String getKennung() {
        return kennung;
    }

    public int getGroesseFuss() {
        return groesseFuss;
    }

    public String getWareCode() {
        return wareCode;
    }

    public int getOrtId() {
        return ortId;
    }
}
