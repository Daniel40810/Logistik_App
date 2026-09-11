-- =====================================================================
-- Logistik_App / Schema DEMO
-- 08 - Karte: Spatial-Index auf den Landesflaechen und die Lesesicht
--
-- Laeuft NACH 07_grenzen.tsv: ein Index, der beim Laden von 58 000
-- Stuetzpunkten mitgepflegt werden muss, macht das Laden nur langsamer.
-- Einzeln wiederholbar.
-- =====================================================================

BEGIN
  EXECUTE IMMEDIATE 'DROP INDEX log_land_sx FORCE';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE INDEX log_land_sx ON log_land (grenze) '
                    || 'INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2';
EXCEPTION
  WHEN OTHERS THEN
    EXECUTE IMMEDIATE 'CREATE INDEX log_land_sx ON log_land (grenze) '
                      || 'INDEXTYPE IS MDSYS.SPATIAL_INDEX';
END;
/

-- ---------------------------------------------------------------------
-- Was die Karte liest: Land, Zone und die Flaeche.
--
-- GRENZE ist das SDO_GEOMETRY-Objekt selbst; die Anwendung liest es als
-- java.sql.Struct (SDO_ELEM_INFO + SDO_ORDINATES), ohne sdoapi.
--
-- FALLSTRICK: frueher stand hier zusaetzlich SDO_UTIL.TO_WKTGEOMETRY.
-- Das scheitert an den grossen Laenderflaechen mit ORA-13199 "wk buffer
-- merge failure" (MDSYS.SDO_UTIL) - im Dauerlauf jedes Mal. Die Spalte
-- ist deshalb raus; wer WKT sehen will, fragt ein kleines Land einzeln ab.
--
-- Projiziert wird in Java (ETRS89-LAEA, EPSG:3035) - dieselbe Rechnung
-- fuer Grenzen, Staedte und Routen. Die Pruefung vergleicht mit SDO_CS.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW log_land_v AS
SELECT l.iso2,
       l.name,
       l.zone_typ,
       l.eu_mitglied,
       l.zollunion,
       l.schengen,
       l.grenze
  FROM log_land l;

-- Kurzkontrolle
SELECT COUNT(*) AS laender, COUNT(grenze) AS mit_grenze FROM log_land;
SELECT l.iso2, SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(l.grenze, 0.05) AS gueltig
  FROM log_land l
 WHERE SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(l.grenze, 0.05) <> 'TRUE';
