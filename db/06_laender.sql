-- =====================================================================
-- Logistik_App / Schema DEMO
-- 06 - Die uebrigen Laender Europas und seiner Nachbarschaft
--
-- Phase 2 braucht eine Karte, und auf einer Karte liegt jedes Land, das
-- man sieht - nicht nur die sieben mit Staedten. Jedes bekommt seine
-- echten Zugehoerigkeiten, damit die Zonenfarbe auf der Karte stimmt
-- und eine spaeter angelegte Stadt (Wien, Rotterdam, Istanbul ...) ohne
-- weitere Pflege die richtige Zone und die richtige Zollregel hat.
--
-- Stand September 2026:
--   EU            27 Staaten. Aland ist autonomes Gebiet Finnlands und
--                 in Natural Earth ein eigenes Feature - es bekommt
--                 Finnlands Werte.
--   Schengen      29 Staaten: EU ohne Irland und Zypern, dazu Norwegen,
--                 Island, Schweiz, Liechtenstein. Zypern strebt den
--                 Beitritt 2026 an, ist aber noch nicht Mitglied -
--                 wenn es so weit ist, EINE Zeile aendern.
--   Zollunion     EU, dazu Monaco und San Marino. Andorra und die Tuerkei
--                 haben nur Teil-Zollunionen (gewerbliche Waren); fuer
--                 den Zollschritt gelten sie als ausserhalb.
--
-- MERGE statt INSERT: das Skript laesst sich einzeln wiederholen.
-- Die sieben Laender aus 05_daten.sql bleiben unberuehrt.
-- =====================================================================

DECLARE
  PROCEDURE land(p_iso VARCHAR2, p_name VARCHAR2,
                 p_eu VARCHAR2, p_zu VARCHAR2, p_sch VARCHAR2) IS
  BEGIN
    MERGE INTO log_land l
    USING (SELECT p_iso AS iso2 FROM dual) s
       ON (l.iso2 = s.iso2)
     WHEN MATCHED THEN UPDATE
          SET l.name = p_name, l.eu_mitglied = p_eu,
              l.zollunion = p_zu, l.schengen = p_sch
     WHEN NOT MATCHED THEN INSERT (iso2, name, inland, eu_mitglied, zollunion, schengen)
          VALUES (p_iso, p_name, 'N', p_eu, p_zu, p_sch);
  END land;
BEGIN
  -- EU und Schengen
  land('AT', 'Österreich',   'J', 'J', 'J');
  land('BE', 'Belgien',      'J', 'J', 'J');
  land('BG', 'Bulgarien',    'J', 'J', 'J');
  land('CZ', 'Tschechien',   'J', 'J', 'J');
  land('DK', 'Dänemark',     'J', 'J', 'J');
  land('EE', 'Estland',      'J', 'J', 'J');
  land('FI', 'Finnland',     'J', 'J', 'J');
  land('AX', 'Åland',        'J', 'J', 'J');
  land('GR', 'Griechenland', 'J', 'J', 'J');
  land('HR', 'Kroatien',     'J', 'J', 'J');
  land('HU', 'Ungarn',       'J', 'J', 'J');
  land('IT', 'Italien',      'J', 'J', 'J');
  land('LT', 'Litauen',      'J', 'J', 'J');
  land('LU', 'Luxemburg',    'J', 'J', 'J');
  land('LV', 'Lettland',     'J', 'J', 'J');
  land('MT', 'Malta',        'J', 'J', 'J');
  land('NL', 'Niederlande',  'J', 'J', 'J');
  land('PT', 'Portugal',     'J', 'J', 'J');
  land('RO', 'Rumänien',     'J', 'J', 'J');
  land('SE', 'Schweden',     'J', 'J', 'J');
  land('SI', 'Slowenien',    'J', 'J', 'J');
  land('SK', 'Slowakei',     'J', 'J', 'J');

  -- EU ohne Schengen
  land('IE', 'Irland',       'J', 'J', 'N');
  land('CY', 'Zypern',       'J', 'J', 'N');

  -- Schengen ohne EU
  land('IS', 'Island',        'N', 'N', 'J');
  land('LI', 'Liechtenstein', 'N', 'N', 'J');

  -- Zollunion ohne EU
  land('MC', 'Monaco',        'N', 'J', 'N');
  land('SM', 'San Marino',    'N', 'J', 'N');

  -- Drittlaender
  land('AD', 'Andorra',                 'N', 'N', 'N');
  land('AL', 'Albanien',                'N', 'N', 'N');
  land('AM', 'Armenien',                'N', 'N', 'N');
  land('AZ', 'Aserbaidschan',           'N', 'N', 'N');
  land('BA', 'Bosnien und Herzegowina', 'N', 'N', 'N');
  land('BY', 'Belarus',                 'N', 'N', 'N');
  land('DZ', 'Algerien',                'N', 'N', 'N');
  land('FO', 'Färöer',                  'N', 'N', 'N');
  land('GE', 'Georgien',                'N', 'N', 'N');
  land('GG', 'Guernsey',                'N', 'N', 'N');
  land('GI', 'Gibraltar',               'N', 'N', 'N');
  land('GL', 'Grönland',                'N', 'N', 'N');
  land('IM', 'Isle of Man',             'N', 'N', 'N');
  land('IQ', 'Irak',                    'N', 'N', 'N');
  land('IR', 'Iran',                    'N', 'N', 'N');
  land('JE', 'Jersey',                  'N', 'N', 'N');
  land('LB', 'Libanon',                 'N', 'N', 'N');
  land('MA', 'Marokko',                 'N', 'N', 'N');
  land('MD', 'Moldau',                  'N', 'N', 'N');
  land('ME', 'Montenegro',              'N', 'N', 'N');
  land('MK', 'Nordmazedonien',          'N', 'N', 'N');
  land('RS', 'Serbien',                 'N', 'N', 'N');
  land('RU', 'Russland',                'N', 'N', 'N');
  land('SY', 'Syrien',                  'N', 'N', 'N');
  land('TN', 'Tunesien',                'N', 'N', 'N');
  land('TR', 'Türkei',                  'N', 'N', 'N');
  land('UA', 'Ukraine',                 'N', 'N', 'N');
  land('XK', 'Kosovo',                  'N', 'N', 'N');
  COMMIT;
END;
/

-- Kurzkontrolle: Erwartet INLAND 1, EU 27 (26 Staaten ohne DE plus
-- Aland), DRITTLAND 35.
SELECT zone_typ, COUNT(*) AS laender FROM log_land GROUP BY zone_typ ORDER BY zone_typ;
