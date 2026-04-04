package com.scm.module.assembler.service;

import com.scm.common.PageResult;
import com.scm.module.assembler.dto.AvailableAssemblyEcidItem;
import com.scm.module.assembler.dto.IntakeVerifyResult;

import java.util.List;

public interface AssemblerIntakeService {

    IntakeVerifyResult verifyEcid(String ecid);

    List<IntakeVerifyResult> verifyEcids(List<String> ecids);

    /**
     * 分页列出满足入库/组装校验通过条件的 ECID（与 {@link #verifyEcid} 规则一致）。
     */
    PageResult<AvailableAssemblyEcidItem> pageAvailableEcidsForAssembly(String keyword, int pageNum, int pageSize);
}
