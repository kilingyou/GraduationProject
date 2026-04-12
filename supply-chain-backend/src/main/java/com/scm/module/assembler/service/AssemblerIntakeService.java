package com.scm.module.assembler.service;

import com.scm.common.PageResult;
import com.scm.module.assembler.dto.AvailableAssemblyEcidItem;
import com.scm.module.assembler.dto.IntakeVerifyResult;

import java.util.List;

public interface AssemblerIntakeService {

    /**
     * @param assemblerUserId 当前组装商用户 ID；为 null 时不校验「订单指定组装商」策略（仅内部调用慎用）
     */
    IntakeVerifyResult verifyEcidForAssembly(String ecid, Long assemblerUserId);

    List<IntakeVerifyResult> verifyEcidsForAssembly(List<String> ecids, Long assemblerUserId);

    PageResult<AvailableAssemblyEcidItem> pageAvailableEcidsForAssembly(
            String keyword, int pageNum, int pageSize, Long assemblerUserId, String orderId);
}
