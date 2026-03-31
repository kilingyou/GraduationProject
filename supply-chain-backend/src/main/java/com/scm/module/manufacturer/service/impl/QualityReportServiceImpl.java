package com.scm.module.manufacturer.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.integration.evidence.EvidenceStorageService;
import com.scm.module.manufacturer.entity.QualityReport;
import com.scm.module.manufacturer.mapper.QualityReportMapper;
import com.scm.module.manufacturer.service.QualityReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QualityReportServiceImpl
        extends ServiceImpl<QualityReportMapper, QualityReport>
        implements QualityReportService {

    private final EvidenceStorageService evidenceStorageService;

    @Override
    public QualityReport saveManufacturedReport(QualityReport report, byte[] fileBytes, String originalFilename) {
        EvidenceStorageService.StoredEvidence ev =
                evidenceStorageService.store(fileBytes, originalFilename != null ? originalFilename : "report.bin", "QUALITY_REPORT_MFG");
        report.setFileHash(ev.getFileHash());
        report.setIpfsCid(ev.getIpfsCid());
        report.setTxHash(ev.getTxHash());
        if (report.getReportName() == null || report.getReportName().trim().isEmpty()) {
            report.setReportName(originalFilename != null ? originalFilename : "quality-report");
        }
        report.setReportType("MANUFACTURE");
        save(report);
        return report;
    }
}
