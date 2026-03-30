package com.scm.module.manufacturer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.manufacturer.entity.QualityReport;

public interface QualityReportService extends IService<QualityReport> {

    boolean uploadReport(QualityReport report);
}
