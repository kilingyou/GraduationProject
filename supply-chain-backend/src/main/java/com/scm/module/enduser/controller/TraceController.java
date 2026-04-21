package com.scm.module.enduser.controller;

import com.scm.common.Result;
import com.scm.integration.evidence.EvidenceStorageService;
import com.scm.module.enduser.service.TraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/trace")
@RequiredArgsConstructor
public class TraceController {

    private final TraceService traceService;
    private final EvidenceStorageService evidenceStorageService;

    /**
     * 溯源接口
     * @param sn
     * @return
     */
    @GetMapping("/{sn}")
    public Result<Map<String, Object>> trace(@PathVariable String sn) {
        Map<String, Object> traceInfo = traceService.traceProduct(sn);
        return Result.ok(traceInfo);
    }

    /**
     * 从 IPFS（或 stub）取回文件并比对 SHA-256，满足 PDF「一键校验防篡改」公开验证能力。
     * 路径不可使用 /verify-file，避免与 {@code /{sn}} 将 "verify-file" 误解析为 SN。
     */
    @GetMapping("/file/verify")
    public Result<Boolean> verifyFile(@RequestParam String ipfsCid, @RequestParam String expectedHash) {
        boolean ok = evidenceStorageService.verifyContentHash(ipfsCid, expectedHash);
        return Result.ok(ok);
    }
}
