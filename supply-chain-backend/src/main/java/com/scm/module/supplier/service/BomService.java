package com.scm.module.supplier.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.supplier.dto.BomVO;
import com.scm.module.supplier.entity.Bom;
import com.scm.module.supplier.entity.BomItem;

import java.util.List;

public interface BomService extends IService<Bom> {

    Bom createBom(Bom bom, List<BomItem> items);

    IPage<Bom> listBySupplier(Long supplierId, Page<Bom> page);

    Bom getBomWithItems(Long bomId);

    IPage<BomVO> pageVoBySupplier(Long supplierId, Page<Bom> page);

    BomVO getBomVoWithItems(Long bomId, Long supplierId);

    void removeBomForSupplier(Long bomId, Long supplierId);
}
