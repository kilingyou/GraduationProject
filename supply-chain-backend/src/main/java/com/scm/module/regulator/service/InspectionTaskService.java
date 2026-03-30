package com.scm.module.regulator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.regulator.entity.InspectionTask;

public interface InspectionTaskService extends IService<InspectionTask> {

    InspectionTask createTask(InspectionTask task);

    IPage<InspectionTask> listTasks(Page<InspectionTask> page);

    InspectionTask submitResult(Long id, InspectionTask result);
}
