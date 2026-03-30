package com.scm.module.regulator.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.regulator.entity.InspectionTask;
import com.scm.module.regulator.mapper.InspectionTaskMapper;
import com.scm.module.regulator.service.InspectionTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
        updateById(existing);
        return existing;
    }
}
