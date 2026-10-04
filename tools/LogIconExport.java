import com.dan.logistikapp.ui.AppIcon;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/**
 * Schreibt das gemalte Programmsymbol als Dateien: icon/logistik.ico (Windows,
 * PNG-Eintr&auml;ge von 16 bis 256 Pixel) und icon/logistik-256.png.
 *
 * <p>Aufruf: {@code java -cp "build/check" LogIconExport [Projektordner]} -
 * oder einfach {@code icon.bat} doppelklicken.</p>
 *
 * @author Dan
 */
public final class LogIconExport {

    private LogIconExport() {
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        Path dir = root.resolve("icon");
        Files.createDirectories(dir);
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        byte[][] png = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            png[i] = png(AppIcon.paint(sizes[i]));
        }
        Files.write(dir.resolve("logistik.ico"), ico(sizes, png));
        Files.write(dir.resolve("logistik-256.png"), png[sizes.length - 1]);
        System.out.println("icon/logistik.ico (" + sizes.length + " Groessen), icon/logistik-256.png");
    }

    static byte[] png(BufferedImage b) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(b, "png", o);
        return o.toByteArray();
    }

    /** ICO-Container: Kopf, Verzeichnis, danach die PNG-Daten (ab Windows Vista lesbar). */
    static byte[] ico(int[] sizes, byte[][] png) throws IOException {
        int n = sizes.length;
        ByteBuffer h = ByteBuffer.allocate(6 + 16 * n).order(ByteOrder.LITTLE_ENDIAN);
        h.putShort((short) 0).putShort((short) 1).putShort((short) n);
        int off = 6 + 16 * n;
        for (int i = 0; i < n; i++) {
            h.put((byte) (sizes[i] >= 256 ? 0 : sizes[i])).put((byte) (sizes[i] >= 256 ? 0 : sizes[i]));
            h.put((byte) 0).put((byte) 0);
            h.putShort((short) 1).putShort((short) 32);
            h.putInt(png[i].length).putInt(off);
            off += png[i].length;
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(o);
        d.write(h.array());
        for (byte[] p : png) {
            d.write(p);
        }
        return o.toByteArray();
    }
}
