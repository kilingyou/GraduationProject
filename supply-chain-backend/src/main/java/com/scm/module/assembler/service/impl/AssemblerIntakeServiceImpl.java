package com.scm.module.assembler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scm.common.Constants;
import com.scm.common.PageResult;
import com.scm.common.exception.BusinessException;
import com.scm.module.assembler.dto.AvailableAssemblyEcidItem;
import com.scm.module.assembler.dto.IntakeVerifyResult;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.mapper.AssemblerIntakeQueryMapper;
import com.scm.module.assembler.mapper.AssemblyRecordMapper;
import com.scm.module.assembler.service.AssemblerIntakeService;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.supplier.entity.BomItem;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.mapper.BomItemMapper;
import com.scm.module.supplier.service.ProductionRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssemblerIntakeServiceImpl implements AssemblerIntakeService {

    private final DeviceRecordService deviceRecordService;
    private final AssemblyRecordMapper assemblyRecordMapper;
    private final AssemblerIntakeQueryMapper assemblerIntakeQueryMapper;
    private final ProductionRequestService productionRequestService;
    private final BomItemMapper bomItemMapper;

    @Override
    public IntakeVerifyResult verifyEcidForAssembly(String ecid, Long assemblerUserId) {
        // 初始化校验结果对象，并写入待校验的 ECID
        IntakeVerifyResult r = new IntakeVerifyResult();
        r.setEcid(ecid);

        // ECID 为空则直接拒绝
        if (!StringUtils.hasText(ecid)) {
            return r.setStatus(IntakeVerifyResult.REJECT).setMessage("ECID 为空");
        }

        // 去除首尾空格，后续查询与比对均使用规范化后的值
        String trimmed = ecid.trim();

        // 根据 ECID 查询设备记录
        DeviceRecord device = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getEcid, trimmed));
        if (device == null) {
            return r.setStatus(IntakeVerifyResult.NOT_FOUND).setMessage("未找到 ECID，可能为伪劣标识");
        }

        // 检查该部件是否已被某台整机（SN）绑定，已绑定则不允许重复组装
        AssemblyRecord bound = assemblyRecordMapper.findOneByContainingEcid(trimmed);
        if (bound != null) {
            return r.setStatus(IntakeVerifyResult.REJECT)
                    .setMessage("该部件已绑定整机，禁止重复组装")
                    .setBoundToSn(bound.getSn())
                    .setDeviceType(device.getDeviceType())
                    .setManufacturerBatchId(device.getBatchId())
                    .setChainRegistered(device.getChainRegistered());
        }

        // 查询部件所属生产订单，校验组装商是否被订单指定且与当前账号一致
        ProductionRequest order = productionRequestService.getOne(
                new LambdaQueryWrapper<ProductionRequest>()
                        .eq(ProductionRequest::getOrderId, device.getOrderId()));
        if (order != null && order.getAssemblyAssemblerId() != null) {
            // 订单已指定组装商时，仅该组装商账号可领用该部件
            if (assemblerUserId == null || !order.getAssemblyAssemblerId().equals(assemblerUserId)) {
                return r.setStatus(IntakeVerifyResult.REJECT)
                        .setMessage("本生产订单已指定组装商，当前账号无权领用该部件")
                        .setDeviceType(device.getDeviceType());
            }
        }

        // 质检不合格或已作废的部件不得进入组装
        if (Constants.REJECTED.equals(device.getStatus()) || Constants.QC_FAILED.equals(device.getStatus())) {
            return r.setStatus(IntakeVerifyResult.REJECT)
                    .setMessage("该部件已质检不合格并作废，禁止进入组装")
                    .setDeviceType(device.getDeviceType());
        }

        // 必须为制造商质检通过（QC_PASS）状态
        if (!Constants.QC_PASS.equals(device.getStatus())) {
            return r.setStatus(IntakeVerifyResult.REJECT)
                    .setMessage("部件未通过制造商质检，当前状态: " + device.getStatus())
                    .setDeviceType(device.getDeviceType());
        }

        // 必须已完成链上注册
        if (device.getChainRegistered() == null || device.getChainRegistered() != 1) {
            return r.setStatus(IntakeVerifyResult.REJECT)
                    .setMessage("部件未完成链上注册，禁止用于组装")
                    .setDeviceType(device.getDeviceType())
                    .setManufacturerBatchId(device.getBatchId());
        }

        // 全部校验通过，填充成功信息与业务上下文
        r.setStatus(IntakeVerifyResult.PASS)
                .setMessage("验证通过，可用于组装")
                //子物料名字
                .setDeviceType(device.getDeviceType())
                .setManufacturerBatchId(device.getBatchId())
                .setChainRegistered(device.getChainRegistered())
                .setOrderId(device.getOrderId())
                .setBomItemId(device.getBomItemId());

        // 若部件关联 BOM 子件，则补充 BOM 子件摘要信息便于组装端展示
        if (device.getBomItemId() != null) {
            BomItem bi = bomItemMapper.selectById(device.getBomItemId());
            if (bi != null) {
                r.setBomPartSummary(summarizeBomItem(bi));
            }
        }

        return r;
    }

    @Override
    public List<IntakeVerifyResult> verifyEcidsForAssembly(List<String> ecids, Long assemblerUserId) {
        if (ecids == null || ecids.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> distinct = ecids.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        List<IntakeVerifyResult> out = new ArrayList<>(distinct.size());
        for (String e : distinct) {
            out.add(verifyEcidForAssembly(e, assemblerUserId));
        }
        return out;
    }

    @Override
    public PageResult<AvailableAssemblyEcidItem> pageAvailableEcidsForAssembly(
            String keyword, int pageNum, int pageSize, Long assemblerUserId, String orderId) {
        if (assemblerUserId == null) {
            throw new BusinessException("缺少组装商身份");
        }
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String oid = StringUtils.hasText(orderId) ? orderId.trim() : null;
        int pn = Math.max(1, pageNum);
        int ps = Math.min(200, Math.max(1, pageSize));
        long total = assemblerIntakeQueryMapper.countAvailableForAssembly(Constants.QC_PASS, kw, assemblerUserId, oid);
        if (total == 0) {
            return new PageResult<AvailableAssemblyEcidItem>()
                    .setRecords(Collections.emptyList())
                    .setTotal(0)
                    .setCurrent(pn)
                    .setSize(ps);
        }
        long offset = (long) (pn - 1) * ps;
        List<AvailableAssemblyEcidItem> records = assemblerIntakeQueryMapper.listAvailableForAssembly(
                Constants.QC_PASS, kw, assemblerUserId, oid, offset, ps);
        fillBomPartSummaries(records);
        return new PageResult<AvailableAssemblyEcidItem>()
                .setRecords(records)
                .setTotal(total)
                .setCurrent(pn)
                .setSize(ps);
    }

    private void fillBomPartSummaries(List<AvailableAssemblyEcidItem> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> ids = rows.stream()
                .map(AvailableAssemblyEcidItem::getBomItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        List<BomItem> items = bomItemMapper.selectBatchIds(ids);
        Map<Long, BomItem> byId = items.stream().collect(Collectors.toMap(BomItem::getId, x -> x, (a, b) -> a));
        for (AvailableAssemblyEcidItem row : rows) {
            if (row.getBomItemId() == null) {
                continue;
            }
            BomItem it = byId.get(row.getBomItemId());
            if (it != null) {
                row.setBomPartSummary(summarizeBomItem(it));
            }
        }
    }

    private static String summarizeBomItem(BomItem it) {
        String num = it.getPartNumber() != null ? it.getPartNumber().trim() : "";
        String name = it.getPartName() != null ? it.getPartName().trim() : "";
        if (StringUtils.hasText(num) && StringUtils.hasText(name)) {
            return num + " / " + name;
        }
        return StringUtils.hasText(num) ? num : name;
    }
}
