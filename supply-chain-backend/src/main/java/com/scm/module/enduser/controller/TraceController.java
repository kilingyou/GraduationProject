package com.scm.module.enduser.controller;

import com.scm.common.Result;
import com.scm.module.enduser.service.TraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public/trace")
@RequiredArgsConstructor
public class TraceController {

    private final TraceService traceService;

    @GetMapping("/{sn}")
    public Result<Map<String, Object>> trace(@PathVariable String sn) {
        Map<String, Object> traceInfo = traceService.traceProduct(sn);
        return Result.ok(traceInfo);
    }
}
