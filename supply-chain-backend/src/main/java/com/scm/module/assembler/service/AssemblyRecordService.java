package com.scm.module.assembler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.assembler.dto.AssemblyRecordCreateRequest;
import com.scm.module.assembler.entity.AssemblyRecord;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AssemblyRecordService extends IService<AssemblyRecord> {

    AssemblyRecord createRecord(AssemblyRecord record);

    AssemblyRecord createFromRequest(AssemblyRecordCreateRequest request, Long assemblerId, MultipartFile qualityReport);

    IPage<AssemblyRecord> listByBatch(String batchNo, Page<AssemblyRecord> page);

    IPage<AssemblyRecord> pageForAssembler(Long assemblerId, String assemblyBatchNo, Page<AssemblyRecord> page);

    AssemblyRecord listBySn(String sn);

    boolean registerOnChain(List<Long> ids);

    /**
     * 单条上链（路径 id），校验归属当前组装商。
     */
    boolean registerOnChainForAssembler(Long recordId, Long assemblerId);

    /** 已绑定到整机的部件 ECID 总次数（JSON 数组长度之和） */
    long sumEcidSlots(Long assemblerId);
}
