package com.scm.module.assembler.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.alibaba.excel.EasyExcel;
import com.scm.module.assembler.dto.AssemblyRecordCreateRequest;
import com.scm.module.assembler.entity.AssemblyBatch;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyBatchService;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.module.distributor.dto.SnImportRow;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assembler/assembly")
@RequiredArgsConstructor
public class AssemblyController {

    private final AssemblyBatchService assemblyBatchService;
    private final AssemblyRecordService assemblyRecordService;

    @PostMapping("/batch")
    public Result<AssemblyBatch> createBatch(@RequestBody AssemblyBatch batch) {
        LoginUser loginUser = getCurrentUser();
        batch.setAssemblerId(loginUser.getUserId());
        AssemblyBatch created = assemblyBatchService.createBatch(batch);
        return Result.ok(created);
    }

    @GetMapping("/batch/list")
    public Result<PageResult<AssemblyBatch>> listBatches(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        LoginUser loginUser = getCurrentUser();
        int pn = pageNum != null && pageNum > 0 ? pageNum : (page != null && page > 0 ? page : 1);
        int ps = pageSize != null && pageSize > 0 ? pageSize : (size != null && size > 0 ? size : 10);
        Page<AssemblyBatch> batchPage = new Page<>(pn, ps);
        IPage<AssemblyBatch> result = assemblyBatchService.listByAssembler(loginUser.getUserId(), batchPage);

        PageResult<AssemblyBatch> pageResult = new PageResult<AssemblyBatch>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    /**
     * 标准创建：校验来料 ECID、批次归属、SN 唯一等。
     */
    @PostMapping("/record")
    public Result<AssemblyRecord> createRecord(@RequestBody AssemblyRecordCreateRequest request) {
        LoginUser loginUser = getCurrentUser();
        AssemblyRecord created = assemblyRecordService.createFromRequest(request, loginUser.getUserId());
        return Result.ok(created);
    }

    @GetMapping("/record/list")
    public Result<PageResult<AssemblyRecord>> listRecords(
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String assemblyBatchNo,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        LoginUser loginUser = getCurrentUser();
        String bn = StringUtils.hasText(batchNo) ? batchNo : assemblyBatchNo;
        int pn = pageNum != null && pageNum > 0 ? pageNum : (page != null && page > 0 ? page : 1);
        int ps = pageSize != null && pageSize > 0 ? pageSize : (size != null && size > 0 ? size : 10);
        Page<AssemblyRecord> recordPage = new Page<>(pn, ps);
        IPage<AssemblyRecord> result = StringUtils.hasText(bn)
                ? assemblyRecordService.pageForAssembler(loginUser.getUserId(), bn.trim(), recordPage)
                : assemblyRecordService.pageForAssembler(loginUser.getUserId(), null, recordPage);

        PageResult<AssemblyRecord> pageResult = new PageResult<AssemblyRecord>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    /**
     * 导出当前组装商的组装记录 CSV（可选按 assemblyBatchNo 过滤）。
     */
    @GetMapping("/record/export")
    public void exportRecords(
            @RequestParam(required = false) String assemblyBatchNo,
            @RequestParam(required = false) String batchNo,
            HttpServletResponse response) throws IOException {
        LoginUser loginUser = getCurrentUser();
        String bn = StringUtils.hasText(batchNo) ? batchNo : assemblyBatchNo;
        LambdaQueryWrapper<AssemblyRecord> w = new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getAssemblerId, loginUser.getUserId())
                .orderByDesc(AssemblyRecord::getCreateTime);
        if (StringUtils.hasText(bn)) {
            w.eq(AssemblyRecord::getAssemblyBatchNo, bn.trim());
        }
        List<AssemblyRecord> list = assemblyRecordService.list(w);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        String fn = java.net.URLEncoder.encode("assembly-records.csv", StandardCharsets.UTF_8.name()).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fn);
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            pw.write('\uFEFF');
            pw.println("id,sn,assemblyBatchNo,firmwareVersion,ecidListJson,testResult,status,chainRegistered,assemblyTime,txHash");
            for (AssemblyRecord r : list) {
                pw.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        csvLong(r.getId()),
                        csv(r.getSn()),
                        csv(r.getAssemblyBatchNo()),
                        csv(r.getFirmwareVersion()),
                        csv(r.getEcidList()),
                        csv(r.getTestResult()),
                        csv(r.getStatus()),
                        r.getChainRegistered() != null && r.getChainRegistered() == 1 ? "1" : "0",
                        csv(r.getAssemblyTime() != null ? r.getAssemblyTime().toString() : ""),
                        csv(r.getTxHash()));
            }
        }
    }

    /**
     * 导出 SN 列表为 Excel，与分销商「SN 批量发货」模板一致：表头 SN，单列，sheet 名 SN。
     */
    @GetMapping("/record/export-sn-xlsx")
    public void exportSnForShipTemplate(
            @RequestParam(required = false) String assemblyBatchNo,
            @RequestParam(required = false) String batchNo,
            HttpServletResponse response) throws IOException {
        LoginUser loginUser = getCurrentUser();
        String bn = StringUtils.hasText(batchNo) ? batchNo : assemblyBatchNo;
        LambdaQueryWrapper<AssemblyRecord> w = new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getAssemblerId, loginUser.getUserId())
                .orderByDesc(AssemblyRecord::getCreateTime);
        if (StringUtils.hasText(bn)) {
            w.eq(AssemblyRecord::getAssemblyBatchNo, bn.trim());
        }
        List<AssemblyRecord> list = assemblyRecordService.list(w);
        List<SnImportRow> rows = list.stream()
                .map(AssemblyRecord::getSn)
                .filter(StringUtils::hasText)
                .map(s -> {
                    SnImportRow r = new SnImportRow();
                    r.setSn(s.trim());
                    return r;
                })
                .collect(Collectors.toList());

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fn = java.net.URLEncoder.encode("组装记录-SN批量发货格式", StandardCharsets.UTF_8.name()).replace("+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fn + ".xlsx");
        EasyExcel.write(response.getOutputStream(), SnImportRow.class).sheet("SN").doWrite(rows.isEmpty() ? new ArrayList<>() : rows);
    }

    private static String csvLong(Long id) {
        return id == null ? "" : id.toString();
    }

    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\"", "\"\"");
        if (t.contains(",") || t.contains("\"") || t.contains("\n") || t.contains("\r")) {
            return "\"" + t + "\"";
        }
        return t;
    }

    @PostMapping("/record/{id}/register")
    public Result<Boolean> registerOnChain(@PathVariable Long id) {
        LoginUser loginUser = getCurrentUser();
        boolean success = assemblyRecordService.registerOnChainForAssembler(id, loginUser.getUserId());
        return success ? Result.ok(true) : Result.fail("上链失败");
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
