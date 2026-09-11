package com.dan.logistikapp.mesh;

import com.dan.rayphong.Mesh;
import com.dan.rayphong.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ISO-Container als RayPhong-Mesh, in Metern, Mittelpunkt der Grundfl&auml;che
 * im Ursprung. L&auml;nge entlang x, H&ouml;he entlang y, Breite entlang z.
 *
 * <p>Au&szlig;enma&szlig;e nach ISO 668: 20 Fu&szlig; 6,058 &times; 2,438 &times; 2,591 m,
 * 40 Fu&szlig; 12,192 &times; 2,438 &times; 2,591 m (8'6" hoch). Aufgebaut aus
 * Teilen mit eigener Rolle, damit der Renderer sie verschieden einf&auml;rben
 * kann: Wand (Warenfarbe), Rahmen (dunkler), Stahl (Verriegelung,
 * Eckbeschl&auml;ge), K&uuml;hlaggregat (nur Reefer).</p>
 *
 * <p>Die Seitenw&auml;nde sind trapezf&ouml;rmig gesickt. Die Teilung ist mit
 * 0,46 m etwas gr&ouml;ber als am echten Container (rund 0,28 m) - bei
 * 26 Bildpunkten L&auml;nge w&uuml;rden echte Sicken zu Moir&eacute; statt zu Rippen.</p>
 *
 * @author Dan
 */
public final class ContainerMesh {

    /** Wof&uuml;r ein Teil steht - der Renderer w&auml;hlt danach das Material. */
    public enum Rolle { WAND, RAHMEN, STAHL, AGGREGAT }

    /** Ein Teil des Containers. */
    public static final class Teil {
        public final Rolle rolle;
        public final Mesh mesh;

        Teil(Rolle rolle, Mesh mesh) {
            this.rolle = rolle;
            this.mesh = mesh;
        }
    }

    public static final float BREITE = 2.438f;
    public static final float HOEHE = 2.591f;
    public static final float LAENGE_20 = 6.058f;
    public static final float LAENGE_40 = 12.192f;

    private static final float SICKE_TEILUNG = 0.46f;
    private static final float SICKE_TIEFE = 0.045f;
    private static final float RAHMEN = 0.10f;

    private ContainerMesh() {
    }

    public static float laenge(int fuss) {
        return fuss == 40 ? LAENGE_40 : LAENGE_20;
    }

    /** Baut einen Container. */
    public static List<Teil> baue(int fuss, boolean reefer) {
        float l = laenge(fuss);
        float hl = l / 2;
        float hb = BREITE / 2;
        float h = HOEHE;
        List<Teil> teile = new ArrayList<Teil>();

        // --- Wand: gesickte Seiten, Dach, Stirnwand, Tuerseite -------------
        Bauer wand = new Bauer();
        float innen = hb - SICKE_TIEFE;
        seite(wand, -hl + RAHMEN, hl - RAHMEN, RAHMEN, h - RAHMEN, innen, SICKE_TIEFE, +1);
        seite(wand, -hl + RAHMEN, hl - RAHMEN, RAHMEN, h - RAHMEN, innen, SICKE_TIEFE, -1);
        // Dach leicht vertieft zwischen den Dachrahmen
        wand.quader(-hl + RAHMEN, h - 0.03f, -hb + RAHMEN, hl - RAHMEN, h - 0.01f, hb - RAHMEN);
        // Stirnwand (x = -hl) und Tuerseite (x = +hl), leicht zurueckgesetzt
        wand.quader(-hl + 0.02f, RAHMEN, -hb + RAHMEN, -hl + 0.06f, h - RAHMEN, hb - RAHMEN);
        wand.quader(hl - 0.06f, RAHMEN, -hb + RAHMEN, hl - 0.02f, h - RAHMEN, hb - RAHMEN);
        teile.add(new Teil(Rolle.WAND, wand.mesh()));

        // --- Rahmen: Eckpfosten, Ober- und Untergurte ---------------------
        Bauer rahmen = new Bauer();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float x0 = sx < 0 ? -hl : hl - RAHMEN;
                float z0 = sz < 0 ? -hb : hb - RAHMEN;
                rahmen.quader(x0, 0, z0, x0 + RAHMEN, h, z0 + RAHMEN);
            }
        }
        for (int sz = -1; sz <= 1; sz += 2) {
            float z0 = sz < 0 ? -hb : hb - RAHMEN;
            rahmen.quader(-hl, 0, z0, hl, RAHMEN, z0 + RAHMEN);
            rahmen.quader(-hl, h - RAHMEN, z0, hl, h, z0 + RAHMEN);
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            float x0 = sx < 0 ? -hl : hl - RAHMEN;
            rahmen.quader(x0, 0, -hb, x0 + RAHMEN, RAHMEN, hb);
            rahmen.quader(x0, h - RAHMEN, -hb, x0 + RAHMEN, h, hb);
        }
        teile.add(new Teil(Rolle.RAHMEN, rahmen.mesh()));

        // --- Stahl: vier Verriegelungsstangen an der Tuer, Eckbeschlaege ---
        Bauer stahl = new Bauer();
        float[] stangen = {-hb * 0.78f, -hb * 0.30f, hb * 0.30f, hb * 0.78f};
        for (float z : stangen) {
            stahl.quader(hl - 0.02f, RAHMEN + 0.05f, z - 0.025f, hl + 0.035f, h - RAHMEN - 0.05f, z + 0.025f);
        }
        // Tuerfuge in der Mitte
        stahl.quader(hl - 0.02f, RAHMEN, -0.012f, hl + 0.01f, h - RAHMEN, 0.012f);
        float e = 0.178f;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float x0 = sx < 0 ? -hl - 0.005f : hl - e + 0.005f;
                float z0 = sz < 0 ? -hb - 0.005f : hb - e + 0.005f;
                stahl.quader(x0, -0.005f, z0, x0 + e, 0.118f, z0 + e);
                stahl.quader(x0, h - 0.118f, z0, x0 + e, h + 0.005f, z0 + e);
            }
        }
        teile.add(new Teil(Rolle.STAHL, stahl.mesh()));

        // --- Kuehlaggregat an der Stirnseite, nur beim Reefer ---------------
        if (reefer) {
            Bauer agg = new Bauer();
            agg.quader(-hl - 0.06f, 0.35f, -hb + 0.22f, -hl + 0.03f, h - 0.3f, hb - 0.22f);
            teile.add(new Teil(Rolle.AGGREGAT, agg.mesh()));
        }
        return Collections.unmodifiableList(teile);
    }

    /**
     * Eine gesickte Seitenwand bei z = seite * (innen ... innen + tiefe).
     * Das Profil wiederholt sich entlang x: au&szlig;en flach, Schr&auml;ge,
     * innen flach, Schr&auml;ge.
     */
    private static void seite(Bauer b, float x0, float x1, float y0, float y1,
            float innen, float tiefe, int seite) {
        float p = SICKE_TEILUNG;
        int n = Math.max(1, Math.round((x1 - x0) / p));
        p = (x1 - x0) / n;
        float[] ux = {0, 0.30f, 0.50f, 0.80f, 1.0f};
        float[] uz = {1, 1, 0, 0, 1};
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < 4; k++) {
                float xa = x0 + (i + ux[k]) * p;
                float xb = x0 + (i + ux[k + 1]) * p;
                float za = seite * (innen + uz[k] * tiefe);
                float zb = seite * (innen + uz[k + 1] * tiefe);
                b.wandstreifen(xa, za, xb, zb, y0, y1, seite);
            }
        }
    }

    /** Sammelt Dreiecke mit Fl&auml;chennormalen (scharfe Kanten). */
    static final class Bauer {
        private final List<Vec3> pos = new ArrayList<Vec3>();
        private final List<Vec3> nor = new ArrayList<Vec3>();

        /** Viereck a-b-c-d gegen den Uhrzeigersinn von au&szlig;en gesehen. */
        void viereck(Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
            Vec3 n = b.sub(a).cross(c.sub(a)).normalize();
            dreieck(a, b, c, n);
            dreieck(a, c, d, n);
        }

        private void dreieck(Vec3 a, Vec3 b, Vec3 c, Vec3 n) {
            pos.add(a);
            pos.add(b);
            pos.add(c);
            nor.add(n);
            nor.add(n);
            nor.add(n);
        }

        /** Achsparalleler Quader, alle sechs Seiten nach au&szlig;en gewickelt. */
        void quader(float x0, float y0, float z0, float x1, float y1, float z1) {
            Vec3 p000 = new Vec3(x0, y0, z0);
            Vec3 p100 = new Vec3(x1, y0, z0);
            Vec3 p010 = new Vec3(x0, y1, z0);
            Vec3 p110 = new Vec3(x1, y1, z0);
            Vec3 p001 = new Vec3(x0, y0, z1);
            Vec3 p101 = new Vec3(x1, y0, z1);
            Vec3 p011 = new Vec3(x0, y1, z1);
            Vec3 p111 = new Vec3(x1, y1, z1);
            viereck(p001, p101, p111, p011); // +z
            viereck(p100, p000, p010, p110); // -z
            viereck(p101, p100, p110, p111); // +x
            viereck(p000, p001, p011, p010); // -x
            viereck(p010, p011, p111, p110); // +y
            viereck(p000, p100, p101, p001); // -y
        }

        /** Senkrechter Wandstreifen zwischen (xa,za) und (xb,zb), Normale nach au&szlig;en (seite). */
        void wandstreifen(float xa, float za, float xb, float zb, float y0, float y1, int seite) {
            Vec3 a = new Vec3(xa, y0, za);
            Vec3 b = new Vec3(xb, y0, zb);
            Vec3 c = new Vec3(xb, y1, zb);
            Vec3 d = new Vec3(xa, y1, za);
            if (seite > 0) {
                viereck(a, b, c, d);
            } else {
                viereck(b, a, d, c);
            }
        }

        Mesh mesh() {
            Vec3[] p = pos.toArray(new Vec3[0]);
            Vec3[] n = nor.toArray(new Vec3[0]);
            int[] f = new int[p.length];
            for (int i = 0; i < f.length; i++) {
                f[i] = i;
            }
            return new Mesh(p, n, f);
        }
    }
}
