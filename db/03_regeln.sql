-- =====================================================================
-- Logistik_App / Schema DEMO
-- 03 - Regeln, die LOG_API brauchen und deshalb nach 02 kommen
-- =====================================================================

-- ---------------------------------------------------------------------
-- Container: Schluessel und Kennung.
--
-- Ohne Kennung bekommt ein neuer Container die naechste DANU-Kennung,
-- deren Seriennummer aus dem Schluessel folgt (Container 1 -> DANU
-- 100001 5). Wer eine Kennung mitbringt - etwa einen Fremdcontainer
-- mit anderem Eigentuemer -, bekommt sie geprueft statt ueberschrieben.
--
-- Leerzeichen und Kleinschreibung werden vor der Pruefung entfernt,
-- gespeichert wird immer die kompakte Form ('DANU1000015').
-- ---------------------------------------------------------------------
CREATE OR REPLACE TRIGGER log_container_biu
  BEFORE INSERT OR UPDATE OF kennung ON log_container
  FOR EACH ROW
BEGIN
  IF INSERTING AND :new.container_id IS NULL THEN
    :new.container_id := log_container_seq.NEXTVAL;
  END IF;

  IF INSERTING AND :new.kennung IS NULL THEN
    :new.kennung := log_api.neue_kennung(100000 + :new.container_id);
  END IF;

  :new.kennung := UPPER(REPLACE(:new.kennung, ' '));

  IF log_api.kennung_gueltig(:new.kennung) = 'N' THEN
    RAISE_APPLICATION_ERROR(-20010,
      'Kennung ' || :new.kennung || ' verstoesst gegen ISO 6346 '
      || '(Format AAAU0000000 oder Pruefziffer falsch)');
  END IF;
END;
/
