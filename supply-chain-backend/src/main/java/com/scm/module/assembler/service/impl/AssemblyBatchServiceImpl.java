package com.scm.module.assembler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.Constants;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.module.assembler.entity.AssemblyBatch;
import com.scm.module.assembler.mapper.AssemblyBatchMapper;
import com.scm.module.assembler.service.AssemblyBatchService;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.service.ProductionRequestService;
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
    private final ProductionRequestService productionRequestService;

    @Override
    public AssemblyBatch createBatch(AssemblyBatch batch) {
        // 校验是否选择了关联的生产订单
        if (!StringUtils.hasText(batch.getOrderId())) {
            throw new BusinessException("请选择关联的生产订单");
        }

        // 规范化订单号（去除首尾空格）
        String orderId = batch.getOrderId().trim();

        // 根据订单号查询生产订单
        ProductionRequest pr = productionRequestService.getOne(new LambdaQueryWrapper<ProductionRequest>()
                .eq(ProductionRequest::getOrderId, orderId));
        if (pr == null) {
            throw new BusinessException("生产订单不存在: " + orderId);
        }

        // 已撤销的订单不允许再创建组装批次
        if (Constants.CANCELLED.equals(pr.getStatus())) {
            throw new BusinessException("订单已撤销，无法创建组装批次");
        }

        // 若订单已指定组装商，则当前批次中的组装商必须与订单一致，否则无权建批次
        if (pr.getAssemblyAssemblerId() != null && batch.getAssemblerId() != null
                && !pr.getAssemblyAssemblerId().equals(batch.getAssemblerId())) {
            throw new BusinessException("本订单已指定其他组装商，当前账号无权为该订单建批次");
        }

        // 回写规范化后的订单号
        batch.setOrderId(orderId);

        // 若未填写批次号，则自动生成：ASM-日期-随机短码
        if (!StringUtils.hasText(batch.getBatchNo())) {
            batch.setBatchNo("ASM-" + LocalDate.now().format(DATE_FMT) + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        // 默认已完成数量为 0
        if (batch.getCompletedQty() == null) {
            batch.setCompletedQty(0);
        }

        // 默认批次状态为已创建
        if (batch.getStatus() == null) {
            batch.setStatus("CREATED");
        }

        // 组装批次创建信息上链锚定，并保存交易哈希
        String payload = batch.getBatchNo() + "|" + batch.getAssemblerId();
        batch.setTxHash(blockchainAnchorService.anchor("ASSEMBLY_BATCH_CREATE", HashUtil.sha256Hex(payload)));

        // 持久化组装批次
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
