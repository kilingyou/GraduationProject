package com.scm.module.manufacturer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.Constants;
import com.scm.common.exception.BusinessException;
import com.scm.integration.blockchain.SmartContractInvokeService;
import com.scm.module.manufacturer.dto.DeviceRegisterRequest;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.entity.ProductionBatch;
import com.scm.module.manufacturer.mapper.DeviceRecordMapper;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.manufacturer.service.ProductionBatchService;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceRecordServiceImpl
        extends ServiceImpl<DeviceRecordMapper, DeviceRecord>
        implements DeviceRecordService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductionBatchService productionBatchService;
    private final ProductionRequestService productionRequestService;
    private final BomItemMapper bomItemMapper;
    private final SmartContractInvokeService smartContractInvokeService;

    // 批量生成 ECID
    @Override
    public List<String> generateEcids(String batchId, String orderId, Long manufacturerId, Integer qty, String deviceType) {
        // 根据批次号查询生产批次
        ProductionBatch batch = productionBatchService.getOne(
                new LambdaQueryWrapper<ProductionBatch>().eq(ProductionBatch::getBatchId, batchId));

        // 校验批次是否存在，以及当前制造商是否有权限操作该批次
        if (batch == null || !manufacturerId.equals(batch.getManufacturerId())) {
            throw new BusinessException("批次不存在或无权限");
        }

        // 校验传入的订单号是否与批次所属订单一致
        if (!batch.getOrderId().equals(orderId)) {
            throw new BusinessException("订单号与批次不匹配");
        }

        // 根据订单号查询生产订单
        ProductionRequest order = productionRequestService.getOne(
                new LambdaQueryWrapper<ProductionRequest>().eq(ProductionRequest::getOrderId, orderId));

        // 默认取批次中绑定的 BOM 子件行 ID，用于后续设备记录保存
        Long bomItemIdForRecord = batch.getBomItemId();

        // 默认设备类型使用传入值，后续可能根据 BOM 自动解析覆盖
        String resolvedDeviceType = deviceType;

        // 如果订单存在且已关联 BOM，则按 BOM 子件逻辑校验和生成
        if (order != null && order.getBomId() != null) {
            // 已关联 BOM 的订单，批次必须绑定具体的 BOM 子件行
            if (bomItemIdForRecord == null) {
                throw new BusinessException("该批次未绑定 BOM 子件行，请新建「子件批次」后再生成 ECID");
            }

            // 查询 BOM 子件明细
            BomItem item = bomItemMapper.selectById(bomItemIdForRecord);

            // 校验 BOM 子件是否存在，且属于当前订单关联的 BOM
            if (item == null || !order.getBomId().equals(item.getBomId())) {
                throw new BusinessException("BOM 明细行无效");
            }

            // 获取订单数量，为空时按 0 处理
            int orderQty = order.getQuantity() == null ? 0 : order.getQuantity();

            // 获取该子件单套用量，小于 1 或为空时默认按 1 处理
            int lineUse = item.getQuantity() == null || item.getQuantity() < 1 ? 1 : item.getQuantity();

            // 该子件允许生成的最大设备数量 = 订单数量 × 子件用量
            int lineCap = orderQty * lineUse;

            if (lineCap > 0) {
                // 统计当前订单、当前制造商、当前 BOM 子件下仍占用需求的设备记录数量；不合格件允许补产
                long lineExisting = count(new LambdaQueryWrapper<DeviceRecord>()
                        .eq(DeviceRecord::getOrderId, orderId)
                        .eq(DeviceRecord::getManufacturerId, manufacturerId)
                        .eq(DeviceRecord::getBomItemId, bomItemIdForRecord)
                        .ne(DeviceRecord::getStatus, Constants.REJECTED));

                // 校验本次生成后是否超出该子件的需求上限
                if (lineExisting + qty > lineCap) {
                    throw new BusinessException(
                            "该子件 ECID 数量将超过订单需求（上限 " + lineCap + "，已有 " + lineExisting + "）");
                }
            }

            // 根据 BOM 子件信息自动解析设备类型
            resolvedDeviceType = deviceTypeFromBomItem(item);
        } else {
            // 如果订单未关联 BOM，则设备记录中不保存 BOM 子件行 ID
            bomItemIdForRecord = null;

            // 此时必须由外部传入设备类型
            if (!StringUtils.hasText(deviceType)) {
                throw new BusinessException("请输入设备类型");
            }

            // 去除设备类型前后空格
            resolvedDeviceType = deviceType.trim();
        }

        // 如果批次设置了计划数量，则生成的设备总数不能超过该计划数量
        if (batch.getPlannedQty() != null && batch.getPlannedQty() > 0) {
            // 统计该批次下仍占用计划数量的设备数量；已不合格的 ECID 不再阻塞补产
            long existing = count(new LambdaQueryWrapper<DeviceRecord>()
                    .eq(DeviceRecord::getBatchId, batchId)
                    .ne(DeviceRecord::getStatus, Constants.REJECTED));

            // 校验本次生成后是否超过批次计划数量
            if (existing + qty > batch.getPlannedQty()) {
                throw new BusinessException(
                        "本批次设备数量将超过计划数量（计划 " + batch.getPlannedQty() + "，已有 " + existing + "）");
            }
        }

        // 生成当天日期字符串，用于拼接 ECID
        String dateStr = LocalDate.now().format(DATE_FMT);

        // 生成制造商编码，格式示例：M0001
        String mfCode = "M" + String.format("%04d", manufacturerId % 10000);

        // ECID 序号按“制造商 + 日期”维度全局递增，避免同一制造商当天不同批次出现重复编号
        String ecidPrefix = "ECID-" + mfCode + "-" + dateStr + "-";

        // 查询当前制造商当天已有 ECID 的最大序号
        int maxSeq = list(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getManufacturerId, manufacturerId)
                .likeRight(DeviceRecord::getEcid, ecidPrefix))
                .stream()
                .mapToInt(r -> parseEcidSeq(r.getEcid(), ecidPrefix))
                .max()
                .orElse(0);

        // 本次生成的起始序号 = 当前最大序号 + 1
        int startSeq = maxSeq + 1;

        // 用于保存设备记录对象和最终返回的 ECID 编号列表
        List<DeviceRecord> records = new ArrayList<>(qty);
        List<String> ecids = new ArrayList<>(qty);

        // 统一记录生产时间
        LocalDateTime now = LocalDateTime.now();

        // 按数量循环生成 ECID 和设备记录
        for (int i = 0; i < qty; i++) {
            // 生成 6 位递增序号
            String seq = String.format("%06d", startSeq + i);

            // 拼接完整 ECID
            String ecid = "ECID-" + mfCode + "-" + dateStr + "-" + seq;
            ecids.add(ecid);

            // 构造设备记录对象
            DeviceRecord record = new DeviceRecord()
                    .setEcid(ecid)
                    .setOrderId(orderId)
                    .setBatchId(batchId)
                    .setManufacturerId(manufacturerId)
                    .setBomItemId(bomItemIdForRecord)
                    .setDeviceType(resolvedDeviceType)
                    .setManufactureTime(now)
                    .setStatus("PRODUCED")
                    .setChainRegistered(0);
            records.add(record);
        }

        // 批量持久化设备记录
        saveBatch(records);

        // 根据设备记录数量刷新批次已完成数量
        productionBatchService.refreshCompletedQtyFromDevices(batchId);

        // 返回本次生成的 ECID 列表
        return ecids;
    }

    private static int parseEcidSeq(String ecid, String prefix) {
        if (ecid == null || !ecid.startsWith(prefix)) {
            return 0;
        }
        try {
            return Integer.parseInt(ecid.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    //批量生产ECID
    @Override
    public List<String> generateEcidsForBatch(String batchId, Long manufacturerId, Integer qty, String deviceType) {
        ProductionBatch batch = productionBatchService.getOne(
                new LambdaQueryWrapper<ProductionBatch>().eq(ProductionBatch::getBatchId, batchId));
        if (batch == null || !manufacturerId.equals(batch.getManufacturerId())) {
            throw new BusinessException("批次不存在或无权限");
        }
        return generateEcids(batchId, batch.getOrderId(), manufacturerId, qty, deviceType);
    }

    @Override
    public List<DeviceRecord> listByBatch(String batchId) {
        return list(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getBatchId, batchId)
                .orderByAsc(DeviceRecord::getEcid));
    }

    @Override
    public IPage<DeviceRecord> pageForManufacturer(
            Long manufacturerId,
            Page<DeviceRecord> page,
            String batchId,
            String orderId,
            String keyword,
            String status,
            Integer chainRegistered) {
        String orderIdEq = StringUtils.hasText(orderId) ? orderId.trim() : null;
        LambdaQueryWrapper<DeviceRecord> w = new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getManufacturerId, manufacturerId)
                .eq(StringUtils.hasText(batchId), DeviceRecord::getBatchId, batchId)
                .eq(orderIdEq != null, DeviceRecord::getOrderId, orderIdEq);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            w.and(q -> q.like(DeviceRecord::getEcid, kw)
                    .or().like(DeviceRecord::getOrderId, kw)
                    .or().like(DeviceRecord::getBatchId, kw)
                    .or().like(DeviceRecord::getDeviceType, kw));
        }
        if (StringUtils.hasText(status)) {
            w.eq(DeviceRecord::getStatus, status.trim());
        }
        if (chainRegistered != null) {
            if (chainRegistered == 1) {
                w.eq(DeviceRecord::getChainRegistered, 1);
            } else if (chainRegistered == 0) {
                w.and(q -> q.isNull(DeviceRecord::getChainRegistered).or().ne(DeviceRecord::getChainRegistered, 1));
            }
        }
        w.orderByDesc(DeviceRecord::getCreateTime);
        IPage<DeviceRecord> raw = page(page, w);
        fillDeviceBomSummaries(raw.getRecords());
        return raw;
    }

    private void fillDeviceBomSummaries(List<DeviceRecord> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> ids = rows.stream()
                .map(DeviceRecord::getBomItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        List<BomItem> items = bomItemMapper.selectBatchIds(ids);
        Map<Long, BomItem> byId = items.stream().collect(Collectors.toMap(BomItem::getId, x -> x, (a, b) -> a));
        for (DeviceRecord r : rows) {
            if (r.getBomItemId() == null) {
                continue;
            }
            BomItem it = byId.get(r.getBomItemId());
            if (it != null) {
                r.setBomPartSummary(summarizeBomItem(it));
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

    /** 写入链上 devType 与库展示：料号 + 名称（与 BOM 行一致） */
    private static String deviceTypeFromBomItem(BomItem it) {
        String num = it.getPartNumber() != null ? it.getPartNumber().trim() : "";
        String name = it.getPartName() != null ? it.getPartName().trim() : "";
        if (StringUtils.hasText(num) && StringUtils.hasText(name)) {
            return num + " " + name;
        }
        return StringUtils.hasText(num) ? num : (StringUtils.hasText(name) ? name : "BOM_ITEM_" + it.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean registerOnChain(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        // 根据设备记录 ID 集合查询对应的设备记录列表（去重，避免重复上链同一 ECID）
        List<Long> distinctIds = ids.stream().distinct().collect(Collectors.toList());
        List<DeviceRecord> records = listByIds(distinctIds);
        Set<Long> requestedIds = new HashSet<>(ids);
        Set<Long> foundIds = records.stream().map(DeviceRecord::getId).collect(Collectors.toSet());
        if (!foundIds.equals(requestedIds)) {
            throw new BusinessException("部分设备记录不存在，请刷新后重试");
        }

        // 先筛选出状态为 REJECTED（质检不合格）的设备，收集其 ECID
        List<String> rejectedEcids = records.stream()
                .filter(r -> Constants.REJECTED.equals(r.getStatus()))
                .map(DeviceRecord::getEcid)
                .collect(Collectors.toList());

        // 质检不合格的设备不允许执行上链注册
        if (!rejectedEcids.isEmpty()) {
            throw new BusinessException(
                    "质检不合格的设备不能上链注册，请先处理或取消勾选：" + String.join("、", rejectedEcids));
        }

        // 逐条校验设备记录是否满足上链注册条件
        for (DeviceRecord record : records) {
            // 只有质检合格（QC_PASS）的设备才允许上链注册
            if (!Constants.QC_PASS.equals(record.getStatus())) {
                throw new BusinessException("仅质检合格（QC_PASS）的设备可上链注册，请先完成质检: " + record.getEcid());
            }

            // 上链前必须已经绑定质检报告哈希
            if (!StringUtils.hasText(record.getTestReportHash())) {
                throw new BusinessException("设备未绑定质检报告哈希，请先上传检测报告后再注册: " + record.getEcid());
            }

            // 已完成上链注册的设备不允许重复提交
            if (record.getChainRegistered() != null && record.getChainRegistered() == 1) {
                throw new BusinessException("设备已上链注册，请勿重复提交: " + record.getEcid());
            }
        }

        // 逐条执行设备上链注册（仅合约 registerDeviceRecord 一笔交易，txHash 为合约交易哈希）
        for (DeviceRecord record : records) {
            String txHash = smartContractInvokeService.registerDeviceRecord(
                    record.getEcid(),
                    record.getOrderId(),
                    record.getBatchId(),
                    record.getDeviceType(),
                    record.getTestReportHash(),
                    Constants.QC_PASS
            );

            record.setChainRegistered(1);
            record.setTxHash(txHash);

            // 理论上前面已校验为 QC_PASS，这里再次兜底修正状态
            if (!Constants.QC_PASS.equals(record.getStatus())) {
                record.setStatus(Constants.QC_PASS);
            }
        }

        // 批量更新数据库中的设备记录
        boolean ok = updateBatchById(records);

        // 如果更新成功，则尝试检查这些设备所属批次是否可自动完工
        if (ok) {
            Map<String, Long> batchToManufacturer = new LinkedHashMap<>();
            for (DeviceRecord r : records) {
                // 收集批次ID与制造商ID的映射，避免同一批次重复处理
                if (StringUtils.hasText(r.getBatchId())) {
                    batchToManufacturer.putIfAbsent(r.getBatchId(), r.getManufacturerId());
                }
            }

            // 对每个涉及的批次尝试执行自动完工判断
            for (Map.Entry<String, Long> e : batchToManufacturer.entrySet()) {
                productionBatchService.tryAutoCompleteBatch(e.getKey(), e.getValue());
            }
        }

        // 返回数据库更新结果
        return ok;
    }

    //校验id
    @Override
    public boolean registerOnChain(DeviceRegisterRequest request, Long manufacturerId) {
        List<Long> ids = request.getIds();
        // 未传主键 id 时，按 ECID 解析出本厂商下的设备记录 id
        if (ids == null || ids.isEmpty()) {
            if (request.getEcids() == null || request.getEcids().isEmpty()) {
                return false;
            }
            Set<String> wantedEcids = request.getEcids().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.toSet());
            if (wantedEcids.isEmpty()) {
                return false;
            }
            List<DeviceRecord> recs = list(new LambdaQueryWrapper<DeviceRecord>()
                    .in(DeviceRecord::getEcid, wantedEcids)
                    .eq(DeviceRecord::getManufacturerId, manufacturerId));
            Set<String> foundEcids = recs.stream()
                    .map(DeviceRecord::getEcid)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.toSet());
            // 校验：请求的 ECID 必须全部存在且属于当前制造商
            if (!foundEcids.equals(wantedEcids)) {
                throw new BusinessException("部分 ECID 不存在或无权操作");
            }
            ids = recs.stream().map(DeviceRecord::getId).collect(Collectors.toList());
        } else {
            // 已传主键 id：校验记录存在且归属当前制造商
            List<DeviceRecord> recs = listByIds(ids);
            Set<Long> requested = new HashSet<>(ids);
            Set<Long> found = recs.stream().map(DeviceRecord::getId).collect(Collectors.toSet());
            if (!found.equals(requested)) {
                throw new BusinessException("部分设备记录不存在");
            }
            for (DeviceRecord r : recs) {
                if (!manufacturerId.equals(r.getManufacturerId())) {
                    throw new BusinessException("存在无权限的设备记录");
                }
            }
        }
        return registerOnChain(ids);
    }
}
