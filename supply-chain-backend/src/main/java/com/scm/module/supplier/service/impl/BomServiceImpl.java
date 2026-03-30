package com.scm.module.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.supplier.entity.Bom;
import com.scm.module.supplier.entity.BomItem;
import com.scm.module.supplier.mapper.BomItemMapper;
import com.scm.module.supplier.mapper.BomMapper;
import com.scm.module.supplier.service.BomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BomServiceImpl extends ServiceImpl<BomMapper, Bom> implements BomService {

    private final BomItemMapper bomItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Bom createBom(Bom bom, List<BomItem> items) {
        bom.setChainStatus("PENDING");
        save(bom);

        if (items != null && !items.isEmpty()) {
            for (BomItem item : items) {
                item.setBomId(bom.getId());
                bomItemMapper.insert(item);
            }
        }

        bom.setItems(items);
        log.info("BOM created: id={}, name={}, items={}", bom.getId(), bom.getBomName(),
                items != null ? items.size() : 0);
        return bom;
    }

    @Override
    public IPage<Bom> listBySupplier(Long supplierId, Page<Bom> page) {
        LambdaQueryWrapper<Bom> wrapper = new LambdaQueryWrapper<Bom>()
                .eq(Bom::getSupplierId, supplierId)
                .orderByDesc(Bom::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public Bom getBomWithItems(Long bomId) {
        Bom bom = getById(bomId);
        if (bom == null) {
            return null;
        }

        List<BomItem> items = bomItemMapper.selectList(
                new LambdaQueryWrapper<BomItem>().eq(BomItem::getBomId, bomId));
        bom.setItems(items);
        return bom;
    }
}
