-- =====================================================================
-- Logistik_App / Schema DEMO
-- 12 - Weitere europaeische Containerstandorte (insgesamt rund 90)
--
-- Laeuft nach 10_daten2.sql und ist idempotent: bestehende Staedte
-- bleiben unveraendert, neue Staedte werden nur einmal angelegt.
-- =====================================================================

DECLARE
  PROCEDURE stadt(p_name VARCHAR2, p_iso2 VARCHAR2,
                  p_laenge NUMBER, p_breite NUMBER, p_kapazitaet NUMBER) IS
    v_n NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_n FROM log_ort WHERE name = p_name;
    IF v_n = 0 THEN
      INSERT INTO log_ort (name, iso2, lage, kapazitaet_teu)
      VALUES (p_name, p_iso2,
              SDO_GEOMETRY(2001, 8307,
                SDO_POINT_TYPE(p_laenge, p_breite, NULL), NULL, NULL),
              p_kapazitaet);
    END IF;
  END stadt;
BEGIN
  stadt('Amsterdam',  'NL',  4.9041, 52.3676, 12);
  stadt('Rotterdam',  'NL',  4.4777, 51.9244, 16);
  stadt('Brüssel',    'BE',  4.3517, 50.8503, 10);
  stadt('Kopenhagen', 'DK', 12.5683, 55.6761, 10);
  stadt('Prag',       'CZ', 14.4378, 50.0755, 10);
  stadt('Wien',       'AT', 16.3738, 48.2082, 12);
  stadt('Mailand',    'IT',  9.1900, 45.4642, 12);
  stadt('Madrid',     'ES', -3.7038, 40.4168, 10);

  -- Deutschland
  stadt('Frankfurt am Main', 'DE',  8.6821, 50.1109, 16);
  stadt('Stuttgart',         'DE',  9.1829, 48.7758, 12);
  stadt('Düsseldorf',        'DE',  6.7735, 51.2277, 12);
  stadt('Leipzig',           'DE', 12.3731, 51.3397, 10);
  stadt('Bremen',            'DE',  8.8017, 53.0793, 12);
  stadt('Hannover',          'DE',  9.7320, 52.3759, 10);
  stadt('Nürnberg',          'DE', 11.0767, 49.4521, 10);
  stadt('Dresden',           'DE', 13.7373, 51.0504, 10);

  -- Frankreich
  stadt('Marseille',   'FR',  5.3698, 43.2965, 14);
  stadt('Lyon',        'FR',  4.8357, 45.7640, 12);
  stadt('Toulouse',    'FR',  1.4442, 43.6047, 10);
  stadt('Lille',       'FR',  3.0573, 50.6292, 10);
  stadt('Bordeaux',    'FR', -0.5792, 44.8378, 10);
  stadt('Nantes',      'FR', -1.5536, 47.2184, 10);
  stadt('Straßburg',   'FR',  7.7521, 48.5734, 10);

  -- Polen
  stadt('Krakau',  'PL', 19.9445, 50.0647, 12);
  stadt('Danzig',   'PL', 18.6466, 54.3520, 12);
  stadt('Breslau',  'PL', 17.0385, 51.1079, 10);
  stadt('Posen',    'PL', 16.9252, 52.4064, 10);
  stadt('Łódź',     'PL', 19.4550, 51.7592, 10);

  -- Spanien und Italien
  stadt('Valencia',  'ES', -0.3763, 39.4699, 14);
  stadt('Sevilla',   'ES', -5.9845, 37.3891, 10);
  stadt('Málaga',    'ES', -4.4214, 36.7213, 10);
  stadt('Bilbao',    'ES', -2.9350, 43.2630, 10);
  stadt('Rom',       'IT', 12.4964, 41.9028, 14);
  stadt('Turin',     'IT',  7.6869, 45.0703, 12);
  stadt('Neapel',    'IT', 14.2681, 40.8518, 10);
  stadt('Genua',     'IT',  8.9463, 44.4056, 12);
  stadt('Bologna',   'IT', 11.3426, 44.4949, 10);
  stadt('Venedig',   'IT', 12.3155, 45.4408, 10);

  -- Benelux, Oesterreich und Tschechien
  stadt('Utrecht',    'NL',  5.1214, 52.0907, 10);
  stadt('Eindhoven',  'NL',  5.4697, 51.4416, 10);
  stadt('Antwerpen',  'BE',  4.4025, 51.2194, 14);
  stadt('Gent',       'BE',  3.7174, 51.0543, 10);
  stadt('Salzburg',   'AT', 13.0550, 47.8095, 10);
  stadt('Graz',       'AT', 15.4395, 47.0707, 10);
  stadt('Linz',       'AT', 14.2858, 48.3069, 10);
  stadt('Brünn',      'CZ', 16.6068, 49.1951, 10);
  stadt('Ostrava',    'CZ', 18.2625, 49.8209, 10);

  -- Daenemark, Schweden und Finnland
  stadt('Aarhus',    'DK', 10.2039, 56.1629, 10);
  stadt('Odense',    'DK', 10.4024, 55.4038,  8);
  stadt('Stockholm', 'SE', 18.0686, 59.3293, 12);
  stadt('Göteborg',  'SE', 11.9746, 57.7089, 12);
  stadt('Malmö',     'SE', 13.0038, 55.6050, 10);
  stadt('Helsinki',  'FI', 24.9384, 60.1699, 12);
  stadt('Tampere',   'FI', 23.7600, 61.4978,  8);

  -- Irland, Portugal und Griechenland
  stadt('Dublin',       'IE', -6.2603, 53.3498, 12);
  stadt('Cork',         'IE', -8.4863, 51.8985,  8);
  stadt('Lissabon',     'PT', -9.1393, 38.7223, 12);
  stadt('Porto',        'PT', -8.6291, 41.1579, 10);
  stadt('Athen',        'GR', 23.7275, 37.9838, 12);
  stadt('Thessaloniki', 'GR', 22.9444, 40.6401, 10);

  -- Suedosteuropa und Osten
  stadt('Budapest',   'HU', 19.0402, 47.4979, 12);
  stadt('Bukarest',   'RO', 26.1025, 44.4268, 12);
  stadt('Cluj-Napoca','RO', 23.6236, 46.7712,  8);
  stadt('Sofia',      'BG', 23.3219, 42.6977, 10);
  stadt('Zagreb',     'HR', 15.9819, 45.8150, 10);
  stadt('Ljubljana',  'SI', 14.5058, 46.0569,  8);
  stadt('Bratislava', 'SK', 17.1077, 48.1486,  8);
  stadt('Belgrad',    'RS', 20.4489, 44.7866, 10);
  stadt('Istanbul',   'TR', 28.9784, 41.0082, 14);
  stadt('Kyjiw',      'UA', 30.5234, 50.4501, 10);
  stadt('Moskau',     'RU', 37.6173, 55.7558, 12);

  -- Schweiz, Norwegen und Vereinigtes Koenigreich
  stadt('Genf',       'CH',  6.1432, 46.2044, 10);
  stadt('Basel',      'CH',  7.5886, 47.5596, 10);
  stadt('Bern',       'CH',  7.4474, 46.9480,  8);
  stadt('Bergen',     'NO',  5.3221, 60.3913,  8);
  stadt('Trondheim',  'NO', 10.3951, 63.4305,  8);
  stadt('Manchester', 'GB', -2.2426, 53.4808, 12);
  stadt('Liverpool',  'GB', -2.9916, 53.4084, 10);
  stadt('Edinburgh',  'GB', -3.1883, 55.9533, 10);
  COMMIT;
END;
/

SELECT COUNT(*) AS staedte_gesamt FROM log_ort_v;
