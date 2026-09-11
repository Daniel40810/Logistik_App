-- =====================================================================
-- Logistik_App / Schema DEMO
-- 04 - Sichten: das, was die Anwendung liest
--
-- Die Anwendung liest ausschliesslich ueber diese Sichten und schreibt
-- ausschliesslich ueber LOG_API. Die Tabellen bleiben dahinter frei
-- umbaubar.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Staedte mit Land, Zone und Koordinaten als Zahlen; seit Runde 2 mit
-- Kapazitaet, belegten und freien TEU (Zoll-Wartende belegen mit).
-- Attribute einer Objektspalte brauchen den Tabellen-Alias
-- (o.lage.sdo_point.x), sonst ORA-00904.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW log_ort_v AS
SELECT o.ort_id,
       o.name,
       o.iso2,
       l.name              AS land,
       l.zone_typ,
       l.eu_mitglied,
       l.zollunion,
       l.schengen,
       o.lage.sdo_point.x  AS laenge,
       o.lage.sdo_point.y  AS breite,
       o.kapazitaet_teu,
       NVL(b.teu, 0)       AS belegt_teu,
       o.kapazitaet_teu - NVL(b.teu, 0) AS frei_teu
  FROM log_ort  o
  JOIN log_land l ON l.iso2 = o.iso2
  LEFT JOIN (SELECT ort_id, SUM(CASE groesse_fuss WHEN 40 THEN 2 ELSE 1 END) AS teu
               FROM log_container
              GROUP BY ort_id) b ON b.ort_id = o.ort_id;

-- ---------------------------------------------------------------------
-- Container, fertig fuer die Karte: Farbe, Standort, Zone, ISO-Typcode.
--
-- Typcode nach ISO 6346: erste Ziffer Laenge (2 = 20 Fuss, 4 = 40 Fuss),
-- zweite Ziffer Hoehe (2 = 8'6"), dann G1 fuer Standard- und R1 fuer
-- Kuehlcontainer. Er wird abgeleitet, nicht gespeichert: eine
-- kuehlpflichtige Ware bekommt damit von selbst einen Reefer.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW log_container_v AS
SELECT c.container_id,
       c.kennung,
       log_api.kennung_anzeige(c.kennung)                 AS kennung_anzeige,
       c.groesse_fuss,
       CASE c.groesse_fuss WHEN 40 THEN '42' ELSE '22' END
       || CASE w.kuehlpflichtig WHEN 'J' THEN 'R1' ELSE 'G1' END AS typ_code,
       CASE c.groesse_fuss WHEN 40 THEN 2 ELSE 1 END      AS teu,
       c.ware_id,
       w.code                                            AS ware_code,
       w.name                                            AS ware,
       w.farbe_hex,
       c.ort_id,
       o.name                                            AS ort,
       o.zone_typ,
       o.laenge,
       o.breite,
       c.status,
       c.geaendert_am
  FROM log_container c
  JOIN log_ware  w ON w.ware_id = c.ware_id
  JOIN log_ort_v o ON o.ort_id  = c.ort_id;

-- ---------------------------------------------------------------------
-- Bestand je Stadt und Ware - fuer Legende und Bestandsliste.
-- Staedte ohne Container erscheinen nicht; wer alle Staedte braucht,
-- nimmt LOG_ORT_V dazu.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW log_bestand_v AS
SELECT c.ort_id,
       c.ort,
       c.zone_typ,
       c.ware_code,
       c.ware,
       c.farbe_hex,
       COUNT(*)                                          AS anzahl,
       SUM(c.teu)                                        AS teu,
       SUM(CASE c.status WHEN 'ZOLL' THEN 1 ELSE 0 END)  AS beim_zoll
  FROM log_container_v c
 GROUP BY c.ort_id, c.ort, c.zone_typ, c.ware_code, c.ware, c.farbe_hex;

-- ---------------------------------------------------------------------
-- Historie lesbar: Namen statt Schluessel, Kilometer statt Meter.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW log_bewegung_v AS
SELECT b.bewegung_id,
       b.container_id,
       log_api.kennung_anzeige(c.kennung)                AS kennung_anzeige,
       b.von_ort_id,
       v.name                                            AS von_ort,
       b.nach_ort_id,
       n.name                                            AS nach_ort,
       b.von_zone,
       b.nach_zone,
       CASE WHEN b.von_zone <> b.nach_zone THEN 'J' ELSE 'N' END AS zonenwechsel,
       b.zoll,
       b.distanz_m,
       ROUND(b.distanz_m / 1000, 1)                      AS distanz_km,
       b.zeitpunkt,
       b.fracht_eur,
       b.zoll_eur,
       b.standgeld_eur,
       NVL(b.fracht_eur, 0) + NVL(b.zoll_eur, 0) + NVL(b.standgeld_eur, 0) AS kosten_eur,
       b.freigegeben_am,
       b.ausgeloest,
       a.auftrag_id
  FROM log_bewegung  b
  JOIN log_container c ON c.container_id = b.container_id
  JOIN log_ort       v ON v.ort_id = b.von_ort_id
  JOIN log_ort       n ON n.ort_id = b.nach_ort_id
  LEFT JOIN log_auftrag a ON a.bewegung_id = b.bewegung_id;

-- ---------------------------------------------------------------------
-- Wer beim Zoll wartet, seit wann und an welcher Grenze.
--
-- "Seit" ist der Zeitpunkt der letzten Bewegung - mit ihr ist der
-- Container an die Grenze gekommen. Die Wartezeit rechnet die Datenbank
-- selbst aus (Sekunden), damit keine Uhr der Anwendung mitreden muss.
-- Die Grenze nennt beide Laender, weil die Zone allein nicht verraet,
-- welche Zollgrenze es war (Zuerich -> Oslo: Drittland -> Drittland).
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW log_zoll_v AS
SELECT c.container_id,
       log_api.kennung_anzeige(c.kennung)                         AS kennung_anzeige,
       w.name                                                     AS ware,
       w.farbe_hex,
       c.ort_id,
       n.name                                                     AS ort,
       v.name                                                     AS von_ort,
       v.iso2                                                     AS von_land,
       n.iso2                                                     AS nach_land,
       b.von_zone,
       b.nach_zone,
       b.zeitpunkt                                                AS seit,
       ROUND((CAST(SYSTIMESTAMP AS DATE) - CAST(b.zeitpunkt AS DATE)) * 86400) AS wartet_sek,
       log_api.standgeld_eur(b.zeitpunkt, SYSTIMESTAMP)           AS standgeld_bisher
  FROM log_container c
  JOIN log_ware      w ON w.ware_id = c.ware_id
  JOIN log_bewegung  b ON b.container_id = c.container_id
  JOIN log_ort       v ON v.ort_id = b.von_ort_id
  JOIN log_ort       n ON n.ort_id = b.nach_ort_id
 WHERE c.status = 'ZOLL'
   AND b.bewegung_id = (SELECT MAX(b2.bewegung_id) FROM log_bewegung b2
                         WHERE b2.container_id = c.container_id);

-- ---------------------------------------------------------------------
-- Auftraege mit Namen, Kennung und Warenfarbe (Runde 2).
--
-- RANG ist der Platz in der Kette des Containers: nur Rang 1 eines
-- offenen Auftrags ist ausfuehrbar (Terminfolge). ORT_JETZT ist, wo der
-- Container heute steht - von dort faehrt er, wenn es so weit ist.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW log_auftrag_v AS
SELECT a.auftrag_id,
       a.container_id,
       log_api.kennung_anzeige(c.kennung)                         AS kennung_anzeige,
       w.name                                                     AS ware,
       w.farbe_hex,
       c.ort_id                                                   AS ort_jetzt_id,
       j.name                                                     AS ort_jetzt,
       c.status                                                   AS container_status,
       a.nach_ort_id,
       n.name                                                     AS nach_ort,
       a.faellig_am,
       a.status,
       a.grund,
       a.versuche,
       a.bewegung_id,
       a.ausgefuehrt_von,
       a.angelegt_am,
       a.erledigt_am,
       CASE WHEN a.status = 'OFFEN' AND a.faellig_am <= SYSTIMESTAMP THEN 'J' ELSE 'N' END AS faellig,
       CASE WHEN a.status = 'OFFEN'
            THEN ROW_NUMBER() OVER (PARTITION BY a.container_id, a.status
                                    ORDER BY a.faellig_am, a.auftrag_id) END AS rang
  FROM log_auftrag   a
  JOIN log_container c ON c.container_id = a.container_id
  JOIN log_ware      w ON w.ware_id = c.ware_id
  JOIN log_ort       j ON j.ort_id = c.ort_id
  JOIN log_ort       n ON n.ort_id = a.nach_ort_id;

-- ---------------------------------------------------------------------
-- Kosten je Tag, Zonenpaar und Ware (Runde 2). Standgeld zaehlt erst
-- ab der Freigabe - solange einer wartet, steht es in LOG_ZOLL_V.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW log_kosten_v AS
SELECT TRUNC(b.zeitpunkt)                                         AS tag,
       b.von_zone,
       b.nach_zone,
       w.code                                                     AS ware_code,
       w.name                                                     AS ware,
       COUNT(*)                                                   AS fahrten,
       SUM(CASE b.zoll WHEN 'J' THEN 1 ELSE 0 END)                AS zollfahrten,
       ROUND(SUM(b.distanz_m) / 1000, 1)                          AS km,
       SUM(NVL(b.fracht_eur, 0))                                  AS fracht_eur,
       SUM(NVL(b.zoll_eur, 0))                                    AS zoll_eur,
       SUM(NVL(b.standgeld_eur, 0))                               AS standgeld_eur,
       SUM(NVL(b.fracht_eur, 0) + NVL(b.zoll_eur, 0) + NVL(b.standgeld_eur, 0)) AS kosten_eur
  FROM log_bewegung  b
  JOIN log_container c ON c.container_id = b.container_id
  JOIN log_ware      w ON w.ware_id = c.ware_id
 GROUP BY TRUNC(b.zeitpunkt), b.von_zone, b.nach_zone, w.code, w.name;
