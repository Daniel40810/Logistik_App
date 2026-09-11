package com.dan.logistikapp.db;

import com.dan.fdal.FSql;
import com.dan.logistikapp.karte.KartenQuelle;
import com.dan.logistikapp.model.ContainerInfo;
import com.dan.logistikapp.model.Land;
import com.dan.logistikapp.model.Ort;
import com.dan.logistikapp.model.Ware;

import java.util.List;

/**
 * Kartendaten aus dem Schema DEMO.
 *
 * @author Dan
 */
public final class DbKartenQuelle implements KartenQuelle {

    private final LandRepository laender;
    private final OrtRepository orte;
    private final WareRepository waren;
    private final ContainerRepository container;
    private final TarifRepository tarif;

    public DbKartenQuelle(FSql sql) {
        this.laender = new LandRepository(sql);
        this.orte = new OrtRepository(sql);
        this.waren = new WareRepository(sql);
        this.container = new ContainerRepository(sql);
        this.tarif = new TarifRepository(sql);
    }

    @Override
    public List<Land> laender() {
        return laender.alle();
    }

    @Override
    public List<Ort> orte() {
        return orte.alle();
    }

    @Override
    public List<Ware> waren() {
        return waren.alle();
    }

    @Override
    public List<ContainerInfo> container() {
        return container.alle();
    }

    @Override
    public com.dan.logistikapp.model.Tarif tarif() {
        return tarif.lade();
    }
}
