package com.scm.module.regulator.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.integration.evidence.EvidenceStorageService;
import com.scm.module.regulator.entity.InspectionTask;
import com.scm.module.regulator.mapper.InspectionTaskMapper;
import com.scm.module.regulator.service.InspectionTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class InspectionTaskServiceImpl
        extends ServiceImpl<InspectionTaskMapper, InspectionTask>
        implements InspectionTaskService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Random RANDOM = new Random();

    private final BlockchainAnchorService blockchainAnchorService;
    private final EvidenceStorageService evidenceStorageService;

    @Override
    public InspectionTask createTask(InspectionTask task) {
        if (task.getTaskNo() == null || task.getTaskNo().isEmpty()) {
            String dateStr = LocalDate.now().format(DATE_FMT);
            String random = String.format("%06d", RANDOM.nextInt(1000000));
            task.setTaskNo("IT-" + dateStr + "-" + random);
        }
        if (task.getStatus() == null) {
            task.setStatus("CREATED");
        }
        save(task);
        return task;
    }

    @Override
    public IPage<InspectionTask> listTasks(Page<InspectionTask> page) {
        return page(page);
    }

    @Override
    public InspectionTask submitResult(Long id, InspectionTask result) {
        InspectionTask existing = getById(id);
        if (existing == null) {
            return null;
        }
        existing.setInspectionResult(result.getInspectionResult());
        existing.setReportHash(result.getReportHash());
        existing.setReportCid(result.getReportCid());
        existing.setInspectorSign(result.getInspectorSign());
        existing.setStatus("COMPLETED");
        String payload = existing.getTaskNo() + "|" + existing.getInspectionResult() + "|" + existing.getReportHash();
        existing.setTxHash(blockchainAnchorService.anchor("INSPECTION_RESULT", HashUtil.sha256Hex(payload)));
        updateById(existing);
        return existing;
    }

    @Override
    public InspectionTask submitResult(Long id, String inspectionResult, MultipartFile reportFile, String inspectorSign)
            throws IOException {
        InspectionTask existing = getById(id);
        if (existing == null) {
            return null;
        }
        existing.setInspectionResult(inspectionResult);
        existing.setInspectorSign(inspectorSign);

        if (reportFile != null && !reportFile.isEmpty()) {
            EvidenceStorageService.StoredEvidence ev = evidenceStorageService.store(
                    reportFile.getBytes(),
                    reportFile.getOriginalFilename(),
                    "INSPECTION_REPORT"
            );
            existing.setReportHash(ev.getFileHash());
            existing.setReportCid(ev.getIpfsCid());
        }

        existing.setStatus("COMPLETED");
        String payload = existing.getTaskNo() + "|" + existing.getInspectionResult() + "|" + existing.getReportHash();
        existing.setTxHash(blockchainAnchorService.anchor("INSPECTION_RESULT", HashUtil.sha256Hex(payload)));
        updateById(existing);
        return existing;
    }
}
