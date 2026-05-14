package com.scm.module.enduser.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.common.exception.BusinessException;
import com.scm.module.enduser.entity.RecallRequest;
import com.scm.module.enduser.service.RecallRequestService;
import com.scm.module.enduser.service.UserProductService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/enduser/complaint")
@RequiredArgsConstructor
public class ComplaintController {

    private final RecallRequestService recallRequestService;
    private final UserProductService userProductService;

    /**
     * 以 multipart/form-data 提交投诉/召回申请，可同时上传证据文件。
     * <p>文本字段使用 {@code @RequestParam}，文件部分使用 {@code @RequestPart(name = "evidenceFiles")}。</p>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<RecallRequest> submitMultipart(
            @RequestParam String sn,
            @RequestParam(required = false) String faultType,
            @RequestParam String faultDesc,
            @RequestPart(value = "evidenceFiles", required = false) MultipartFile[] evidenceFiles
    ) throws IOException {
        LoginUser loginUser = getCurrentUser();

        // 组装业务实体，用户 ID 以当前登录用户为准，防止客户端伪造
        RecallRequest request = new RecallRequest();
        request.setUserId(loginUser.getUserId());
        request.setSn(sn);
        request.setFaultType(faultType);
        request.setFaultDesc(faultDesc);

        // 仅允许对已绑定序列号的产品发起投诉
        if (!userProductService.isBound(loginUser.getUserId(), sn)) {
            throw new BusinessException("请先完成产品绑定后再提交投诉");
        }

        // 未传文件时避免 NPE，统一交给服务层处理空列表
        List<MultipartFile> files = evidenceFiles == null ?
                Collections.<MultipartFile>emptyList() :
                Arrays.asList(evidenceFiles);

        RecallRequest created = recallRequestService.createRequest(request, files);
        return Result.ok(created);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<RecallRequest> submitJson(@RequestBody RecallRequest request) {
        LoginUser loginUser = getCurrentUser();
        request.setUserId(loginUser.getUserId());
        if (!userProductService.isBound(loginUser.getUserId(), request.getSn())) {
            throw new BusinessException("请先完成产品绑定后再提交投诉");
        }
        RecallRequest created = recallRequestService.createRequest(request);
        return Result.ok(created);
    }

    @GetMapping("/list")
    public Result<PageResult<RecallRequest>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        LoginUser loginUser = getCurrentUser();
        int pn = pageNum != null && pageNum > 0 ? pageNum : (page != null && page > 0 ? page : 1);
        int ps = pageSize != null && pageSize > 0 ? pageSize : (size != null && size > 0 ? size : 10);
        Page<RecallRequest> recallPage = new Page<>(pn, ps);
        IPage<RecallRequest> result = recallRequestService.listByUser(loginUser.getUserId(), recallPage);

        PageResult<RecallRequest> pageResult = new PageResult<RecallRequest>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
