package com.scm.module.manufacturer.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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

    @Override
    public boolean uploadReport(QualityReport report) {
        return save(report);
    }
}
