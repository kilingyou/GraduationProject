package com.scm.module.assembler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.assembler.entity.AssemblyRecord;

import java.util.List;

public interface AssemblyRecordService extends IService<AssemblyRecord> {

    AssemblyRecord createRecord(AssemblyRecord record);

    IPage<AssemblyRecord> listByBatch(String batchNo, Page<AssemblyRecord> page);

    AssemblyRecord listBySn(String sn);

    boolean registerOnChain(List<Long> ids);
}
