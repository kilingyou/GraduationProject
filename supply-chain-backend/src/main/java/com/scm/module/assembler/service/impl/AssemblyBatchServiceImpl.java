package com.scm.module.assembler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.assembler.entity.AssemblyBatch;
import com.scm.module.assembler.mapper.AssemblyBatchMapper;
import com.scm.module.assembler.service.AssemblyBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssemblyBatchServiceImpl
        extends ServiceImpl<AssemblyBatchMapper, AssemblyBatch>
        implements AssemblyBatchService {

    @Override
    public AssemblyBatch createBatch(AssemblyBatch batch) {
        if (batch.getCompletedQty() == null) {
            batch.setCompletedQty(0);
        }
        if (batch.getStatus() == null) {
            batch.setStatus("CREATED");
        }
        save(batch);
        return batch;
    }

    @Override
    public IPage<AssemblyBatch> listByAssembler(Long assemblerId, Page<AssemblyBatch> page) {
        return page(page, new LambdaQueryWrapper<AssemblyBatch>()
                .eq(AssemblyBatch::getAssemblerId, assemblerId)
                .orderByDesc(AssemblyBatch::getCreateTime));
    }
}
