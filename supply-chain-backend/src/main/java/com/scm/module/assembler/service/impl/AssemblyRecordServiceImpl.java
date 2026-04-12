package com.scm.module.assembler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.common.Constants;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.integration.blockchain.SmartContractInvokeService;
import com.scm.module.assembler.dto.AssemblyRecordCreateRequest;
import com.scm.module.assembler.dto.IntakeVerifyResult;
import com.scm.module.assembler.entity.AssemblyBatch;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.mapper.AssemblyRecordMapper;
import com.scm.module.assembler.service.AssemblerIntakeService;
import com.scm.module.assembler.service.AssemblyBatchService;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.supplier.entity.BomItem;
import com.scm.module.supplier.entity.ProductionRequest;
import com.scm.module.supplier.mapper.BomItemMapper;
import com.scm.module.supplier.service.ProductionRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssemblyRecordServiceImpl
        extends ServiceImpl<AssemblyRecordMapper, AssemblyRecord>
        implements AssemblyRecordService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Random RANDOM = new Random();

    private final BlockchainAnchorService blockchainAnchorService;
    private final SmartContractInvokeService smartContractInvokeService;
    private final AssemblyBatchService assemblyBatchService;
    private final AssemblerIntakeService assemblerIntakeService;
    private final DeviceRecordService deviceRecordService;
    private final ProductionRequestService productionRequestService;
    private final BomItemMapper bomItemMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AssemblyRecord createRecord(AssemblyRecord record) {
        if (record.getSn() == null || record.getSn().isEmpty()) {
            record.setSn(generateUniqueSn());
        }
        if (record.getStatus() == null) {
            record.setStatus("ASSEMBLED");
        }
        if (record.getChainRegistered() == null) {
            record.setChainRegistered(0);
        }
        if (record.getAssemblyTime() == null) {
            record.setAssemblyTime(LocalDateTime.now());
        }
        if (record.getCurrentHolderId() == null && record.getAssemblerId() != null) {
            record.setCurrentHolderId(record.getAssemblerId());
        }
        save(record);
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssemblyRecord createFromRequest(AssemblyRecordCreateRequest request, Long assemblerId) {
        if (request == null || !StringUtils.hasText(request.getBatchNo())) {
            throw new BusinessException("请选择组装批次");
        }
        if (request.getEcidList() == null || request.getEcidList().isEmpty()) {
            throw new BusinessException("请至少绑定一个 ECID");
        }
        if (!StringUtils.hasText(request.getFirmwareVersion())) {
            throw new BusinessException("请填写固件/系统版本");
        }
        AssemblyBatch batch = assemblyBatchService.getOne(new LambdaQueryWrapper<AssemblyBatch>()
                .eq(AssemblyBatch::getBatchNo, request.getBatchNo().trim())
                .eq(AssemblyBatch::getAssemblerId, assemblerId));
        if (batch == null) {
            throw new BusinessException("批次不存在或无权操作");
        }
        int doneBefore = batch.getCompletedQty() == null ? 0 : batch.getCompletedQty();
        if (batch.getPlannedQty() != null && doneBefore >= batch.getPlannedQty()) {
            throw new BusinessException("该批次已达到计划组装数量上限");
        }

        List<String> ecids = request.getEcidList().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toList());
        if (ecids.size() != new HashSet<>(ecids).size()) {
            throw new BusinessException("ECID 列表存在重复");
        }
        for (String ecid : ecids) {
            IntakeVerifyResult v = assemblerIntakeService.verifyEcidForAssembly(ecid, assemblerId);
            if (!IntakeVerifyResult.PASS.equals(v.getStatus())) {
                throw new BusinessException("ECID 校验未通过: " + ecid + " — " + v.getMessage());
            }
        }
        validateSameOrderBomKit(ecids);
        if (StringUtils.hasText(batch.getOrderId())) {
            DeviceRecord probe = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                    .eq(DeviceRecord::getEcid, ecids.get(0)));
            if (probe == null || !batch.getOrderId().equals(probe.getOrderId())) {
                throw new BusinessException("所选部件须属于本组装批次绑定的生产订单");
            }
        }

        String ecidJson;
        try {
            ecidJson = objectMapper.writeValueAsString(ecids);
        } catch (JsonProcessingException e) {
            throw new BusinessException("ECID 列表序列化失败");
        }

        String sn = StringUtils.hasText(request.getSn()) ? request.getSn().trim() : generateSn();
        if (StringUtils.hasText(request.getSn())) {
            long exists = count(new LambdaQueryWrapper<AssemblyRecord>().eq(AssemblyRecord::getSn, sn));
            if (exists > 0) {
                throw new BusinessException("SN 已存在: " + sn);
            }
        }

        AssemblyRecord record = new AssemblyRecord()
                .setSn(sn)
                .setAssemblyBatchNo(batch.getBatchNo())
                .setAssemblerId(assemblerId)
                .setCurrentHolderId(assemblerId)
                .setEcidList(ecidJson)
                .setFirmwareVersion(request.getFirmwareVersion().trim())
                .setStatus("ASSEMBLED")
                .setChainRegistered(0)
                .setAssemblyTime(LocalDateTime.now());
        String anchorPayload = sn + "|" + ecidJson + "|" + assemblerId;
        record.setAssemblyTxHash(blockchainAnchorService.anchor("ASSEMBLY_CREATE", HashUtil.sha256Hex(anchorPayload)));
        smartContractInvokeService.createAssemblyRecord(
                sn,
                ecidJson,
                batch.getBatchNo(),
                record.getFirmwareVersion(),
                record.getTestReportHash()
        );
        for (String ecid : ecids) {
            smartContractInvokeService.bindEcidToSn(ecid, sn);
        }
        save(record);

        deviceRecordService.update(new LambdaUpdateWrapper<DeviceRecord>()
                .in(DeviceRecord::getEcid, ecids)
                .set(DeviceRecord::getStatus, Constants.ASSEMBLED));

        int done = batch.getCompletedQty() == null ? 0 : batch.getCompletedQty();
        batch.setCompletedQty(done + 1);
        if (batch.getPlannedQty() != null && batch.getCompletedQty() >= batch.getPlannedQty()) {
            batch.setStatus("COMPLETED");
        } else if (!"IN_PROGRESS".equals(batch.getStatus())) {
            batch.setStatus("IN_PROGRESS");
        }
        assemblyBatchService.updateById(batch);

        return record;
    }

    @Override
    public IPage<AssemblyRecord> listByBatch(String batchNo, Page<AssemblyRecord> page) {
        return page(page, new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getAssemblyBatchNo, batchNo)
                .orderByDesc(AssemblyRecord::getCreateTime));
    }

    @Override
    public IPage<AssemblyRecord> pageForAssembler(Long assemblerId, String assemblyBatchNo, Page<AssemblyRecord> page) {
        LambdaQueryWrapper<AssemblyRecord> w = new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getAssemblerId, assemblerId)
                .orderByDesc(AssemblyRecord::getCreateTime);
        if (StringUtils.hasText(assemblyBatchNo)) {
            w.eq(AssemblyRecord::getAssemblyBatchNo, assemblyBatchNo.trim());
        }
        return page(page, w);
    }

    @Override
    public AssemblyRecord listBySn(String sn) {
        return getOne(new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getSn, sn));
    }

    @Override
    public long sumEcidSlots(Long assemblerId) {
        Long n = baseMapper.sumEcidCountByAssembler(assemblerId);
        return n == null ? 0L : n;
    }

    @Override
    public boolean registerOnChain(List<Long> ids) {
        List<AssemblyRecord> records = listByIds(ids);
        for (AssemblyRecord record : records) {
            record.setChainRegistered(1);
            record.setStatus("ON_CHAIN");
            if (record.getCurrentHolderId() == null && record.getAssemblerId() != null) {
                record.setCurrentHolderId(record.getAssemblerId());
            }
            String payload = record.getSn() + "|" + (record.getEcidList() != null ? record.getEcidList() : "");
            record.setTxHash(blockchainAnchorService.anchor("ASSEMBLY_RECORD", HashUtil.sha256Hex(payload)));
        }
        return updateBatchById(records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean registerOnChainForAssembler(Long recordId, Long assemblerId) {
        AssemblyRecord record = getById(recordId);
        if (record == null) {
            throw new BusinessException("组装记录不存在");
        }
        if (!assemblerId.equals(record.getAssemblerId())) {
            throw new BusinessException("无权操作该记录");
        }
        if (record.getChainRegistered() != null && record.getChainRegistered() == 1) {
            return true;
        }
        record.setChainRegistered(1);
        record.setStatus("ON_CHAIN");
        if (record.getCurrentHolderId() == null && record.getAssemblerId() != null) {
            record.setCurrentHolderId(record.getAssemblerId());
        }
        String payload = record.getSn() + "|" + (record.getEcidList() != null ? record.getEcidList() : "");
        record.setTxHash(blockchainAnchorService.anchor("ASSEMBLY_RECORD", HashUtil.sha256Hex(payload)));
        return updateById(record);
    }

    private String generateSn() {
        String dateStr = LocalDate.now().format(DATE_FMT);
        String random = String.format("%06d", RANDOM.nextInt(1000000));
        return "SN-" + dateStr + "-" + random;
    }

    private String generateUniqueSn() {
        for (int i = 0; i < 32; i++) {
            String candidate = generateSn();
            long exists = count(new LambdaQueryWrapper<AssemblyRecord>().eq(AssemblyRecord::getSn, candidate));
            if (exists == 0) {
                return candidate;
            }
        }
        throw new BusinessException("自动生成 SN 失败，请指定自定义 SN");
    }

    /**
     * 同一生产订单下的部件方可组装为整机；若订单绑定 BOM，则所选 ECID 须恰好构成一套子件（各 BOM 行数量与明细一致）。
     */
    private void validateSameOrderBomKit(List<String> ecids) {
        List<DeviceRecord> devices = deviceRecordService.list(new LambdaQueryWrapper<DeviceRecord>()
                .in(DeviceRecord::getEcid, ecids));
        if (devices.size() != ecids.size()) {
            throw new BusinessException("部分 ECID 未找到设备记录");
        }
        String orderId = devices.get(0).getOrderId();
        if (!StringUtils.hasText(orderId)) {
            throw new BusinessException("部件缺少所属订单，无法组装");
        }
        for (DeviceRecord d : devices) {
            if (!orderId.equals(d.getOrderId())) {
                throw new BusinessException("参与组装的部件须属于同一生产订单");
            }
        }
        ProductionRequest pr = productionRequestService.getOne(new LambdaQueryWrapper<ProductionRequest>()
                .eq(ProductionRequest::getOrderId, orderId));
        if (pr == null) {
            throw new BusinessException("未找到生产订单: " + orderId);
        }
        if (pr.getBomId() == null) {
            return;
        }
        List<BomItem> bomItems = bomItemMapper.selectList(new LambdaQueryWrapper<BomItem>()
                .eq(BomItem::getBomId, pr.getBomId()));
        if (bomItems == null || bomItems.isEmpty()) {
            return;
        }
        Map<Long, Integer> required = new HashMap<>();
        for (BomItem bi : bomItems) {
            int q = bi.getQuantity() == null || bi.getQuantity() < 1 ? 1 : bi.getQuantity();
            required.put(bi.getId(), q);
        }
        Map<Long, Integer> actual = new HashMap<>();
        for (DeviceRecord d : devices) {
            if (d.getBomItemId() == null) {
                throw new BusinessException("订单含 BOM，部件须关联具体 BOM 行: " + d.getEcid());
            }
            actual.merge(d.getBomItemId(), 1, Integer::sum);
        }
        for (Long bid : actual.keySet()) {
            if (!required.containsKey(bid)) {
                throw new BusinessException("存在不属于该订单 BOM 的部件 (bomItemId=" + bid + ")");
            }
        }
        for (Map.Entry<Long, Integer> e : required.entrySet()) {
            int got = actual.getOrDefault(e.getKey(), 0);
            if (got != e.getValue()) {
                throw new BusinessException("BOM 套件数量不匹配: bomItemId=" + e.getKey()
                        + " 需要 " + e.getValue() + " 件，实际 " + got + " 件");
            }
        }
    }
}
