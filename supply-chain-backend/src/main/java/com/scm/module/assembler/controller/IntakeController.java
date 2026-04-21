package com.scm.module.assembler.controller;

import com.alibaba.excel.EasyExcel;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.assembler.dto.AvailableAssemblyEcidItem;
import com.scm.module.assembler.dto.EcidImportRow;
import com.scm.module.assembler.dto.IntakeVerifyResult;
import com.scm.module.assembler.service.AssemblerIntakeService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/assembler/intake")
@RequiredArgsConstructor
public class IntakeController {

    private final AssemblerIntakeService assemblerIntakeService;

    /**
     * 根据ecid校验合法性
     * @param body ecid
     * @return
     */
    @PostMapping("/scan")
    public Result<IntakeVerifyResult> scan(@RequestBody Map<String, String> body) {
        String ecid = body.get("ecid");
        LoginUser user = currentUser();
        return Result.ok(assemblerIntakeService.verifyEcidForAssembly(ecid, user.getUserId()));
    }

    /**
     * 组装记录 ECID 下拉：与部件入库校验通过条件一致（质检合格、已上链、未绑定整机）。
     */
    @GetMapping("/available-ecids")
    public Result<PageResult<AvailableAssemblyEcidItem>> availableEcids(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orderId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize) {
        LoginUser user = currentUser();
        return Result.ok(assemblerIntakeService.pageAvailableEcidsForAssembly(
                keyword, pageNum, pageSize, user.getUserId(), orderId));
    }

    /**
     * 批量校验（JSON）。入库前循环验证每条 ECID。
     */
    @PostMapping("/verify-batch")
    public Result<List<IntakeVerifyResult>> verifyBatch(@RequestBody Map<String, List<String>> body) {
        List<String> ecids = body != null ? body.get("ecids") : null;
        if (ecids == null || ecids.isEmpty()) {
            return Result.fail("请提供 ecids 列表");
        }
        LoginUser user = currentUser();
        return Result.ok(assemblerIntakeService.verifyEcidsForAssembly(ecids, user.getUserId()));
    }

    /**
     * 上传 Excel：解析 ECID 并逐条校验（一次请求完成导入预览）。
     */
    @PostMapping(value = "/import-verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<IntakeVerifyResult>> importVerify(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail("请上传文件");
        }
        List<EcidImportRow> rows = EasyExcel.read(file.getInputStream()).head(EcidImportRow.class).sheet().doReadSync();
        List<String> ecids = new ArrayList<>();
        for (EcidImportRow row : rows) {
            if (row.getEcid() != null && !row.getEcid().trim().isEmpty()) {
                ecids.add(row.getEcid().trim());
            }
        }
        if (ecids.isEmpty()) {
            return Result.fail("未解析到 ECID，请使用表头为「ECID」的列或检查模板");
        }
        LoginUser user = currentUser();
        return Result.ok(assemblerIntakeService.verifyEcidsForAssembly(ecids, user.getUserId()));
    }

    /**
     * 解析 Excel（首列表头 ECID，或单列）。
     */
    @PostMapping(value = "/import-parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<String>> parseImport(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail("请上传文件");
        }
        List<EcidImportRow> rows = EasyExcel.read(file.getInputStream()).head(EcidImportRow.class).sheet().doReadSync();
        List<String> ecids = new ArrayList<>();
        for (EcidImportRow row : rows) {
            if (row.getEcid() != null && !row.getEcid().trim().isEmpty()) {
                ecids.add(row.getEcid().trim());
            }
        }
        if (ecids.isEmpty()) {
            return Result.fail("未解析到 ECID，请使用表头为「ECID」的列或检查模板");
        }
        return Result.ok(ecids);
    }

    /**
     * 兼容旧前端：JSON 批量校验（同 verify-batch）。
     */
    @PostMapping("/batch-import")
    public Result<List<IntakeVerifyResult>> batchImportLegacy(@RequestBody Map<String, List<String>> body) {
        List<String> ecids = body != null ? body.get("ecids") : null;
        if (ecids == null || ecids.isEmpty()) {
            return Result.fail("ECID list is required");
        }
        LoginUser user = currentUser();
        return Result.ok(assemblerIntakeService.verifyEcidsForAssembly(ecids, user.getUserId()));
    }

    @GetMapping("/import-template")
    public void importTemplate(javax.servlet.http.HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fn = java.net.URLEncoder.encode("ECID导入模板", java.nio.charset.StandardCharsets.UTF_8.name()).replace("+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fn + ".xlsx");
        EcidImportRow demo = new EcidImportRow();
        demo.setEcid("ECID-示例-请删除后粘贴真实数据");
        EasyExcel.write(response.getOutputStream(), EcidImportRow.class).sheet("ECID").doWrite(Collections.singletonList(demo));
    }

    private static LoginUser currentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
