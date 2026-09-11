-- =====================================================================
-- Logistik_App / Schema DEMO
-- 09 - Runde 2: Struktur fuer Kapazitaet, Kosten und Auftraege
--
-- Nur Struktur, keine Daten (die stehen in 10_daten2.sql). Das Skript
-- darf mehrfach laufen: jede Spalte, Tabelle und Regel wird nur
-- angelegt, wenn es sie noch nicht gibt. Damit gilt
--   Neuaufbau:        00 01 09 02 03 04 05 06 07 08 10 11
--   bestehende DEMO:  09 02 04 10 11
-- und beide Wege enden beim selben Schema, ohne die Historie zu verlieren.
--
-- Neu:
--   LOG_ORT.kapazitaet_teu     Stellplatz in TEU, leer = unbegrenzt
--   LOG_WARE.fracht_eur_teu_km Frachtsatz der Ware
--   LOG_BEWEGUNG               + Kosten als Schnappschuss, Freigabezeit,
--                                Ausloeser (HAND / AUFTRAG / JOB)
--   LOG_TARIF                  Stellschrauben (Zollpauschale, Standgeld, Karenz)
--   LOG_AUFTRAG                geplante Fahrten mit Termin
-- =====================================================================

DECLARE
  PROCEDURE spalte(p_tabelle VARCHAR2, p_spalte VARCHAR2, p_def VARCHAR2) IS
    v_n NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_n FROM user_tab_columns
     WHERE table_name = p_tabelle AND column_name = p_spalte;
    IF v_n = 0 THEN
      EXECUTE IMMEDIATE 'ALTER TABLE ' || p_tabelle || ' ADD (' || p_spalte || ' ' || p_def || ')';
    END IF;
  END;

  PROCEDURE regel(p_tabelle VARCHAR2, p_name VARCHAR2, p_def VARCHAR2) IS
    v_n NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_n FROM user_constraints WHERE constraint_name = p_name;
    IF v_n = 0 THEN
      EXECUTE IMMEDIATE 'ALTER TABLE ' || p_tabelle || ' ADD CONSTRAINT ' || p_name || ' ' || p_def;
    END IF;
  END;

  PROCEDURE objekt(p_typ VARCHAR2, p_name VARCHAR2, p_ddl VARCHAR2) IS
    v_n NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_n FROM user_objects
     WHERE object_type = p_typ AND object_name = p_name;
    IF v_n = 0 THEN
      EXECUTE IMMEDIATE p_ddl;
    END IF;
  END;
BEGIN
  -- Kapazitaet -------------------------------------------------------
  spalte('LOG_ORT', 'KAPAZITAET_TEU', 'NUMBER(4)');
  regel('LOG_ORT', 'LOG_ORT_KAP_CK', 'CHECK (kapazitaet_teu IS NULL OR kapazitaet_teu > 0)');

  -- Frachtsatz -------------------------------------------------------
  spalte('LOG_WARE', 'FRACHT_EUR_TEU_KM', 'NUMBER(6,3)');
  regel('LOG_WARE', 'LOG_WARE_SATZ_CK', 'CHECK (fracht_eur_teu_km IS NULL OR fracht_eur_teu_km >= 0)');

  -- Kosten, Freigabe, Ausloeser an der Bewegung ------------------------
  spalte('LOG_BEWEGUNG', 'FRACHT_EUR',     'NUMBER(10,2)');
  spalte('LOG_BEWEGUNG', 'ZOLL_EUR',       'NUMBER(10,2)');
  spalte('LOG_BEWEGUNG', 'STANDGELD_EUR',  'NUMBER(10,2)');
  spalte('LOG_BEWEGUNG', 'FREIGEGEBEN_AM', 'TIMESTAMP');
  spalte('LOG_BEWEGUNG', 'AUSGELOEST',     'VARCHAR2(10) DEFAULT ''HAND'' NOT NULL');
  regel('LOG_BEWEGUNG', 'LOG_BEWEGUNG_AUSL_CK', 'CHECK (ausgeloest IN (''HAND'', ''AUFTRAG'', ''JOB''))');
  regel('LOG_BEWEGUNG', 'LOG_BEWEGUNG_FREI_CK',
        'CHECK (freigegeben_am IS NULL OR (zoll = ''J'' AND freigegeben_am >= zeitpunkt))');

  -- Tarife -----------------------------------------------------------
  objekt('TABLE', 'LOG_TARIF',
    'CREATE TABLE log_tarif (
       schluessel   VARCHAR2(30)  NOT NULL,
       wert         NUMBER(12,3)  NOT NULL,
       einheit      VARCHAR2(20)  NOT NULL,
       beschreibung VARCHAR2(200),
       CONSTRAINT log_tarif_pk PRIMARY KEY (schluessel),
       CONSTRAINT log_tarif_wert_ck CHECK (wert >= 0))');

  -- Auftraege --------------------------------------------------------
  objekt('SEQUENCE', 'LOG_AUFTRAG_SEQ',
    'CREATE SEQUENCE log_auftrag_seq START WITH 1 INCREMENT BY 1 NOCACHE');
  objekt('TABLE', 'LOG_AUFTRAG',
    'CREATE TABLE log_auftrag (
       auftrag_id       NUMBER,
       container_id     NUMBER        NOT NULL,
       nach_ort_id      NUMBER        NOT NULL,
       faellig_am       TIMESTAMP     NOT NULL,
       status           VARCHAR2(12)  DEFAULT ''OFFEN'' NOT NULL,
       grund            VARCHAR2(300),
       versuche         NUMBER(6)     DEFAULT 0 NOT NULL,
       bewegung_id      NUMBER,
       ausgefuehrt_von  VARCHAR2(4),
       angelegt_am      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
       geaendert_am     TIMESTAMP,
       erledigt_am      TIMESTAMP,
       CONSTRAINT log_auftrag_pk        PRIMARY KEY (auftrag_id),
       CONSTRAINT log_auftrag_cont_fk   FOREIGN KEY (container_id)
                  REFERENCES log_container (container_id) ON DELETE CASCADE,
       CONSTRAINT log_auftrag_ort_fk    FOREIGN KEY (nach_ort_id) REFERENCES log_ort (ort_id),
       CONSTRAINT log_auftrag_bew_fk    FOREIGN KEY (bewegung_id)
                  REFERENCES log_bewegung (bewegung_id) ON DELETE SET NULL,
       CONSTRAINT log_auftrag_status_ck CHECK (status IN (''OFFEN'', ''ERLEDIGT'', ''STORNIERT'', ''GESCHEITERT'')),
       CONSTRAINT log_auftrag_von_ck    CHECK (ausgefuehrt_von IS NULL OR ausgefuehrt_von IN (''APP'', ''JOB'')),
       CONSTRAINT log_auftrag_ende_ck   CHECK (status = ''OFFEN'' OR erledigt_am IS NOT NULL))');
  objekt('INDEX', 'LOG_AUFTRAG_OFFEN_IX',
    'CREATE INDEX log_auftrag_offen_ix ON log_auftrag (status, faellig_am)');
  objekt('INDEX', 'LOG_AUFTRAG_CONT_IX',
    'CREATE INDEX log_auftrag_cont_ix ON log_auftrag (container_id, faellig_am)');
  objekt('INDEX', 'LOG_AUFTRAG_BEW_IX',
    'CREATE INDEX log_auftrag_bew_ix ON log_auftrag (bewegung_id)');
END;
/

CREATE OR REPLACE TRIGGER log_auftrag_bi
  BEFORE INSERT ON log_auftrag
  FOR EACH ROW
  WHEN (new.auftrag_id IS NULL)
BEGIN
  :new.auftrag_id := log_auftrag_seq.NEXTVAL;
END;
/

COMMENT ON COLUMN log_ort.kapazitaet_teu IS
  'Stellplatz in TEU. Leer = unbegrenzt. Container beim Zoll belegen ihren Platz mit';
COMMENT ON COLUMN log_ware.fracht_eur_teu_km IS
  'Frachtsatz in EUR je TEU und Kilometer Luftlinie';
COMMENT ON COLUMN log_bewegung.fracht_eur IS
  'Schnappschuss bei der Fahrt: km * TEU * Frachtsatz, auf Cent gerundet';
COMMENT ON COLUMN log_bewegung.zoll_eur IS
  'Schnappschuss bei der Fahrt: Zollpauschale bei Zollfahrt, sonst 0';
COMMENT ON COLUMN log_bewegung.standgeld_eur IS
  'Bei der Freigabe: je angefangene Viertelstunde beim Zoll. Leer, solange er wartet';
COMMENT ON COLUMN log_bewegung.freigegeben_am IS
  'Zeitpunkt der Zollfreigabe. Nur bei Zollfahrten; leer, solange er wartet';
COMMENT ON COLUMN log_bewegung.ausgeloest IS
  'HAND = in der Karte gezogen, AUFTRAG = Auftrag durch die App, JOB = Auftrag durch LOG_AUFTRAG_JOB';
COMMENT ON TABLE log_tarif IS
  'Stellschrauben fuer Kosten und Auftraege. Aenderungen wirken auf kuenftige Fahrten, nie auf die Historie';
COMMENT ON TABLE log_auftrag IS
  'Geplante Fahrten. Je Container gilt die Terminfolge: nur der frueheste offene Auftrag ist ausfuehrbar';
COMMENT ON COLUMN log_auftrag.grund IS
  'Warum ein offener Auftrag nicht fahren konnte (Zoll, Ziel voll) oder warum er gescheitert ist';
