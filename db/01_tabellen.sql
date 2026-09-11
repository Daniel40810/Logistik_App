-- =====================================================================
-- Logistik_App / Schema DEMO
-- 01 - Tabellen, Sequenzen, Schluessel-Trigger, Geo-Metadaten
--
-- Fuenf Tabellen, alle mit Praefix LOG_:
--   LOG_LAND       Laender und ihre Zugehoerigkeiten (EU, Zollunion,
--                  Schengen). Hier - und NUR hier - entsteht die Zone.
--   LOG_ORT        Staedte als Punkt in WGS84 (SRID 8307)
--   LOG_WARE       Was in einem Container steckt, samt Anzeigefarbe
--   LOG_CONTAINER  Die Container selbst, mit Kennung nach ISO 6346
--   LOG_BEWEGUNG   Jede Verschiebung als eigene Zeile (Historie)
--
-- Schluesselvergabe nach Hauskonvention: Sequence plus BEFORE-INSERT-
-- Trigger, kein IDENTITY.
-- =====================================================================

-- ---------------------------------------------------------------------
-- LOG_LAND
--
-- Die Zone gehoert zum Land, nicht zur Stadt. Sie ist deshalb eine
-- virtuelle Spalte: niemand kann sie falsch pflegen, und wenn sich die
-- Zugehoerigkeit eines Landes aendert (London seit 2021), aendert man
-- EINE Zeile statt jede Stadt.
--
-- Zollunion und EU sind NICHT dasselbe: Schweiz und Norwegen sind im
-- Schengenraum, aber ausserhalb der Zollunion. An der Zollunion haengt,
-- ob beim Verschieben ein Zollschritt noetig wird - nicht an der Zone.
-- ---------------------------------------------------------------------
CREATE TABLE log_land (
  iso2         CHAR(2)       NOT NULL,
  name         VARCHAR2(60)  NOT NULL,
  inland       CHAR(1)       DEFAULT 'N' NOT NULL,
  eu_mitglied  CHAR(1)       DEFAULT 'N' NOT NULL,
  zollunion    CHAR(1)       DEFAULT 'N' NOT NULL,
  schengen     CHAR(1)       DEFAULT 'N' NOT NULL,
  grenze       SDO_GEOMETRY,
  zone_typ     VARCHAR2(10)  GENERATED ALWAYS AS (
                 CASE WHEN inland = 'J'      THEN 'INLAND'
                      WHEN eu_mitglied = 'J' THEN 'EU'
                      ELSE 'DRITTLAND' END) VIRTUAL,
  CONSTRAINT log_land_pk      PRIMARY KEY (iso2),
  CONSTRAINT log_land_iso_ck  CHECK (REGEXP_LIKE(iso2, '^[A-Z]{2}$')),
  CONSTRAINT log_land_in_ck   CHECK (inland      IN ('J', 'N')),
  CONSTRAINT log_land_eu_ck   CHECK (eu_mitglied IN ('J', 'N')),
  CONSTRAINT log_land_zu_ck   CHECK (zollunion   IN ('J', 'N')),
  CONSTRAINT log_land_sch_ck  CHECK (schengen    IN ('J', 'N')),
  CONSTRAINT log_land_euzu_ck CHECK (eu_mitglied = 'N' OR zollunion = 'J')
);

-- Genau ein Land darf Inland sein. Ein eindeutiger Index ueber einen
-- Ausdruck, der fuer alle anderen Laender NULL ist - NULL zaehlt nicht.
CREATE UNIQUE INDEX log_land_inland_ux
  ON log_land (CASE WHEN inland = 'J' THEN 'J' END);

COMMENT ON TABLE log_land IS
  'Laender. Die Zone jeder Stadt wird hier abgeleitet, nie an der Stadt gepflegt';
COMMENT ON COLUMN log_land.zone_typ IS
  'Virtuell: INLAND, EU oder DRITTLAND';
COMMENT ON COLUMN log_land.zollunion IS
  'J = in der EU-Zollunion. Entscheidet ueber den Zollschritt, nicht die Zone';
COMMENT ON COLUMN log_land.grenze IS
  'Landesflaeche, SRID 8307. Wird erst in Phase 2 (Karte) gefuellt';

-- ---------------------------------------------------------------------
-- LOG_ORT
-- ---------------------------------------------------------------------
CREATE SEQUENCE log_ort_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE log_ort (
  ort_id  NUMBER,
  name    VARCHAR2(60)  NOT NULL,
  iso2    CHAR(2)       NOT NULL,
  lage    SDO_GEOMETRY  NOT NULL,
  CONSTRAINT log_ort_pk      PRIMARY KEY (ort_id),
  CONSTRAINT log_ort_name_uk UNIQUE (name),
  CONSTRAINT log_ort_land_fk FOREIGN KEY (iso2) REFERENCES log_land (iso2)
);

CREATE OR REPLACE TRIGGER log_ort_bi
  BEFORE INSERT ON log_ort
  FOR EACH ROW
  WHEN (new.ort_id IS NULL)
BEGIN
  :new.ort_id := log_ort_seq.NEXTVAL;
END;
/

CREATE INDEX log_ort_land_ix ON log_ort (iso2);

COMMENT ON TABLE log_ort IS 'Staedte, zwischen denen Container verschoben werden';
COMMENT ON COLUMN log_ort.lage IS 'Punkt (Laenge, Breite) in WGS84, SRID 8307';

-- ---------------------------------------------------------------------
-- LOG_WARE
-- ---------------------------------------------------------------------
CREATE SEQUENCE log_ware_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE log_ware (
  ware_id         NUMBER,
  code            VARCHAR2(20)  NOT NULL,
  name            VARCHAR2(40)  NOT NULL,
  farbe_hex       VARCHAR2(7)   NOT NULL,
  kuehlpflichtig  CHAR(1)       DEFAULT 'N' NOT NULL,
  sortierung      NUMBER(3)     DEFAULT 0 NOT NULL,
  CONSTRAINT log_ware_pk       PRIMARY KEY (ware_id),
  CONSTRAINT log_ware_code_uk  UNIQUE (code),
  CONSTRAINT log_ware_code_ck  CHECK (REGEXP_LIKE(code, '^[A-Z_]+$')),
  CONSTRAINT log_ware_farbe_ck CHECK (REGEXP_LIKE(farbe_hex, '^#[0-9A-F]{6}$')),
  CONSTRAINT log_ware_kuehl_ck CHECK (kuehlpflichtig IN ('J', 'N'))
);

CREATE OR REPLACE TRIGGER log_ware_bi
  BEFORE INSERT ON log_ware
  FOR EACH ROW
  WHEN (new.ware_id IS NULL)
BEGIN
  :new.ware_id := log_ware_seq.NEXTVAL;
END;
/

COMMENT ON TABLE log_ware IS 'Warenarten. Die Farbe ist die Containerfarbe auf der Karte';
COMMENT ON COLUMN log_ware.kuehlpflichtig IS
  'J = faehrt im Kuehlcontainer (Reefer, ISO-Typ 22R1/42R1)';

-- ---------------------------------------------------------------------
-- LOG_CONTAINER
--
-- Die Kennung wird nicht hier, sondern in 03_regeln.sql vom Trigger
-- vergeben und geprueft - der braucht das Package LOG_API, das es an
-- dieser Stelle noch nicht gibt. Hier nur der Schluessel.
--
-- Die Spalte ist bewusst breiter als die Kennung: eine Eingabe wie
-- 'DANU 100001 5' (13 Zeichen) muss den Trigger ERREICHEN, um dort
-- kompakt gemacht zu werden. Die Laenge prueft Oracle vor dem Trigger
-- (ORA-12899), die CHECK-Bedingung erst danach.
-- ---------------------------------------------------------------------
CREATE SEQUENCE log_container_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE log_container (
  container_id  NUMBER,
  kennung       VARCHAR2(20)  NOT NULL,
  groesse_fuss  NUMBER(2)     DEFAULT 20 NOT NULL,
  ware_id       NUMBER        NOT NULL,
  ort_id        NUMBER        NOT NULL,
  status        VARCHAR2(10)  DEFAULT 'BEREIT' NOT NULL,
  angelegt_am   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  geaendert_am  TIMESTAMP,
  CONSTRAINT log_container_pk        PRIMARY KEY (container_id),
  CONSTRAINT log_container_kenn_uk   UNIQUE (kennung),
  CONSTRAINT log_container_kenn_ck   CHECK (LENGTH(kennung) = 11),
  CONSTRAINT log_container_groe_ck   CHECK (groesse_fuss IN (20, 40)),
  CONSTRAINT log_container_status_ck CHECK (status IN ('BEREIT', 'ZOLL')),
  CONSTRAINT log_container_ware_fk   FOREIGN KEY (ware_id) REFERENCES log_ware (ware_id),
  CONSTRAINT log_container_ort_fk    FOREIGN KEY (ort_id)  REFERENCES log_ort (ort_id)
);

CREATE INDEX log_container_ort_ix  ON log_container (ort_id);
CREATE INDEX log_container_ware_ix ON log_container (ware_id);

COMMENT ON TABLE log_container IS 'Container auf der Karte. Standort = ort_id';
COMMENT ON COLUMN log_container.kennung IS
  'ISO 6346 ohne Leerzeichen: Eigentuemer(3) + Kategorie(1) + Serie(6) + Pruefziffer(1)';
COMMENT ON COLUMN log_container.status IS
  'BEREIT = frei verschiebbar, ZOLL = wartet nach Grenzuebertritt auf Freigabe';

-- ---------------------------------------------------------------------
-- LOG_BEWEGUNG
--
-- Die Zonen stehen als SCHNAPPSCHUSS in der Zeile, nicht als Verweis:
-- eine Fahrt nach London im Jahr 2019 war eine EU-Fahrt, auch wenn
-- Grossbritannien heute Drittland ist. Die Historie soll sagen, was
-- damals galt.
-- ---------------------------------------------------------------------
CREATE SEQUENCE log_bewegung_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE log_bewegung (
  bewegung_id   NUMBER,
  container_id  NUMBER        NOT NULL,
  von_ort_id    NUMBER        NOT NULL,
  nach_ort_id   NUMBER        NOT NULL,
  von_zone      VARCHAR2(10)  NOT NULL,
  nach_zone     VARCHAR2(10)  NOT NULL,
  zoll          CHAR(1)       NOT NULL,
  distanz_m     NUMBER        NOT NULL,
  zeitpunkt     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT log_bewegung_pk      PRIMARY KEY (bewegung_id),
  CONSTRAINT log_bewegung_cont_fk FOREIGN KEY (container_id)
             REFERENCES log_container (container_id) ON DELETE CASCADE,
  CONSTRAINT log_bewegung_von_fk  FOREIGN KEY (von_ort_id)  REFERENCES log_ort (ort_id),
  CONSTRAINT log_bewegung_nach_fk FOREIGN KEY (nach_ort_id) REFERENCES log_ort (ort_id),
  CONSTRAINT log_bewegung_ziel_ck CHECK (von_ort_id <> nach_ort_id),
  CONSTRAINT log_bewegung_zoll_ck CHECK (zoll IN ('J', 'N')),
  CONSTRAINT log_bewegung_dist_ck CHECK (distanz_m >= 0)
);

CREATE OR REPLACE TRIGGER log_bewegung_bi
  BEFORE INSERT ON log_bewegung
  FOR EACH ROW
  WHEN (new.bewegung_id IS NULL)
BEGIN
  :new.bewegung_id := log_bewegung_seq.NEXTVAL;
END;
/

CREATE INDEX log_bewegung_cont_ix ON log_bewegung (container_id, zeitpunkt);

COMMENT ON TABLE log_bewegung IS 'Jede Verschiebung eines Containers, in der Reihenfolge des Geschehens';
COMMENT ON COLUMN log_bewegung.distanz_m IS 'Luftlinie in Metern, geodaetisch auf WGS84';

-- ---------------------------------------------------------------------
-- Geo-Metadaten. Ohne diesen Eintrag legt Oracle keinen Spatial-Index
-- an. Bei SRID 8307 sind die Grenzen Grad, die Toleranz aber METER.
-- ---------------------------------------------------------------------
INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
VALUES ('LOG_ORT', 'LAGE',
        SDO_DIM_ARRAY(SDO_DIM_ELEMENT('LONG', -180, 180, 0.05),
                      SDO_DIM_ELEMENT('LAT',   -90,  90, 0.05)),
        8307);

INSERT INTO user_sdo_geom_metadata (table_name, column_name, diminfo, srid)
VALUES ('LOG_LAND', 'GRENZE',
        SDO_DIM_ARRAY(SDO_DIM_ELEMENT('LONG', -180, 180, 0.05),
                      SDO_DIM_ELEMENT('LAT',   -90,  90, 0.05)),
        8307);
COMMIT;

-- Spatial-Index auf die Staedte - er wird ab Phase 4 gebraucht, wenn
-- beim Loslassen die naechste Stadt gesucht wird (SDO_NN). Wie in
-- FCurvedField erst SPATIAL_INDEX_V2 versuchen, dann den alten Namen.
-- Der Index fuer LOG_LAND.GRENZE folgt in Phase 2 mit den Flaechen.
BEGIN
  EXECUTE IMMEDIATE 'CREATE INDEX log_ort_sx ON log_ort (lage) '
                    || 'INDEXTYPE IS MDSYS.SPATIAL_INDEX_V2';
EXCEPTION
  WHEN OTHERS THEN
    EXECUTE IMMEDIATE 'CREATE INDEX log_ort_sx ON log_ort (lage) '
                      || 'INDEXTYPE IS MDSYS.SPATIAL_INDEX';
END;
/
