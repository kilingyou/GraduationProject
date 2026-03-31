package com.scm.module.regulator.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.config.SchedulerProperties;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.module.enduser.entity.RecallRequest;
import com.scm.module.enduser.mapper.RecallRequestMapper;
import com.scm.module.manufacturer.entity.DeviceRecord;
import com.scm.module.manufacturer.service.DeviceRecordService;
import com.scm.module.regulator.entity.InspectionTask;
import com.scm.module.regulator.entity.RecallNotice;
import com.scm.module.regulator.mapper.InspectionTaskMapper;
import com.scm.module.regulator.mapper.RecallNoticeMapper;
import com.scm.module.regulator.service.RecallNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PDF: 自动化通告与预警（最小可用版，基于本地 MySQL 数据做拓扑计算）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoRecallScheduler {

    private final SchedulerProperties props;

    private final RecallRequestMapper recallRequestMapper;
    private final InspectionTaskMapper inspectionTaskMapper;
    private final RecallNoticeMapper recallNoticeMapper;

    private final AssemblyRecordService assemblyRecordService;
    private final DeviceRecordService deviceRecordService;
    private final RecallNoticeService recallNoticeService;
    private final ObjectMapper objectMapper;

    private volatile LocalDateTime lastRunAt;
    private volatile LocalDateTime lastSuccessAt;
    private volatile String lastError;
    private final AtomicLong totalRuns = new AtomicLong(0);
    private final AtomicLong noticesFromComplaints = new AtomicLong(0);
    private final AtomicLong noticesFromInspections = new AtomicLong(0);

    @Scheduled(fixedDelayString = "#{@schedulerProperties.recallScanIntervalMs}")
    public void scanAndGenerateRecalls() {
        lastRunAt = LocalDateTime.now();
        totalRuns.incrementAndGet();
        if (!props.isEnabled()) {
            return;
        }
        try {
            autoRecallFromComplaints();
        } catch (Exception e) {
            log.error("autoRecallFromComplaints failed", e);
        }
        try {
            autoRecallFromInspections();
        } catch (Exception e) {
            lastError = "autoRecallFromInspections: " + e.getMessage();
            log.error("autoRecallFromInspections failed", e);
        }
        lastSuccessAt = LocalDateTime.now();
    }

    private void autoRecallFromComplaints() {
        LocalDateTime since = LocalDateTime.now().minusMinutes(props.getRecallThresholdWindowMinutes());
        List<RecallRequest> recent = recallRequestMapper.selectList(new LambdaQueryWrapper<RecallRequest>()
                .ge(RecallRequest::getCreateTime, since)
                .eq(RecallRequest::getStatus, "SUBMITTED")
                .orderByDesc(RecallRequest::getCreateTime));
        if (recent.isEmpty()) {
            return;
        }

        // 粗粒度：按故障批次聚合计数，达到阈值则生成召回通告（避免重复）
        java.util.Map<String, Integer> batchCount = new java.util.HashMap<>();
        java.util.Map<String, String> batchToSourceSn = new java.util.HashMap<>();
        java.util.Map<String, List<Long>> batchToRequestIds = new java.util.HashMap<>();

        for (RecallRequest rr : recent) {
            String batchId = resolveFaultBatchFromSn(rr.getSn());
            if (batchId == null || batchId.trim().isEmpty()) {
                continue;
            }
            batchCount.put(batchId, batchCount.getOrDefault(batchId, 0) + 1);
            batchToSourceSn.putIfAbsent(batchId, rr.getSn());
            batchToRequestIds.computeIfAbsent(batchId, k -> new ArrayList<>()).add(rr.getId());
        }

        for (java.util.Map.Entry<String, Integer> e : batchCount.entrySet()) {
            String batchId = e.getKey();
            int count = e.getValue();
            if (count < props.getRecallThresholdCount()) {
                continue;
            }
            if (existsNoticeForBatch(batchId)) {
                continue;
            }
            RecallNotice notice = new RecallNotice();
            notice.setFaultSourceSn(batchToSourceSn.get(batchId));
            notice.setFaultBatchId(batchId);
            notice.setDisposalPlan("自动预警：请监管人员确认后发布召回（退回维修/销毁）");
            notice.setStatus(props.getRecallNoticeDefaultStatus());
            recallNoticeService.createNotice(notice);
            markRequestsProcessing(batchToRequestIds.get(batchId));
            noticesFromComplaints.incrementAndGet();
            log.info("Auto recall notice created from complaints: batchId={}, count={}, noticeNo={}",
                    batchId, count, notice.getNoticeNo());
        }
    }

    private void autoRecallFromInspections() {
        // 官方抽检不合格 -> 自动召回（避免重复）
        List<InspectionTask> bad = inspectionTaskMapper.selectList(new LambdaQueryWrapper<InspectionTask>()
                .eq(InspectionTask::getStatus, "COMPLETED")
                .eq(InspectionTask::getInspectionResult, "FAIL")
                .orderByDesc(InspectionTask::getUpdateTime)
                .last("LIMIT 50"));
        for (InspectionTask t : bad) {
            String batchId = null;
            String sourceSn = null;
            if ("SN".equalsIgnoreCase(t.getTargetType())) {
                sourceSn = t.getTargetId();
                batchId = resolveFaultBatchFromSn(sourceSn);
            } else if ("ECID".equalsIgnoreCase(t.getTargetType())) {
                DeviceRecord d = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                        .eq(DeviceRecord::getEcid, t.getTargetId()));
                if (d != null) {
                    batchId = d.getBatchId();
                }
            } else if ("BATCH".equalsIgnoreCase(t.getTargetType())) {
                batchId = t.getTargetId();
            }

            if (batchId == null || batchId.trim().isEmpty()) {
                continue;
            }
            if (existsNoticeForBatch(batchId)) {
                continue;
            }
            RecallNotice notice = new RecallNotice();
            notice.setFaultSourceSn(sourceSn);
            notice.setFaultBatchId(batchId);
            notice.setDisposalPlan("官方抽检不合格自动预警：请确认后发布召回（退回维修/销毁）");
            // 策略分层：官方抽检 FAIL 直接发布；投诉阈值保持默认配置（通常 DRAFT）
            notice.setStatus("PUBLISHED");
            recallNoticeService.createNotice(notice);
            // 官方抽检产生的召回：不直接绑定用户投诉单
            noticesFromInspections.incrementAndGet();
            log.info("Auto recall notice created from inspection: taskNo={}, batchId={}, noticeNo={}",
                    t.getTaskNo(), batchId, notice.getNoticeNo());
        }
    }

    private void markRequestsProcessing(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            recallRequestMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RecallRequest>()
                    .in(RecallRequest::getId, ids)
                    .set(RecallRequest::getStatus, "PROCESSING"));
        } catch (Exception e) {
            log.warn("markRequestsProcessing failed, ids={}", ids, e);
        }
    }

    private boolean existsNoticeForBatch(String batchId) {
        Long c = recallNoticeMapper.selectCount(new LambdaQueryWrapper<RecallNotice>()
                .eq(RecallNotice::getFaultBatchId, batchId));
        return c != null && c > 0;
    }

    private String resolveFaultBatchFromSn(String sn) {
        if (sn == null || sn.trim().isEmpty()) {
            return null;
        }
        AssemblyRecord ar = assemblyRecordService.listBySn(sn);
        if (ar == null) {
            return null;
        }
        List<String> ecids = parseEcids(ar.getEcidList());
        if (ecids.isEmpty()) {
            return null;
        }
        DeviceRecord d = deviceRecordService.getOne(new LambdaQueryWrapper<DeviceRecord>()
                .eq(DeviceRecord::getEcid, ecids.get(0)));
        return d != null ? d.getBatchId() : null;
    }

    private List<String> parseEcids(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> list = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public Map<String, Object> status() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("enabled", props.isEnabled());
        m.put("intervalMs", props.getRecallScanIntervalMs());
        m.put("thresholdCount", props.getRecallThresholdCount());
        m.put("thresholdWindowMinutes", props.getRecallThresholdWindowMinutes());
        m.put("defaultNoticeStatusForComplaints", props.getRecallNoticeDefaultStatus());
        m.put("lastRunAt", lastRunAt);
        m.put("lastSuccessAt", lastSuccessAt);
        m.put("lastError", lastError);
        m.put("totalRuns", totalRuns.get());
        m.put("createdFromComplaints", noticesFromComplaints.get());
        m.put("createdFromInspections", noticesFromInspections.get());
        return m;
    }

    public void runNow() {
        scanAndGenerateRecalls();
    }
}

