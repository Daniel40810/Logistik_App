-- =====================================================================
-- Logistik_App / Schema DEMO
-- 05 - Stammdaten und Startbestand
--
-- Laender: Stand 2026. Norwegen und Schweiz: Schengen ja, EU und
-- Zollunion nein. Grossbritannien seit 2021 weder noch.
-- Koordinaten: Stadtmitte, Laenge vor Breite (so will es SDO).
-- Farben: wie im Werkbuch vorgeschlagen und uebernommen. Kuehlware ist
-- weiss, wie echte Reefer-Container.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Laender
-- ---------------------------------------------------------------------
INSERT INTO log_land (iso2, name, inland, eu_mitglied, zollunion, schengen)
VALUES ('DE', 'Deutschland', 'J', 'J', 'J', 'J');
INSERT INTO log_land (iso2, name, inland, eu_mitglied, zollunion, schengen)
VALUES ('FR', 'Frankreich', 'N', 'J', 'J', 'J');
INSERT INTO log_land (iso2, name, inland, eu_mitglied, zollunion, schengen)
VALUES ('PL', 'Polen', 'N', 'J', 'J', 'J');
INSERT INTO log_land (iso2, name, inland, eu_mitglied, zollunion, schengen)
VALUES ('ES', 'Spanien', 'N', 'J', 'J', 'J');
INSERT INTO log_land (iso2, name, inland, eu_mitglied, zollunion, schengen)
VALUES ('NO', 'Norwegen', 'N', 'N', 'N', 'J');
INSERT INTO log_land (iso2, name, inland, eu_mitglied, zollunion, schengen)
VALUES ('GB', 'Vereinigtes Königreich', 'N', 'N', 'N', 'N');
INSERT INTO log_land (iso2, name, inland, eu_mitglied, zollunion, schengen)
VALUES ('CH', 'Schweiz', 'N', 'N', 'N', 'J');

-- ---------------------------------------------------------------------
-- Staedte
-- ---------------------------------------------------------------------
INSERT INTO log_ort (name, iso2, lage) VALUES ('München', 'DE',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(11.5755, 48.1374, NULL), NULL, NULL));
INSERT INTO log_ort (name, iso2, lage) VALUES ('Hamburg', 'DE',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(9.9937, 53.5511, NULL), NULL, NULL));
INSERT INTO log_ort (name, iso2, lage) VALUES ('Köln', 'DE',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(6.9603, 50.9375, NULL), NULL, NULL));
INSERT INTO log_ort (name, iso2, lage) VALUES ('Berlin', 'DE',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(13.4050, 52.5200, NULL), NULL, NULL));
INSERT INTO log_ort (name, iso2, lage) VALUES ('Paris', 'FR',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(2.3522, 48.8566, NULL), NULL, NULL));
INSERT INTO log_ort (name, iso2, lage) VALUES ('Warschau', 'PL',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(21.0122, 52.2297, NULL), NULL, NULL));
INSERT INTO log_ort (name, iso2, lage) VALUES ('Barcelona', 'ES',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(2.1734, 41.3851, NULL), NULL, NULL));
INSERT INTO log_ort (name, iso2, lage) VALUES ('Oslo', 'NO',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(10.7522, 59.9139, NULL), NULL, NULL));
INSERT INTO log_ort (name, iso2, lage) VALUES ('London', 'GB',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(-0.1276, 51.5072, NULL), NULL, NULL));
INSERT INTO log_ort (name, iso2, lage) VALUES ('Zürich', 'CH',
  SDO_GEOMETRY(2001, 8307, SDO_POINT_TYPE(8.5417, 47.3769, NULL), NULL, NULL));

-- ---------------------------------------------------------------------
-- Waren
-- ---------------------------------------------------------------------
INSERT INTO log_ware (code, name, farbe_hex, kuehlpflichtig, sortierung)
VALUES ('EISEN', 'Eisen', '#8C3A22', 'N', 10);
INSERT INTO log_ware (code, name, farbe_hex, kuehlpflichtig, sortierung)
VALUES ('WEIZEN', 'Weizen', '#D6A62A', 'N', 20);
INSERT INTO log_ware (code, name, farbe_hex, kuehlpflichtig, sortierung)
VALUES ('HOLZ', 'Holz', '#6E4F2E', 'N', 30);
INSERT INTO log_ware (code, name, farbe_hex, kuehlpflichtig, sortierung)
VALUES ('KOHLE', 'Kohle', '#2E3236', 'N', 40);
INSERT INTO log_ware (code, name, farbe_hex, kuehlpflichtig, sortierung)
VALUES ('MASCHINEN', 'Maschinen', '#3F7A46', 'N', 50);
INSERT INTO log_ware (code, name, farbe_hex, kuehlpflichtig, sortierung)
VALUES ('KUEHLWARE', 'Kühlware', '#F4F6F7', 'J', 60);

COMMIT;

-- ---------------------------------------------------------------------
-- Startbestand: 18 Container. Die Kennung vergibt der Trigger
-- (Container 1 = DANU 100001 5).
-- ---------------------------------------------------------------------
DECLARE
  PROCEDURE neu(p_ort VARCHAR2, p_ware VARCHAR2, p_fuss NUMBER) IS
  BEGIN
    INSERT INTO log_container (groesse_fuss, ware_id, ort_id)
    SELECT p_fuss, w.ware_id, o.ort_id
      FROM log_ware w, log_ort o
     WHERE w.code = p_ware
       AND o.name = p_ort;
    IF SQL%ROWCOUNT <> 1 THEN
      RAISE_APPLICATION_ERROR(-20099,
        'Startbestand: ' || p_ort || ' / ' || p_ware || ' nicht gefunden');
    END IF;
  END neu;
BEGIN
  neu('Hamburg',   'WEIZEN',    40);
  neu('Hamburg',   'EISEN',     20);
  neu('Hamburg',   'KOHLE',     20);
  neu('Hamburg',   'HOLZ',      20);
  neu('Berlin',    'MASCHINEN', 20);
  neu('Berlin',    'KUEHLWARE', 20);
  neu('München',   'MASCHINEN', 40);
  neu('München',   'KUEHLWARE', 20);
  neu('Köln',      'EISEN',     20);
  neu('Köln',      'KOHLE',     20);
  neu('Paris',     'WEIZEN',    20);
  neu('Warschau',  'WEIZEN',    40);
  neu('Warschau',  'KOHLE',     20);
  neu('Barcelona', 'KUEHLWARE', 20);
  neu('Oslo',      'HOLZ',      40);
  neu('London',    'MASCHINEN', 20);
  neu('Zürich',    'KUEHLWARE', 20);
  neu('Zürich',    'MASCHINEN', 20);
  COMMIT;
END;
/

-- ---------------------------------------------------------------------
-- Etwas Historie, damit die Bewegungsliste nicht leer startet - und
-- zwar ueber LOG_API, genau wie die Anwendung es tun wird:
--   Weizen   Warschau -> Berlin    EU -> Inland, kein Zoll
--   Eisen    Koeln    -> Paris     Inland -> EU, kein Zoll
--   Holz     Oslo     -> Hamburg   Drittland -> Inland, Zoll, danach frei
-- ---------------------------------------------------------------------
DECLARE
  v_bew  NUMBER;
  v_holz NUMBER;

  FUNCTION cont(p_ort VARCHAR2, p_ware VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    SELECT c.container_id INTO v_id
      FROM log_container c
      JOIN log_ort  o ON o.ort_id  = c.ort_id
      JOIN log_ware w ON w.ware_id = c.ware_id
     WHERE o.name = p_ort
       AND w.code = p_ware
     ORDER BY c.container_id
     FETCH FIRST 1 ROWS ONLY;
    RETURN v_id;
  END cont;

  FUNCTION ort(p_name VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    SELECT ort_id INTO v_id FROM log_ort WHERE name = p_name;
    RETURN v_id;
  END ort;
BEGIN
  log_api.verschieben(cont('Warschau', 'WEIZEN'), ort('Berlin'),  v_bew);
  log_api.verschieben(cont('Köln',     'EISEN'),  ort('Paris'),   v_bew);
  -- Den Holz-Container VORHER merken: in Hamburg steht schon einer.
  v_holz := cont('Oslo', 'HOLZ');
  log_api.verschieben(v_holz, ort('Hamburg'), v_bew);
  log_api.zoll_freigeben(v_holz);
  COMMIT;
END;
/

-- Kurzkontrolle
SELECT zone_typ, COUNT(*) AS staedte FROM log_ort_v GROUP BY zone_typ ORDER BY zone_typ;
SELECT kennung_anzeige, typ_code, ware, ort, status FROM log_container_v ORDER BY container_id;
SELECT von_ort, nach_ort, von_zone, nach_zone, zoll, distanz_km FROM log_bewegung_v ORDER BY bewegung_id;
