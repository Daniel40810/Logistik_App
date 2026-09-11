-- =====================================================================
-- Logistik_App / Schema DEMO
-- 02 - Package LOG_API: die eine Stelle, an der die Regeln stehen
--
-- Die Anwendung schreibt Container und Container-Bewegungen NUR ueber
-- diese API. Damit steht jede Regel (Zoll, Zonen, Distanz, was nicht
-- erlaubt ist) an genau einem Ort.
--
-- Das Package committet NIE. Die Transaktion gehoert dem Aufrufer.
--
-- Fehlernummern:
--   -20010  Kennung verstoesst gegen ISO 6346     (Trigger, 03_regeln)
--   -20011  Seriennummer ausserhalb 0..999999
--   -20020  Container steht bereits am Zielort
--   -20021  Container steht beim Zoll, erst freigeben
--   -20022  Container gibt es nicht
--   -20023  Zielort gibt es nicht
--   -20024  Container steht nicht beim Zoll (nichts freizugeben)
--   -20025  Suchradius fuer naechster_ort nicht positiv
--   -20026  Zielort ist voll (Kapazitaet in TEU)            (Runde 2)
--   -20027  Auftrag gibt es nicht                            (Runde 2)
--   -20028  Auftrag ist nicht mehr offen                     (Runde 2)
--   -20030  Containergroesse ist nicht 20 oder 40
--   -20031  Warenart gibt es nicht
--   -20032  Stadtname fehlt oder ist bereits vorhanden
--   -20033  Land gibt es nicht
--   -20034  Koordinaten ausserhalb des gueltigen Bereichs
--   -20035  Kapazitaet ist nicht positiv
--
-- Runde 2 braucht die Spalten und Tabellen aus 09_runde2.sql. Beim
-- Neuaufbau laeuft 09 deshalb vor diesem Skript.
--
-- Sperrreihenfolge, damit sich zwei Sitzungen nie gegenseitig
-- blockieren: erst der Auftrag, dann der Container, dann die Zielstadt.
-- =====================================================================

CREATE OR REPLACE PACKAGE log_api AS

  -- Eigentuemer (3 Buchstaben) plus Kategorie U (Frachtcontainer).
  c_praefix CONSTANT VARCHAR2(4) := 'DANU';

  -- ISO 6346: Zahlenwert eines Zeichens. Ziffern zaehlen als sie selbst,
  -- A = 10, und jedes Vielfache von 11 wird uebersprungen (B = 12,
  -- L = 23, V = 34). Liefert NULL fuer alles andere.
  FUNCTION zeichenwert(p_zeichen IN VARCHAR2) RETURN NUMBER DETERMINISTIC;

  -- Pruefziffer zu den ersten zehn Zeichen einer Kennung.
  FUNCTION pruefziffer(p_basis IN VARCHAR2) RETURN NUMBER DETERMINISTIC;

  -- 'J' wenn Format und Pruefziffer stimmen, sonst 'N'.
  -- Leerzeichen werden ignoriert ('DANU 100001 5' ist gueltig).
  FUNCTION kennung_gueltig(p_kennung IN VARCHAR2) RETURN VARCHAR2 DETERMINISTIC;

  -- Vollstaendige Kennung aus c_praefix und einer Seriennummer.
  FUNCTION neue_kennung(p_serie IN NUMBER) RETURN VARCHAR2 DETERMINISTIC;

  -- Kennung lesbar gesetzt, wie sie auf der Containertuer steht.
  FUNCTION kennung_anzeige(p_kennung IN VARCHAR2) RETURN VARCHAR2 DETERMINISTIC;

  -- INLAND, EU oder DRITTLAND - abgeleitet ueber das Land der Stadt.
  FUNCTION zone_von(p_ort_id IN NUMBER) RETURN VARCHAR2;

  -- Luftlinie zwischen zwei Staedten in Metern (geodaetisch, WGS84).
  FUNCTION distanz_m(p_von_ort IN NUMBER, p_nach_ort IN NUMBER) RETURN NUMBER;

  -- 'J' wenn die Fahrt eine Zollgrenze kreuzt: verschiedene Laender, und
  -- mindestens eines davon liegt ausserhalb der Zollunion.
  FUNCTION zoll_noetig(p_von_ort IN NUMBER, p_nach_ort IN NUMBER) RETURN VARCHAR2;

  -- Naechste Stadt zu einem Punkt (Laenge/Breite, WGS84) innerhalb von
  -- p_max_km, sonst NULL. Ueber SDO_NN auf dem Staedte-Index.
  FUNCTION naechster_ort(p_laenge IN NUMBER, p_breite IN NUMBER,
                         p_max_km IN NUMBER DEFAULT 100) RETURN NUMBER;

  -- Verschiebt einen Container und schreibt die Bewegung.
  -- Kreuzt die Fahrt eine Zollgrenze, steht er danach auf ZOLL.
  PROCEDURE verschieben(p_container_id IN  NUMBER,
                        p_nach_ort     IN  NUMBER,
                        p_bewegung_id  OUT NUMBER);

  -- Gibt einen Container frei, der beim Zoll steht. Schreibt die
  -- Freigabezeit und das Standgeld an seine letzte Fahrt.
  PROCEDURE zoll_freigeben(p_container_id IN NUMBER);

  -- Legt einen neuen Container mit automatisch erzeugter Kennung an,
  -- wenn p_kennung leer ist. Committet nicht.
  PROCEDURE container_anlegen(p_kennung     IN VARCHAR2,
                              p_groesse_fuss IN NUMBER,
                              p_ware_code   IN VARCHAR2,
                              p_ort_id      IN NUMBER,
                              p_container_id OUT NUMBER);

  -- Legt eine neue Stadt mit WGS84-Koordinaten an. Committet nicht.
  PROCEDURE ort_anlegen(p_name        IN VARCHAR2,
                        p_iso2        IN CHAR,
                        p_laenge      IN NUMBER,
                        p_breite      IN NUMBER,
                        p_kapazitaet  IN NUMBER,
                        p_ort_id      OUT NUMBER);

  -- ===================================================== Runde 2

  -- Belegte TEU an einem Ort, Container beim Zoll eingeschlossen.
  FUNCTION belegt_teu(p_ort_id IN NUMBER) RETURN NUMBER;

  -- Freie TEU an einem Ort; NULL, wenn der Ort unbegrenzt ist.
  FUNCTION frei_teu(p_ort_id IN NUMBER) RETURN NUMBER;

  -- Wert aus LOG_TARIF, NULL wenn nicht gesetzt.
  FUNCTION tarif(p_schluessel IN VARCHAR2) RETURN NUMBER;

  -- Frachtkosten: km * TEU * Frachtsatz der Ware, auf Cent. NULL ohne Satz.
  FUNCTION fracht_eur(p_distanz_m IN NUMBER, p_teu IN NUMBER, p_ware_id IN NUMBER) RETURN NUMBER;

  -- Standgeld fuer die Zeit zwischen Ankunft und Freigabe:
  -- je angefangene Viertelstunde der Tarif STANDGELD_VIERTELSTUNDE.
  FUNCTION standgeld_eur(p_von IN TIMESTAMP, p_bis IN TIMESTAMP) RETURN NUMBER;

  -- Was die Fahrt vom heutigen Standort nach p_nach_ort kosten wuerde
  -- (Fracht + Zollpauschale). Genau das, was verschieben schreiben wuerde.
  FUNCTION kosten_vorschau(p_container_id IN NUMBER, p_nach_ort IN NUMBER) RETURN NUMBER;

  -- Legt einen Auftrag an. Ein Termin in der Vergangenheit ist erlaubt:
  -- der Auftrag ist dann sofort faellig.
  PROCEDURE auftrag_anlegen(p_container_id IN  NUMBER,
                            p_nach_ort     IN  NUMBER,
                            p_faellig_am   IN  TIMESTAMP,
                            p_auftrag_id   OUT NUMBER);

  PROCEDURE auftrag_stornieren(p_auftrag_id IN NUMBER);

  -- Fuehrt einen Auftrag aus, wenn er dran ist. p_ergebnis:
  --   ERLEDIGT       gefahren (oder stand schon am Ziel, dann ohne Fahrt)
  --   BLOCKIERT      beim Zoll oder Ziel voll; bleibt OFFEN, Grund steht dran
  --   GESCHEITERT    geht nie mehr (etwa Ort geloescht)
  --   WARTET         ein frueherer offener Auftrag desselben Containers ist zuerst dran
  --   NICHT_FAELLIG  Termin liegt in der Zukunft
  --   GESPERRT       eine andere Sitzung fuehrt ihn gerade aus
  -- p_von: APP oder JOB. Committet nicht.
  PROCEDURE auftrag_ausfuehren(p_auftrag_id  IN  NUMBER,
                               p_von         IN  VARCHAR2,
                               p_ergebnis    OUT VARCHAR2,
                               p_bewegung_id OUT NUMBER);

  -- Kleine Kennzahl, die sich bei jeder Fahrt, Freigabe und Auftrags-
  -- aenderung aendert. Die App fragt sie ab und liest nur bei Aenderung neu.
  FUNCTION stand_marke RETURN VARCHAR2;

END log_api;
/

CREATE OR REPLACE PACKAGE BODY log_api AS

  -- -------------------------------------------------------------------
  FUNCTION zeichenwert(p_zeichen IN VARCHAR2) RETURN NUMBER DETERMINISTIC IS
    v_pos NUMBER;
  BEGIN
    IF p_zeichen IS NULL OR LENGTH(p_zeichen) <> 1 THEN
      RETURN NULL;
    END IF;
    v_pos := INSTR('0123456789', p_zeichen);
    IF v_pos > 0 THEN
      RETURN v_pos - 1;
    END IF;
    v_pos := INSTR('ABCDEFGHIJKLMNOPQRSTUVWXYZ', p_zeichen);
    IF v_pos = 0 THEN
      RETURN NULL;
    END IF;
    -- v_pos + 9 ist der "rohe" Wert (A = 10 .. Z = 35). Fuer jedes
    -- uebersprungene Vielfache von 11 kommt eins dazu: ab B eins,
    -- ab L zwei, ab V drei.
    RETURN (v_pos + 9) + FLOOR((v_pos + 8) / 10);
  END zeichenwert;

  -- -------------------------------------------------------------------
  FUNCTION pruefziffer(p_basis IN VARCHAR2) RETURN NUMBER DETERMINISTIC IS
    v_summe NUMBER := 0;
    v_wert  NUMBER;
  BEGIN
    IF p_basis IS NULL OR LENGTH(p_basis) <> 10 THEN
      RETURN NULL;
    END IF;
    FOR i IN 1 .. 10 LOOP
      v_wert := zeichenwert(SUBSTR(p_basis, i, 1));
      IF v_wert IS NULL THEN
        RETURN NULL;
      END IF;
      v_summe := v_summe + v_wert * POWER(2, i - 1);
    END LOOP;
    -- Rest 10 wird zur 0. Deshalb vergeben Eigentuemer solche
    -- Seriennummern ungern - zulaessig sind sie.
    RETURN MOD(MOD(v_summe, 11), 10);
  END pruefziffer;

  -- -------------------------------------------------------------------
  FUNCTION kennung_gueltig(p_kennung IN VARCHAR2) RETURN VARCHAR2 DETERMINISTIC IS
    v_k VARCHAR2(40) := REPLACE(p_kennung, ' ');
  BEGIN
    IF v_k IS NULL OR NOT REGEXP_LIKE(v_k, '^[A-Z]{3}[UJZ][0-9]{7}$') THEN
      RETURN 'N';
    END IF;
    IF pruefziffer(SUBSTR(v_k, 1, 10)) = TO_NUMBER(SUBSTR(v_k, 11, 1)) THEN
      RETURN 'J';
    END IF;
    RETURN 'N';
  END kennung_gueltig;

  -- -------------------------------------------------------------------
  FUNCTION neue_kennung(p_serie IN NUMBER) RETURN VARCHAR2 DETERMINISTIC IS
    v_basis VARCHAR2(10);
  BEGIN
    IF p_serie IS NULL OR p_serie < 0 OR p_serie > 999999
       OR p_serie <> TRUNC(p_serie) THEN
      RAISE_APPLICATION_ERROR(-20011,
        'Seriennummer ' || p_serie || ' liegt ausserhalb 0..999999');
    END IF;
    v_basis := c_praefix || LPAD(TO_CHAR(p_serie), 6, '0');
    RETURN v_basis || TO_CHAR(pruefziffer(v_basis));
  END neue_kennung;

  -- -------------------------------------------------------------------
  FUNCTION kennung_anzeige(p_kennung IN VARCHAR2) RETURN VARCHAR2 DETERMINISTIC IS
    v_k VARCHAR2(40) := REPLACE(p_kennung, ' ');
  BEGIN
    IF v_k IS NULL OR LENGTH(v_k) <> 11 THEN
      RETURN p_kennung;
    END IF;
    RETURN SUBSTR(v_k, 1, 4) || ' ' || SUBSTR(v_k, 5, 6) || ' ' || SUBSTR(v_k, 11, 1);
  END kennung_anzeige;

  -- -------------------------------------------------------------------
  FUNCTION zone_von(p_ort_id IN NUMBER) RETURN VARCHAR2 IS
    v_zone log_land.zone_typ%TYPE;
  BEGIN
    SELECT l.zone_typ
      INTO v_zone
      FROM log_ort o
      JOIN log_land l ON l.iso2 = o.iso2
     WHERE o.ort_id = p_ort_id;
    RETURN v_zone;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RETURN NULL;
  END zone_von;

  -- -------------------------------------------------------------------
  FUNCTION distanz_m(p_von_ort IN NUMBER, p_nach_ort IN NUMBER) RETURN NUMBER IS
    v_m NUMBER;
  BEGIN
    SELECT SDO_GEOM.SDO_DISTANCE(a.lage, b.lage, 0.05, 'unit=M')
      INTO v_m
      FROM log_ort a, log_ort b
     WHERE a.ort_id = p_von_ort
       AND b.ort_id = p_nach_ort;
    RETURN ROUND(v_m);
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RETURN NULL;
  END distanz_m;

  -- -------------------------------------------------------------------
  FUNCTION zoll_noetig(p_von_ort IN NUMBER, p_nach_ort IN NUMBER) RETURN VARCHAR2 IS
    v_von_land  log_land.iso2%TYPE;
    v_nach_land log_land.iso2%TYPE;
    v_von_zu    log_land.zollunion%TYPE;
    v_nach_zu   log_land.zollunion%TYPE;
  BEGIN
    SELECT la.iso2, la.zollunion, lb.iso2, lb.zollunion
      INTO v_von_land, v_von_zu, v_nach_land, v_nach_zu
      FROM log_ort a
      JOIN log_land la ON la.iso2 = a.iso2
      CROSS JOIN log_ort b
      JOIN log_land lb ON lb.iso2 = b.iso2
     WHERE a.ort_id = p_von_ort
       AND b.ort_id = p_nach_ort;

    IF v_von_land = v_nach_land THEN
      RETURN 'N';
    END IF;
    IF v_von_zu = 'J' AND v_nach_zu = 'J' THEN
      RETURN 'N';
    END IF;
    RETURN 'J';
  END zoll_noetig;

  -- -------------------------------------------------------------------
  FUNCTION naechster_ort(p_laenge IN NUMBER, p_breite IN NUMBER,
                         p_max_km IN NUMBER DEFAULT 100) RETURN NUMBER IS
    v_punkt SDO_GEOMETRY := SDO_GEOMETRY(2001, 8307,
                              SDO_POINT_TYPE(p_laenge, p_breite, NULL), NULL, NULL);
    v_id    NUMBER;
    v_km    NUMBER;
  BEGIN
    IF p_max_km IS NULL OR p_max_km <= 0 THEN
      RAISE_APPLICATION_ERROR(-20025, 'Suchradius muss positiv sein, nicht ' || p_max_km);
    END IF;
    -- SDO_NN liefert immer den Naechsten, egal wie weit; der Radius wird
    -- deshalb danach geprueft. SDO_NN_DISTANCE(1) gehoert zur Marke 1.
    -- FALLSTRICK: SDO_DISTANCE nimmt 'unit=M', SDO_NN nicht (ORA-13207
    -- INVALID UNITS). Hier deshalb 'unit=KM', wie in Oracles Beispielen.
    SELECT o.ort_id, SDO_NN_DISTANCE(1)
      INTO v_id, v_km
      FROM log_ort o
     WHERE SDO_NN(o.lage, v_punkt, 'sdo_num_res=1 unit=KM', 1) = 'TRUE';
    IF v_km > p_max_km THEN
      RETURN NULL;
    END IF;
    RETURN v_id;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RETURN NULL;
  END naechster_ort;

  -- -------------------------------------------------------------------
  FUNCTION teu_von(p_groesse_fuss IN NUMBER) RETURN NUMBER IS
  BEGIN
    RETURN CASE p_groesse_fuss WHEN 40 THEN 2 ELSE 1 END;
  END teu_von;

  -- -------------------------------------------------------------------
  PROCEDURE container_anlegen(p_kennung      IN VARCHAR2,
                              p_groesse_fuss IN NUMBER,
                              p_ware_code    IN VARCHAR2,
                              p_ort_id       IN NUMBER,
                              p_container_id OUT NUMBER) IS
    v_ware_id log_ware.ware_id%TYPE;
    v_ort_id  log_ort.ort_id%TYPE;
  BEGIN
    IF p_groesse_fuss NOT IN (20, 40) THEN
      RAISE_APPLICATION_ERROR(-20030, 'Containergroesse muss 20 oder 40 Fuss sein');
    END IF;

    BEGIN
      SELECT ware_id INTO v_ware_id
        FROM log_ware
       WHERE code = UPPER(TRIM(p_ware_code));
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20031, 'Ware ' || UPPER(TRIM(p_ware_code)) || ' unbekannt');
    END;

    BEGIN
      SELECT ort_id INTO v_ort_id FROM log_ort WHERE ort_id = p_ort_id;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20023, 'Ort ' || p_ort_id || ' gibt es nicht');
    END;

    INSERT INTO log_container (kennung, groesse_fuss, ware_id, ort_id)
    VALUES (NULLIF(TRIM(p_kennung), ''), p_groesse_fuss, v_ware_id, v_ort_id)
    RETURNING container_id INTO p_container_id;
  END container_anlegen;

  -- -------------------------------------------------------------------
  PROCEDURE ort_anlegen(p_name       IN VARCHAR2,
                        p_iso2       IN CHAR,
                        p_laenge     IN NUMBER,
                        p_breite     IN NUMBER,
                        p_kapazitaet IN NUMBER,
                        p_ort_id     OUT NUMBER) IS
    v_iso2 log_land.iso2%TYPE := UPPER(TRIM(p_iso2));
  BEGIN
    IF p_name IS NULL OR TRIM(p_name) IS NULL THEN
      RAISE_APPLICATION_ERROR(-20032, 'Stadtname darf nicht leer sein');
    END IF;
    BEGIN
      SELECT ort_id INTO p_ort_id FROM log_ort
       WHERE UPPER(name) = UPPER(TRIM(p_name));
      RAISE_APPLICATION_ERROR(-20032, 'Die Stadt ' || TRIM(p_name) || ' gibt es bereits');
    EXCEPTION
      WHEN NO_DATA_FOUND THEN NULL;
    END;
    BEGIN
      SELECT iso2 INTO v_iso2 FROM log_land WHERE iso2 = UPPER(TRIM(p_iso2));
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20033, 'Land ' || v_iso2 || ' gibt es nicht');
    END;
    IF p_laenge IS NULL OR p_breite IS NULL
       OR p_laenge < -180 OR p_laenge > 180
       OR p_breite < -90 OR p_breite > 90 THEN
      RAISE_APPLICATION_ERROR(-20034, 'Koordinaten liegen ausserhalb des gueltigen Bereichs');
    END IF;
    IF p_kapazitaet IS NOT NULL AND p_kapazitaet <= 0 THEN
      RAISE_APPLICATION_ERROR(-20035, 'Kapazitaet muss groesser als 0 sein');
    END IF;

    INSERT INTO log_ort (name, iso2, lage, kapazitaet_teu)
    VALUES (TRIM(p_name), v_iso2,
            SDO_GEOMETRY(2001, 8307,
                         SDO_POINT_TYPE(p_laenge, p_breite, NULL), NULL, NULL),
            p_kapazitaet)
    RETURNING ort_id INTO p_ort_id;
  END ort_anlegen;

  -- -------------------------------------------------------------------
  -- Die eigentliche Fahrt; p_ausgeloest sagt, wer sie wollte.
  PROCEDURE fahren(p_container_id IN  NUMBER,
                   p_nach_ort     IN  NUMBER,
                   p_ausgeloest   IN  VARCHAR2,
                   p_bewegung_id  OUT NUMBER) IS
    v_von_ort  log_container.ort_id%TYPE;
    v_status   log_container.status%TYPE;
    v_fuss     log_container.groesse_fuss%TYPE;
    v_ware     log_container.ware_id%TYPE;
    v_kap      log_ort.kapazitaet_teu%TYPE;
    v_ziel     log_ort.name%TYPE;
    v_belegt   NUMBER;
    v_zoll     VARCHAR2(1);
    v_m        NUMBER;
    v_teu      NUMBER;
    v_fracht   NUMBER;
    v_zoll_eur NUMBER;
  BEGIN
    -- Container sperren: zwei gleichzeitige Verschiebungen desselben
    -- Containers duerfen nicht beide vom alten Standort ausgehen.
    BEGIN
      SELECT ort_id, status, groesse_fuss, ware_id
        INTO v_von_ort, v_status, v_fuss, v_ware
        FROM log_container
       WHERE container_id = p_container_id
         FOR UPDATE;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20022,
          'Container ' || p_container_id || ' gibt es nicht');
    END;

    IF v_von_ort = p_nach_ort THEN
      RAISE_APPLICATION_ERROR(-20020,
        'Container ' || p_container_id || ' steht bereits am Zielort');
    END IF;

    IF v_status = 'ZOLL' THEN
      RAISE_APPLICATION_ERROR(-20021,
        'Container ' || p_container_id
        || ' steht beim Zoll und muss erst freigegeben werden');
    END IF;

    -- Zielstadt sperren: zwei Container, die gleichzeitig in dieselbe
    -- Stadt fahren, duerfen nicht beide den letzten Platz sehen.
    BEGIN
      SELECT kapazitaet_teu, name
        INTO v_kap, v_ziel
        FROM log_ort
       WHERE ort_id = p_nach_ort
         FOR UPDATE;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20023,
          'Zielort ' || p_nach_ort || ' gibt es nicht');
    END;

    v_teu := teu_von(v_fuss);
    IF v_kap IS NOT NULL THEN
      v_belegt := belegt_teu(p_nach_ort);
      IF v_belegt + v_teu > v_kap THEN
        RAISE_APPLICATION_ERROR(-20026,
          v_ziel || ' ist voll: ' || v_belegt || ' von ' || v_kap
          || ' TEU belegt, der Container braucht ' || v_teu);
      END IF;
    END IF;

    -- Alles vorher in Variablen: private Funktionen (teu_von) duerfen
    -- nicht in SQL stehen (PLS-00231).
    v_zoll     := zoll_noetig(v_von_ort, p_nach_ort);
    v_m        := distanz_m(v_von_ort, p_nach_ort);
    v_fracht   := fracht_eur(v_m, v_teu, v_ware);
    v_zoll_eur := CASE v_zoll WHEN 'J' THEN tarif('ZOLL_PAUSCHALE') ELSE 0 END;

    INSERT INTO log_bewegung
           (container_id, von_ort_id, nach_ort_id,
            von_zone, nach_zone, zoll, distanz_m,
            fracht_eur, zoll_eur, ausgeloest)
    VALUES (p_container_id, v_von_ort, p_nach_ort,
            zone_von(v_von_ort), zone_von(p_nach_ort), v_zoll, v_m,
            v_fracht, v_zoll_eur, p_ausgeloest)
    RETURNING bewegung_id INTO p_bewegung_id;

    UPDATE log_container
       SET ort_id       = p_nach_ort,
           status       = CASE v_zoll WHEN 'J' THEN 'ZOLL' ELSE 'BEREIT' END,
           geaendert_am = SYSTIMESTAMP
     WHERE container_id = p_container_id;
  END fahren;

  -- -------------------------------------------------------------------
  PROCEDURE verschieben(p_container_id IN  NUMBER,
                        p_nach_ort     IN  NUMBER,
                        p_bewegung_id  OUT NUMBER) IS
  BEGIN
    fahren(p_container_id, p_nach_ort, 'HAND', p_bewegung_id);
  END verschieben;

  -- -------------------------------------------------------------------
  PROCEDURE zoll_freigeben(p_container_id IN NUMBER) IS
    v_jetzt TIMESTAMP := SYSTIMESTAMP;
  BEGIN
    UPDATE log_container
       SET status       = 'BEREIT',
           geaendert_am = v_jetzt
     WHERE container_id = p_container_id
       AND status = 'ZOLL';
    IF SQL%ROWCOUNT = 0 THEN
      RAISE_APPLICATION_ERROR(-20024,
        'Container ' || p_container_id || ' steht nicht beim Zoll');
    END IF;
    -- Die Fahrt, mit der er an die Grenze kam: seine letzte
    UPDATE log_bewegung b
       SET b.freigegeben_am = v_jetzt,
           -- mit Package-Namen: Funktion und Spalte heissen gleich
           b.standgeld_eur  = log_api.standgeld_eur(b.zeitpunkt, v_jetzt)
     WHERE b.bewegung_id = (SELECT MAX(x.bewegung_id) FROM log_bewegung x
                             WHERE x.container_id = p_container_id)
       AND b.zoll = 'J'
       AND b.freigegeben_am IS NULL;
  END zoll_freigeben;

  -- ===================================================== Runde 2

  -- -------------------------------------------------------------------
  FUNCTION belegt_teu(p_ort_id IN NUMBER) RETURN NUMBER IS
    v_teu NUMBER;
  BEGIN
    SELECT NVL(SUM(CASE groesse_fuss WHEN 40 THEN 2 ELSE 1 END), 0)
      INTO v_teu
      FROM log_container
     WHERE ort_id = p_ort_id;
    RETURN v_teu;
  END belegt_teu;

  -- -------------------------------------------------------------------
  FUNCTION frei_teu(p_ort_id IN NUMBER) RETURN NUMBER IS
    v_kap log_ort.kapazitaet_teu%TYPE;
  BEGIN
    SELECT kapazitaet_teu INTO v_kap FROM log_ort WHERE ort_id = p_ort_id;
    IF v_kap IS NULL THEN
      RETURN NULL;
    END IF;
    RETURN v_kap - belegt_teu(p_ort_id);
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RETURN NULL;
  END frei_teu;

  -- -------------------------------------------------------------------
  FUNCTION tarif(p_schluessel IN VARCHAR2) RETURN NUMBER IS
    v_wert log_tarif.wert%TYPE;
  BEGIN
    SELECT wert INTO v_wert FROM log_tarif WHERE schluessel = p_schluessel;
    RETURN v_wert;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RETURN NULL;
  END tarif;

  -- -------------------------------------------------------------------
  FUNCTION fracht_eur(p_distanz_m IN NUMBER, p_teu IN NUMBER, p_ware_id IN NUMBER) RETURN NUMBER IS
    v_satz log_ware.fracht_eur_teu_km%TYPE;
  BEGIN
    SELECT fracht_eur_teu_km INTO v_satz FROM log_ware WHERE ware_id = p_ware_id;
    IF v_satz IS NULL OR p_distanz_m IS NULL THEN
      RETURN NULL;
    END IF;
    -- ROUND rundet ab ,5 von der Null weg - fuer positive Betraege
    -- dasselbe wie HALF_UP in Java (BigDecimal).
    RETURN ROUND(p_distanz_m / 1000 * p_teu * v_satz, 2);
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RETURN NULL;
  END fracht_eur;

  -- -------------------------------------------------------------------
  FUNCTION standgeld_eur(p_von IN TIMESTAMP, p_bis IN TIMESTAMP) RETURN NUMBER IS
    v_d    INTERVAL DAY(9) TO SECOND(6);
    v_sek  NUMBER;
    v_satz NUMBER := tarif('STANDGELD_VIERTELSTUNDE');
  BEGIN
    -- Ohne Tarif (Neuaufbau, bevor 10_daten2 lief) bleibt es leer und
    -- wird dort nachgerechnet - nicht still 0.
    IF p_von IS NULL OR p_bis IS NULL OR v_satz IS NULL THEN
      RETURN NULL;
    END IF;
    v_d   := p_bis - p_von;
    v_sek := EXTRACT(DAY FROM v_d) * 86400 + EXTRACT(HOUR FROM v_d) * 3600
           + EXTRACT(MINUTE FROM v_d) * 60 + EXTRACT(SECOND FROM v_d);
    IF v_sek <= 0 THEN
      RETURN 0;
    END IF;
    RETURN CEIL(v_sek / 900) * v_satz;
  END standgeld_eur;

  -- -------------------------------------------------------------------
  FUNCTION kosten_vorschau(p_container_id IN NUMBER, p_nach_ort IN NUMBER) RETURN NUMBER IS
    v_von  log_container.ort_id%TYPE;
    v_fuss log_container.groesse_fuss%TYPE;
    v_ware log_container.ware_id%TYPE;
  BEGIN
    SELECT ort_id, groesse_fuss, ware_id INTO v_von, v_fuss, v_ware
      FROM log_container WHERE container_id = p_container_id;
    IF v_von = p_nach_ort THEN
      RETURN 0;
    END IF;
    RETURN fracht_eur(distanz_m(v_von, p_nach_ort), teu_von(v_fuss), v_ware)
         + CASE zoll_noetig(v_von, p_nach_ort) WHEN 'J' THEN NVL(tarif('ZOLL_PAUSCHALE'), 0) ELSE 0 END;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RETURN NULL;
  END kosten_vorschau;

  -- -------------------------------------------------------------------
  PROCEDURE auftrag_anlegen(p_container_id IN  NUMBER,
                            p_nach_ort     IN  NUMBER,
                            p_faellig_am   IN  TIMESTAMP,
                            p_auftrag_id   OUT NUMBER) IS
    v_n NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_n FROM log_container WHERE container_id = p_container_id;
    IF v_n = 0 THEN
      RAISE_APPLICATION_ERROR(-20022, 'Container ' || p_container_id || ' gibt es nicht');
    END IF;
    SELECT COUNT(*) INTO v_n FROM log_ort WHERE ort_id = p_nach_ort;
    IF v_n = 0 THEN
      RAISE_APPLICATION_ERROR(-20023, 'Zielort ' || p_nach_ort || ' gibt es nicht');
    END IF;
    INSERT INTO log_auftrag (container_id, nach_ort_id, faellig_am)
    VALUES (p_container_id, p_nach_ort, NVL(p_faellig_am, SYSTIMESTAMP))
    RETURNING auftrag_id INTO p_auftrag_id;
  END auftrag_anlegen;

  -- -------------------------------------------------------------------
  PROCEDURE auftrag_stornieren(p_auftrag_id IN NUMBER) IS
    v_status log_auftrag.status%TYPE;
  BEGIN
    BEGIN
      SELECT status INTO v_status FROM log_auftrag WHERE auftrag_id = p_auftrag_id FOR UPDATE;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20027, 'Auftrag ' || p_auftrag_id || ' gibt es nicht');
    END;
    IF v_status <> 'OFFEN' THEN
      RAISE_APPLICATION_ERROR(-20028,
        'Auftrag ' || p_auftrag_id || ' ist nicht mehr offen (' || v_status || ')');
    END IF;
    UPDATE log_auftrag
       SET status = 'STORNIERT', erledigt_am = SYSTIMESTAMP, geaendert_am = SYSTIMESTAMP
     WHERE auftrag_id = p_auftrag_id;
  END auftrag_stornieren;

  -- -------------------------------------------------------------------
  PROCEDURE auftrag_ausfuehren(p_auftrag_id  IN  NUMBER,
                               p_von         IN  VARCHAR2,
                               p_ergebnis    OUT VARCHAR2,
                               p_bewegung_id OUT NUMBER) IS
    v_cont    log_auftrag.container_id%TYPE;
    v_nach    log_auftrag.nach_ort_id%TYPE;
    v_faellig log_auftrag.faellig_am%TYPE;
    v_status  log_auftrag.status%TYPE;
    v_ort     log_container.ort_id%TYPE;
    v_frueher NUMBER;
    v_n       NUMBER;
    v_von     VARCHAR2(4) := CASE WHEN p_von = 'JOB' THEN 'JOB' ELSE 'APP' END;
    v_code    NUMBER;
    v_text    VARCHAR2(300);
  BEGIN
    p_bewegung_id := NULL;
    -- SKIP LOCKED: fuehrt eine andere Sitzung ihn gerade aus, kommt
    -- keine Zeile zurueck - und wir warten nicht, sondern melden es.
    BEGIN
      SELECT container_id, nach_ort_id, faellig_am, status
        INTO v_cont, v_nach, v_faellig, v_status
        FROM log_auftrag
       WHERE auftrag_id = p_auftrag_id
         FOR UPDATE SKIP LOCKED;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        SELECT COUNT(*) INTO v_n FROM log_auftrag WHERE auftrag_id = p_auftrag_id;
        IF v_n = 0 THEN
          RAISE_APPLICATION_ERROR(-20027, 'Auftrag ' || p_auftrag_id || ' gibt es nicht');
        END IF;
        p_ergebnis := 'GESPERRT';
        RETURN;
    END;

    IF v_status <> 'OFFEN' THEN
      RAISE_APPLICATION_ERROR(-20028,
        'Auftrag ' || p_auftrag_id || ' ist nicht mehr offen (' || v_status || ')');
    END IF;

    IF v_faellig > SYSTIMESTAMP THEN
      p_ergebnis := 'NICHT_FAELLIG';
      RETURN;
    END IF;

    -- Terminfolge: ein frueherer offener Auftrag desselben Containers geht vor
    SELECT COUNT(*) INTO v_frueher
      FROM log_auftrag
     WHERE container_id = v_cont
       AND status = 'OFFEN'
       AND auftrag_id <> p_auftrag_id
       AND (faellig_am < v_faellig OR (faellig_am = v_faellig AND auftrag_id < p_auftrag_id));
    IF v_frueher > 0 THEN
      p_ergebnis := 'WARTET';
      RETURN;
    END IF;

    SELECT ort_id INTO v_ort FROM log_container WHERE container_id = v_cont;
    IF v_ort = v_nach THEN
      UPDATE log_auftrag
         SET status = 'ERLEDIGT', grund = 'stand schon am Ziel - ohne Fahrt',
             ausgefuehrt_von = v_von, erledigt_am = SYSTIMESTAMP, geaendert_am = SYSTIMESTAMP
       WHERE auftrag_id = p_auftrag_id;
      p_ergebnis := 'ERLEDIGT';
      RETURN;
    END IF;

    SAVEPOINT vor_auftrag;
    BEGIN
      fahren(v_cont, v_nach, CASE v_von WHEN 'JOB' THEN 'JOB' ELSE 'AUFTRAG' END, p_bewegung_id);
    EXCEPTION
      WHEN OTHERS THEN
        -- SQLCODE/SQLERRM erst in Variablen: in SQL sind sie nicht erlaubt
        v_code := SQLCODE;
        v_text := SUBSTR(REGEXP_REPLACE(SQLERRM, '^ORA-[0-9]+: '), 1, 300);
        ROLLBACK TO SAVEPOINT vor_auftrag;
        p_bewegung_id := NULL;
        IF v_code IN (-20021, -20026) THEN
          -- vorueber: beim Zoll oder Ziel voll. Offen lassen, neuer Versuch spaeter.
          UPDATE log_auftrag
             SET grund = v_text,
                 versuche = versuche + 1, geaendert_am = SYSTIMESTAMP
           WHERE auftrag_id = p_auftrag_id;
          p_ergebnis := 'BLOCKIERT';
        ELSE
          UPDATE log_auftrag
             SET status = 'GESCHEITERT',
                 grund = v_text,
                 versuche = versuche + 1, ausgefuehrt_von = v_von,
                 erledigt_am = SYSTIMESTAMP, geaendert_am = SYSTIMESTAMP
           WHERE auftrag_id = p_auftrag_id;
          p_ergebnis := 'GESCHEITERT';
        END IF;
        RETURN;
    END;

    UPDATE log_auftrag
       SET status = 'ERLEDIGT', grund = NULL, bewegung_id = p_bewegung_id,
           versuche = versuche + 1, ausgefuehrt_von = v_von,
           erledigt_am = SYSTIMESTAMP, geaendert_am = SYSTIMESTAMP
     WHERE auftrag_id = p_auftrag_id;
    p_ergebnis := 'ERLEDIGT';
  END auftrag_ausfuehren;

  -- -------------------------------------------------------------------
  FUNCTION stand_marke RETURN VARCHAR2 IS
    v_c VARCHAR2(40);
    v_b NUMBER;
    v_a VARCHAR2(40);
    v_n NUMBER;
  BEGIN
    SELECT TO_CHAR(MAX(geaendert_am), 'YYYYMMDDHH24MISSFF6') INTO v_c FROM log_container;
    SELECT MAX(bewegung_id) INTO v_b FROM log_bewegung;
    SELECT TO_CHAR(MAX(NVL(geaendert_am, angelegt_am)), 'YYYYMMDDHH24MISSFF6'), COUNT(*)
      INTO v_a, v_n FROM log_auftrag;
    RETURN v_c || '|' || v_b || '|' || v_a || '|' || v_n;
  END stand_marke;

END log_api;
/
