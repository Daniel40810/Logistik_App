package com.dan.logistikapp.mesh;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vorgerenderte Containerbilder, einmal je Farbe, Gr&ouml;&szlig;e, Bauart und
 * Drehstufe. Danach kostet ein Container auf der Karte nur noch ein
 * {@code drawImage}, egal wie viele es sind.
 *
 * <p>Die Drehung wird auf 16 Stufen zu 22,5&deg; gerundet - fein genug,
 * dass ein Container in Phase 4 seiner Route folgen kann, grob genug, dass
 * der Speicher &uuml;berschaubar bleibt.</p>
 *
 * <p>Threadsicher: das Vorw&auml;rmen l&auml;uft im Hintergrund, gezeichnet wird
 * auf dem EDT.</p>
 *
 * @author Dan
 */
public final class SpriteCache {

    public static final int STUFEN = 16;

    private final double pixelProMeter;
    private final Map<String, ContainerSprite> bilder = new ConcurrentHashMap<String, ContainerSprite>();

    /** @param pixelProMeter Aufl&ouml;sung, in der die Bilder abgelegt werden */
    public SpriteCache(double pixelProMeter) {
        this.pixelProMeter = pixelProMeter;
    }

    public double getPixelProMeter() {
        return pixelProMeter;
    }

    /** Rundet einen Winkel auf die n&auml;chste Drehstufe (0..15). */
    public static int stufe(double gierGrad) {
        double s = gierGrad / (360.0 / STUFEN);
        int i = (int) Math.round(s) % STUFEN;
        return i < 0 ? i + STUFEN : i;
    }

    public ContainerSprite get(Color farbe, int fuss, boolean reefer, int stufe) {
        String k = farbe.getRGB() + "/" + fuss + "/" + reefer + "/" + stufe;
        ContainerSprite s = bilder.get(k);
        if (s == null) {
            s = ContainerRenderer.render(farbe, fuss, reefer, stufe * 360.0 / STUFEN, pixelProMeter);
            bilder.put(k, s);
        }
        return s;
    }

    /** Nur zum Nachsehen: ist das Bild schon da? */
    public boolean hat(Color farbe, int fuss, boolean reefer, int stufe) {
        return bilder.containsKey(farbe.getRGB() + "/" + fuss + "/" + reefer + "/" + stufe);
    }

    public int groesse() {
        return bilder.size();
    }
}
