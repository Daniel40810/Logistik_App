-- =====================================================================
-- Logistik_App / Schema DEMO
-- 10 - Runde 2: Tarife, Kapazitaeten, Frachtsaetze, alte Fahrten
--
-- Darf mehrfach laufen. Beim Neuaufbau kommt es nach 05 (die
-- Beispielfahrten entstehen dort noch ohne Kosten) und rechnet sie
-- hier nach - genau wie die Historie einer bestehenden DEMO. So enden
-- beide Wege beim selben Stand.
--
-- Alle Betraege sind ausgedacht. Geaendert wird in LOG_TARIF und
-- LOG_WARE; die Historie behaelt, was bei der Fahrt galt.
-- =====================================================================

MERGE INTO log_tarif t
USING (SELECT 'ZOLL_PAUSCHALE' schluessel, 85 wert, 'EUR' einheit,
              'Je Fahrt ueber eine Zollgrenze' beschreibung FROM dual
       UNION ALL
       SELECT 'STANDGELD_VIERTELSTUNDE', 3, 'EUR',
              'Je angefangene Viertelstunde zwischen Ankunft und Zollfreigabe' FROM dual
       UNION ALL
       SELECT 'JOB_KARENZ_MIN', 2, 'min',
              'So lange muss ein Auftrag ueberfaellig sein, bevor der Job ihn statt der App faehrt' FROM dual) n
   ON (t.schluessel = n.schluessel)
 WHEN NOT MATCHED THEN
   INSERT (schluessel, wert, einheit, beschreibung)
   VALUES (n.schluessel, n.wert, n.einheit, n.beschreibung);

-- Kapazitaeten (Entscheidung 11.09.: grosszuegig) - nur wo noch keine steht
UPDATE log_ort o
   SET o.kapazitaet_teu = CASE o.name
         WHEN 'Hamburg'   THEN 16
         WHEN 'Berlin'    THEN 12
         WHEN 'Köln'      THEN 12
         WHEN 'München'   THEN 12
         WHEN 'Paris'     THEN 10
         WHEN 'Warschau'  THEN 10
         WHEN 'Barcelona' THEN 10
         WHEN 'London'    THEN 8
         WHEN 'Oslo'      THEN 8
         WHEN 'Zürich'    THEN 8
       END
 WHERE o.kapazitaet_teu IS NULL
   AND o.name IN ('Hamburg', 'Berlin', 'Köln', 'München', 'Paris', 'Warschau',
                  'Barcelona', 'London', 'Oslo', 'Zürich');

-- Frachtsaetze in EUR je TEU-km - nur wo noch keiner steht
UPDATE log_ware w
   SET w.fracht_eur_teu_km = CASE w.code
         WHEN 'EISEN'     THEN 1.30
         WHEN 'WEIZEN'    THEN 1.00
         WHEN 'HOLZ'      THEN 0.95
         WHEN 'KOHLE'     THEN 1.10
         WHEN 'MASCHINEN' THEN 1.45
         WHEN 'KUEHLWARE' THEN 1.90
       END
 WHERE w.fracht_eur_teu_km IS NULL
   AND w.code IN ('EISEN', 'WEIZEN', 'HOLZ', 'KOHLE', 'MASCHINEN', 'KUEHLWARE');

-- ---------------------------------------------------------------------
-- Alte Zollfahrten: wann wurden sie freigegeben? Das wurde vor Runde 2
-- nicht festgehalten. Beste Annahme: spaetestens, als der Container
-- wieder losfuhr (Beginn seiner naechsten Fahrt); war es seine letzte
-- und er steht heute bereit, dann bei seiner letzten Aenderung.
-- Wer heute noch beim Zoll steht, bleibt ohne Freigabe.
-- ---------------------------------------------------------------------
UPDATE log_bewegung b
   SET b.freigegeben_am = NVL(
         (SELECT MIN(n.zeitpunkt) FROM log_bewegung n
           WHERE n.container_id = b.container_id
             AND n.bewegung_id > b.bewegung_id),
         (SELECT GREATEST(NVL(c.geaendert_am, b.zeitpunkt), b.zeitpunkt) FROM log_container c
           WHERE c.container_id = b.container_id
             AND c.status = 'BEREIT'))
 WHERE b.zoll = 'J'
   AND b.freigegeben_am IS NULL;

UPDATE log_bewegung b
   SET b.standgeld_eur = log_api.standgeld_eur(b.zeitpunkt, b.freigegeben_am)
 WHERE b.freigegeben_am IS NOT NULL
   AND b.standgeld_eur IS NULL;

-- Fracht und Zoll mit dem Starttarif - einen "damaligen" gab es nicht
UPDATE log_bewegung b
   SET (b.fracht_eur, b.zoll_eur) = (
         SELECT log_api.fracht_eur(b.distanz_m, CASE c.groesse_fuss WHEN 40 THEN 2 ELSE 1 END, c.ware_id),
                CASE b.zoll WHEN 'J' THEN log_api.tarif('ZOLL_PAUSCHALE') ELSE 0 END
           FROM log_container c
          WHERE c.container_id = b.container_id)
 WHERE b.fracht_eur IS NULL
    OR b.zoll_eur IS NULL;

COMMIT;
