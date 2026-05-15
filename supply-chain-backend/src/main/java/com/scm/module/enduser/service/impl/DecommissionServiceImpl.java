package com.scm.module.enduser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.exception.BusinessException;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.integration.blockchain.SmartContractInvokeService;
import com.scm.module.enduser.entity.Decommission;
import com.scm.module.enduser.mapper.DecommissionMapper;
import com.scm.module.enduser.service.DecommissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DecommissionServiceImpl
        extends ServiceImpl<DecommissionMapper, Decommission>
        implements DecommissionService {

    private final BlockchainAnchorService blockchainAnchorService;
    private final SmartContractInvokeService smartContractInvokeService;
    private final AssemblyRecordService assemblyRecordService;

    /**
     * 创建报废登记：校验 SN 与整机档案、上链锚定（可选）、落库并同步整机状态为已报废。
     * 若调用方已写入 {@code txHash}，则跳过链上锚定与合约调用，便于补录或幂等场景。
     */
    @Override
    public Decommission createDecommission(Decommission decommission) {
        // 入参：实体与 SN 必填
        if (decommission == null || !StringUtils.hasText(decommission.getSn())) {
            throw new BusinessException("SN 不能为空");
        }
        String snNorm = decommission.getSn().trim();
        decommission.setSn(snNorm);

        // 整机档案必须存在且未报废，避免对无效或重复 SN 登记
        AssemblyRecord arCheck = assemblyRecordService.listBySn(snNorm);
        if (arCheck == null) {
            throw new BusinessException("未找到该 SN 的整机档案，无法报废登记");
        }
        if ("DECOMMISSIONED".equals(arCheck.getStatus())) {
            throw new BusinessException("该序列号已报废归档，请勿重复提交");
        }

        if (decommission.getStatus() == null) {
            decommission.setStatus("APPLIED");
        }

        // 未带交易哈希时：对业务字段摘要上链，并调用智能合约登记报废信息
        if (decommission.getTxHash() == null || decommission.getTxHash().trim().isEmpty()) {
            String payload = decommission.getSn() + "|"
                    + decommission.getApplicantId() + "|"
                    + decommission.getRecyclerId() + "|"
                    + (decommission.getDisposalMethod() != null ? decommission.getDisposalMethod() : "") + "|"
                    + (decommission.getDisposalTime() != null ? decommission.getDisposalTime().toString() : "") + "|"
                    + (decommission.getRecyclerName() != null ? decommission.getRecyclerName() : "");

            decommission.setTxHash(blockchainAnchorService.anchor(
                    "DECOMMISSION",
                    HashUtil.sha256Hex(payload)
            ));
            smartContractInvokeService.decommissionWithAgency(
                    decommission.getSn(),
                    decommission.getDisposalMethod(),
                    decommission.getRecyclerName()
            );
        }

        save(decommission);

        // 与报废单一致：整机档案标记为已报废
        arCheck.setStatus("DECOMMISSIONED");
        assemblyRecordService.updateById(arCheck);

        return decommission;
    }

    @Override
    public IPage<Decommission> listByApplicant(Long applicantId, Page<Decommission> page) {
        return page(page, new LambdaQueryWrapper<Decommission>()
                .eq(Decommission::getApplicantId, applicantId)
                .orderByDesc(Decommission::getCreateTime));
    }
}
