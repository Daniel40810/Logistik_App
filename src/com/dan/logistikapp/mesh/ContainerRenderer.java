package com.dan.logistikapp.mesh;

import com.dan.rayphong.Mat4;
import com.dan.rayphong.PhongMaterial;
import com.dan.rayphong.PhongShader;
import com.dan.rayphong.PointLight;
import com.dan.rayphong.Rasterizer;
import com.dan.rayphong.Vec3;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Rendert einen Container mit dem RayPhong-Kern aus FStyle.jar in ein Bild
 * mit durchsichtigem Grund.
 *
 * <p>Blick: orthographisch (keine Fluchtpunkte - auf einer Karte soll ein
 * Container im Norden so gro&szlig; sein wie einer im S&uuml;den), von S&uuml;den
 * unter {@value #ERHEBUNG_GRAD}&deg; von oben. Gedreht wird der Container,
 * nicht die Kamera: so bleibt das Licht &uuml;berall auf der Karte gleich -
 * aus Nordwesten, wie es Kartographen seit jeher legen.</p>
 *
 * <p>RayPhong kennt keine Kantengl&auml;ttung. Deshalb wird dreifach
 * &uuml;berabgetastet und fl&auml;chengemittelt verkleinert.</p>
 *
 * @author Dan
 */
public final class ContainerRenderer {

    public static final double ERHEBUNG_GRAD = 52;
    private static final int UEBERABTASTUNG = 3;

    private static final Color STAHL = new Color(0x6B7176);
    private static final Color AGGREGAT = new Color(0x3A3F44);

    private ContainerRenderer() {
    }

    /**
     * @param farbe         Warenfarbe (Wand)
     * @param fuss          20 oder 40
     * @param reefer        K&uuml;hlcontainer mit Aggregat an der Stirnseite
     * @param gierGrad      Drehung um die Hochachse; 0 = L&auml;nge von West nach
     *                      Ost, T&uuml;ren im Osten; positiv gegen den Uhrzeigersinn
     * @param pixelProMeter Aufl&ouml;sung des fertigen Bildes
     */
    public static ContainerSprite render(Color farbe, int fuss, boolean reefer,
            double gierGrad, double pixelProMeter) {
        float l = ContainerMesh.laenge(fuss);
        float hb = ContainerMesh.BREITE / 2;
        float h = ContainerMesh.HOEHE;
        double el = Math.toRadians(ERHEBUNG_GRAD);
        float r = (float) Math.sqrt((l / 2) * (l / 2) + hb * hb) + 0.25f;
        // Bildausschnitt: Grundkreis vorn/hinten um r*sin(el), Hoehe dazu h*cos(el)
        float oben = (float) (r * Math.sin(el) + h * Math.cos(el)) + 0.15f;
        float unten = (float) (-r * Math.sin(el)) - 0.15f;
        double ppmGross = pixelProMeter * UEBERABTASTUNG;
        int w = (int) Math.ceil(2 * r * ppmGross);
        int hh = (int) Math.ceil((oben - unten) * ppmGross);

        float d = 60f;
        Vec3 auge = new Vec3(0, (float) (Math.sin(el) * d), (float) (Math.cos(el) * d));
        Mat4 view = Mat4.lookAt(auge, new Vec3(0, 0, 0), new Vec3(0, 1, 0));
        Mat4 proj = Mat4.orthographic(-r, r, unten, oben, 1f, 200f);
        Mat4 model = Mat4.rotationY((float) Math.toRadians(gierGrad));

        // Hauptlicht aus Nordwesten, hoch; die Kamera steht im Sueden (+z).
        // Dazu ein schwaches, leicht blaues Fuelllicht von Suedosten - sonst
        // waeren die zur Kamera gewandten Seiten fast schwarz, und gerade die
        // tragen die Warenfarbe.
        List<PointLight> licht = java.util.Arrays.asList(
                new PointLight(new Vec3(-70, 110, -60), Color.WHITE, 1.0f, 1f, 0f, 0f),
                new PointLight(new Vec3(60, 45, 90), new Color(0xC8D8E8), 0.85f, 1f, 0f, 0f));

        BufferedImage gross = new BufferedImage(w, hh, BufferedImage.TYPE_INT_ARGB);
        float[] z = new float[w * hh];
        Rasterizer.clearZBuffer(z);
        for (ContainerMesh.Teil t : ContainerMesh.baue(fuss, reefer)) {
            PhongShader s = new PhongShader(material(t.rolle, farbe), auge, licht,
                    new Color(0x9FB5BF), 0.5f);
            Rasterizer.render(t.mesh, model, view, proj, gross, z, s);
        }

        BufferedImage klein = verkleinern(gross, UEBERABTASTUNG);
        double ax = (r / (2.0 * r)) * klein.getWidth();
        double ay = (oben / (oben - unten)) * klein.getHeight();
        return new ContainerSprite(klein, ax, ay, pixelProMeter);
    }

    private static PhongMaterial material(ContainerMesh.Rolle rolle, Color ware) {
        switch (rolle) {
            case WAND:
                return new PhongMaterial(ware, Color.WHITE, 0.55f, 0.75f, 0.18f, 18f);
            case RAHMEN:
                return new PhongMaterial(dunkler(ware, 0.78), Color.WHITE, 0.55f, 0.75f, 0.12f, 14f);
            case STAHL:
                return new PhongMaterial(STAHL, Color.WHITE, 0.55f, 0.7f, 0.45f, 40f);
            default:
                return new PhongMaterial(AGGREGAT, Color.WHITE, 0.5f, 0.7f, 0.25f, 20f);
        }
    }

    static Color dunkler(Color c, double f) {
        return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f));
    }

    /**
     * Fl&auml;chenmittel &uuml;ber n&times;n Bildpunkte, mit vorab multipliziertem
     * Alpha - sonst bekommen die R&auml;nder einen dunklen Saum aus dem
     * schwarzen Grund.
     */
    static BufferedImage verkleinern(BufferedImage src, int n) {
        int w = src.getWidth() / n;
        int h = src.getHeight() / n;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] zeile = new int[src.getWidth() * n];
        for (int y = 0; y < h; y++) {
            src.getRGB(0, y * n, src.getWidth(), n, zeile, 0, src.getWidth());
            for (int x = 0; x < w; x++) {
                long a = 0;
                long rr = 0;
                long gg = 0;
                long bb = 0;
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < n; i++) {
                        int p = zeile[j * src.getWidth() + x * n + i];
                        int pa = (p >>> 24) & 0xFF;
                        a += pa;
                        rr += ((p >> 16) & 0xFF) * pa;
                        gg += ((p >> 8) & 0xFF) * pa;
                        bb += (p & 0xFF) * pa;
                    }
                }
                int argb = 0;
                if (a > 0) {
                    int oa = (int) (a / (n * n));
                    argb = (oa << 24) | ((int) (rr / a) << 16) | ((int) (gg / a) << 8) | (int) (bb / a);
                }
                out.setRGB(x, y, argb);
            }
        }
        return out;
    }

    /** F&uuml;r Vorschaubilder: Sprite auf einfarbigen Grund setzen. */
    public static BufferedImage aufGrund(ContainerSprite s, Color grund) {
        BufferedImage b = new BufferedImage(s.getBild().getWidth(), s.getBild().getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = b.createGraphics();
        g.setColor(grund);
        g.fillRect(0, 0, b.getWidth(), b.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(s.getBild(), 0, 0, null);
        g.dispose();
        return b;
    }
}
