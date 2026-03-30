package com.scm.module.assembler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.assembler.entity.AssemblyBatch;

public interface AssemblyBatchService extends IService<AssemblyBatch> {

    AssemblyBatch createBatch(AssemblyBatch batch);

    IPage<AssemblyBatch> listByAssembler(Long assemblerId, Page<AssemblyBatch> page);
}
