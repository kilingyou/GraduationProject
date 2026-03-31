package com.scm.module.regulator.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.regulator.entity.InspectionTask;
import com.scm.module.regulator.service.InspectionTaskService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/regulator/inspection")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionTaskService inspectionTaskService;

    @PostMapping
    public Result<InspectionTask> create(@RequestBody InspectionTask task) {
        LoginUser loginUser = getCurrentUser();
        task.setInspectorId(loginUser.getUserId());
        InspectionTask created = inspectionTaskService.createTask(task);
        return Result.ok(created);
    }

    @GetMapping("/list")
    public Result<PageResult<InspectionTask>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<InspectionTask> page = new Page<>(pageNum, pageSize);
        IPage<InspectionTask> result = inspectionTaskService.listTasks(page);

        PageResult<InspectionTask> pageResult = new PageResult<InspectionTask>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    @PutMapping("/{id}/result")
    public Result<InspectionTask> submitResult(@PathVariable Long id,
                                               @RequestBody InspectionTask result) {
        InspectionTask updated = inspectionTaskService.submitResult(id, result);
        if (updated == null) {
            return Result.fail("Inspection task not found");
        }
        return Result.ok(updated);
    }

    @PutMapping(value = "/{id}/result", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<InspectionTask> submitResultMultipart(
            @PathVariable Long id,
            @RequestParam String inspectionResult,
            @RequestPart("reportFile") MultipartFile reportFile,
            @RequestParam(required = false) String inspectorSign
    ) throws IOException {
        InspectionTask updated = inspectionTaskService.submitResult(id, inspectionResult, reportFile, inspectorSign);
        if (updated == null) {
            return Result.fail("Inspection task not found");
        }
        return Result.ok(updated);
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
