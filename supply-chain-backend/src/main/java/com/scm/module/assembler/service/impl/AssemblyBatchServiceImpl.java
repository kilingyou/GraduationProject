package com.scm.module.assembler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.module.assembler.entity.AssemblyBatch;
import com.scm.module.assembler.mapper.AssemblyBatchMapper;
import com.scm.module.assembler.service.AssemblyBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssemblyBatchServiceImpl
        extends ServiceImpl<AssemblyBatchMapper, AssemblyBatch>
        implements AssemblyBatchService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;
    private final BlockchainAnchorService blockchainAnchorService;

    @Override
    public AssemblyBatch createBatch(AssemblyBatch batch) {
        if (!StringUtils.hasText(batch.getBatchNo())) {
            batch.setBatchNo("ASM-" + LocalDate.now().format(DATE_FMT) + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (batch.getCompletedQty() == null) {
            batch.setCompletedQty(0);
        }
        if (batch.getStatus() == null) {
            batch.setStatus("CREATED");
        }
        String payload = batch.getBatchNo() + "|" + batch.getAssemblerId();
        batch.setTxHash(blockchainAnchorService.anchor("ASSEMBLY_BATCH_CREATE", HashUtil.sha256Hex(payload)));
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
