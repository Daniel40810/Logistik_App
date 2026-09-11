-- =====================================================================
-- Logistik_App / Schema DEMO
-- 00 - Aufraeumen. Faellt nicht um, wenn noch nichts existiert.
--
-- Beruehrt ausschliesslich Objekte mit dem Praefix LOG_. Die FCF_*-
-- Objekte von FCurvedField im selben Schema bleiben unangetastet.
-- =====================================================================

-- Zuerst der Auffang-Job (Runde 2) - er ruft LOG_JOB und darf nicht
-- mitten im Abbau laufen. -27475 = gibt es nicht.
BEGIN
  DBMS_SCHEDULER.DROP_JOB(job_name => 'LOG_AUFTRAG_JOB', force => TRUE);
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- Namentlich statt per LIKE 'LOG_%': im selben Schema koennte ein
-- fremdes Objekt zufaellig mit LOG_ beginnen.
BEGIN
  FOR o IN (SELECT object_name, object_type
              FROM user_objects
             WHERE object_type IN ('PACKAGE', 'VIEW')
               AND object_name IN ('LOG_API', 'LOG_JOB', 'LOG_ORT_V', 'LOG_CONTAINER_V',
                                   'LOG_BESTAND_V', 'LOG_BEWEGUNG_V',
                                   'LOG_LAND_V', 'LOG_ZOLL_V',
                                   'LOG_AUFTRAG_V', 'LOG_KOSTEN_V')) LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP ' || o.object_type || ' ' || o.object_name;
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
  END LOOP;
END;
/

-- Reihenfolge egal: CASCADE CONSTRAINTS nimmt die Fremdschluessel mit.
BEGIN
  FOR t IN (SELECT table_name FROM user_tables
             WHERE table_name IN ('LOG_AUFTRAG', 'LOG_TARIF',
                                  'LOG_BEWEGUNG', 'LOG_CONTAINER', 'LOG_WARE',
                                  'LOG_ORT', 'LOG_LAND')) LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
  END LOOP;
END;
/

BEGIN
  DELETE FROM user_sdo_geom_metadata
   WHERE table_name IN ('LOG_ORT', 'LOG_LAND');
  COMMIT;
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- Die Trigger fallen mit ihren Tabellen, die Sequenzen nicht.
BEGIN
  FOR q IN (SELECT sequence_name FROM user_sequences
             WHERE sequence_name IN ('LOG_ORT_SEQ', 'LOG_WARE_SEQ',
                                     'LOG_CONTAINER_SEQ', 'LOG_BEWEGUNG_SEQ',
                                     'LOG_AUFTRAG_SEQ')) LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP SEQUENCE ' || q.sequence_name;
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
  END LOOP;
END;
/
