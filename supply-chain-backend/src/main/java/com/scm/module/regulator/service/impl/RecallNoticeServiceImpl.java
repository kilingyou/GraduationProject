package com.scm.module.regulator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.regulator.entity.RecallNotice;
import com.scm.module.regulator.mapper.RecallNoticeMapper;
import com.scm.module.regulator.service.RecallNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecallNoticeServiceImpl
        extends ServiceImpl<RecallNoticeMapper, RecallNotice>
        implements RecallNoticeService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Random RANDOM = new Random();

    private final BlockchainAnchorService blockchainAnchorService;
    private final DeviceRecordService deviceRecordService;
    private final AssemblyRecordService assemblyRecordService;
    private final ObjectMapper objectMapper;

    @Override
    public RecallNotice createNotice(RecallNotice notice) {
        if (notice.getNoticeNo() == null || notice.getNoticeNo().isEmpty()) {
            String dateStr = LocalDate.now().format(DATE_FMT);
            String random = String.format("%06d", RANDOM.nextInt(1000000));
            notice.setNoticeNo("RN-" + dateStr + "-" + random);
        }
        if (notice.getStatus() == null) {
            notice.setStatus("PUBLISHED");
        }

        // 根据缺陷部件批次/ECID 反向计算“受影响整机 SN 列表”（stub：全量解析整机 ecid_list JSON）
        notice.setAffectedSns(computeAffectedSnsJson(notice));

        // 同步标记受影响整机状态：在 Trace 页面展示 RECALLING（与需求文档一致）
        List<String> affectedSnList = parseAffectedSns(notice.getAffectedSns());
        if (!affectedSnList.isEmpty()) {
            assemblyRecordService.update(new LambdaUpdateWrapper<AssemblyRecord>()
                    .in(AssemblyRecord::getSn, affectedSnList)
                    .set(AssemblyRecord::getStatus, "RECALLING"));
        }

        save(notice);
        String payload = notice.getNoticeNo() + "|"
                + notice.getFaultSourceSn() + "|"
                + notice.getFaultBatchId() + "|"
                + (notice.getAffectedSns() != null ? notice.getAffectedSns() : "");

        notice.setTxHash(blockchainAnchorService.anchor(
                "RECALL_NOTICE",
                HashUtil.sha256Hex(payload)
        ));

        updateById(notice);
        return notice;
    }

    @Override
    public IPage<RecallNotice> listNotices(Page<RecallNotice> page) {
        return page(page);
    }

    private String computeAffectedSnsJson(RecallNotice notice) {
        List<String> targetEcids = resolveTargetEcids(notice);
        if (targetEcids.isEmpty()) {
            return "[]";
        }

        Set<String> target = new HashSet<>(targetEcids);
        List<AssemblyRecord> assemblies = assemblyRecordService.list(
                new LambdaQueryWrapper<AssemblyRecord>()
                        .isNotNull(AssemblyRecord::getEcidList));

        List<String> affectedSns = new ArrayList<>();
        for (AssemblyRecord ar : assemblies) {
            List<String> ecids = parseEcids(ar.getEcidList());
            boolean hit = false;
            for (String e : ecids) {
                if (target.contains(e)) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                affectedSns.add(ar.getSn());
            }
        }

        try {
            return objectMapper.writeValueAsString(affectedSns);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> resolveTargetEcids(RecallNotice notice) {
        if (notice.getFaultEcid() != null && !notice.getFaultEcid().trim().isEmpty()) {
            return Collections.singletonList(notice.getFaultEcid().trim());
        }
        if (notice.getFaultBatchId() == null || notice.getFaultBatchId().trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<DeviceRecord> devices = deviceRecordService.listByBatch(notice.getFaultBatchId());
        List<String> out = new ArrayList<>();
        for (DeviceRecord d : devices) {
            if (d.getEcid() != null) out.add(d.getEcid());
        }
        return out;
    }

    private List<String> parseEcids(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> list = objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
            return list != null ? list : Collections.<String>emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<String> parseAffectedSns(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> list = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            return list != null ? list : Collections.<String>emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
