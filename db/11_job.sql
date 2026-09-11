-- =====================================================================
-- Logistik_App / Schema DEMO
-- 11 - Auffang-Job fuer Auftraege
--
-- Die App fuehrt faellige Auftraege selbst aus, sichtbar mit Animation.
-- Dieser Job faengt nur auf, was LAENGER als die Karenz (LOG_TARIF
-- JOB_KARENZ_MIN) ueberfaellig ist - also praktisch nur, wenn keine
-- App laeuft. Doppelt gefahren wird nie: LOG_API.auftrag_ausfuehren
-- sperrt die Auftragszeile (SKIP LOCKED).
--
-- LOG_JOB ist das einzige Package, das committet - je Auftrag einmal,
-- damit ein blockierter Auftrag die anderen nicht aufhaelt.
--
-- Braucht:  GRANT CREATE JOB TO demo;   (als DBA in PDBORCL)
-- =====================================================================

CREATE OR REPLACE PACKAGE log_job AS

  c_job CONSTANT VARCHAR2(30) := 'LOG_AUFTRAG_JOB';

  -- Fuehrt alle offenen Auftraege aus, die mindestens p_karenz_min
  -- Minuten ueberfaellig sind (NULL = Tarif JOB_KARENZ_MIN), in
  -- Terminfolge. Committet je Auftrag. Liefert, wie viele gefahren sind.
  FUNCTION faellige_ausfuehren(p_karenz_min IN NUMBER DEFAULT NULL) RETURN NUMBER;

  -- Fuer den Scheduler: dasselbe ohne Rueckgabewert.
  PROCEDURE lauf;

END log_job;
/

CREATE OR REPLACE PACKAGE BODY log_job AS

  FUNCTION faellige_ausfuehren(p_karenz_min IN NUMBER DEFAULT NULL) RETURN NUMBER IS
    v_karenz   NUMBER := NVL(p_karenz_min, NVL(log_api.tarif('JOB_KARENZ_MIN'), 2));
    v_grenze   TIMESTAMP := SYSTIMESTAMP - NUMTODSINTERVAL(v_karenz, 'MINUTE');
    v_ergebnis VARCHAR2(20);
    v_bew      NUMBER;
    v_gefahren NUMBER := 0;
    TYPE t_ids IS TABLE OF NUMBER;
    v_ids      t_ids;
  BEGIN
    -- Erst die Liste, dann einzeln: so wird jeder Auftrag je Lauf genau
    -- einmal versucht, auch wenn er blockiert bleibt.
    SELECT auftrag_id BULK COLLECT INTO v_ids
      FROM log_auftrag
     WHERE status = 'OFFEN'
       AND faellig_am <= v_grenze
     ORDER BY faellig_am, auftrag_id;

    FOR i IN 1 .. v_ids.COUNT LOOP
      BEGIN
        log_api.auftrag_ausfuehren(v_ids(i), 'JOB', v_ergebnis, v_bew);
        COMMIT;
        IF v_ergebnis = 'ERLEDIGT' AND v_bew IS NOT NULL THEN
          v_gefahren := v_gefahren + 1;
        END IF;
      EXCEPTION
        WHEN OTHERS THEN
          -- ein kaputter Auftrag haelt die anderen nicht auf
          ROLLBACK;
      END;
    END LOOP;
    RETURN v_gefahren;
  END faellige_ausfuehren;

  PROCEDURE lauf IS
    v_n NUMBER;
  BEGIN
    v_n := faellige_ausfuehren;
  END lauf;

END log_job;
/

-- Den Job neu anlegen: erst einen alten entfernen (-27475 = gibt es nicht).
BEGIN
  BEGIN
    DBMS_SCHEDULER.DROP_JOB(job_name => 'LOG_AUFTRAG_JOB', force => TRUE);
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE <> -27475 THEN
        RAISE;
      END IF;
  END;
  DBMS_SCHEDULER.CREATE_JOB(
    job_name        => 'LOG_AUFTRAG_JOB',
    job_type        => 'PLSQL_BLOCK',
    job_action      => 'BEGIN log_job.lauf; END;',
    start_date      => SYSTIMESTAMP,
    repeat_interval => 'FREQ=MINUTELY;INTERVAL=1',
    enabled         => TRUE,
    comments        => 'Logistik_App: faengt ueberfaellige Auftraege auf, wenn keine App sie gefahren hat');
END;
/
