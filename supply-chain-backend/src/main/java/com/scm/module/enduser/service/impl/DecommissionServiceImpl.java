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

    @Override
    public Decommission createDecommission(Decommission decommission) {
        if (decommission == null || !StringUtils.hasText(decommission.getSn())) {
            throw new BusinessException("SN 不能为空");
        }
        String snNorm = decommission.getSn().trim();
        decommission.setSn(snNorm);
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
