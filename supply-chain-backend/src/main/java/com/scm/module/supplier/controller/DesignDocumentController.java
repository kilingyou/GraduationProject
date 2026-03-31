package com.scm.module.supplier.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.module.supplier.entity.DesignDocument;
import com.scm.module.supplier.service.DesignDocumentService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/supplier/design")
@RequiredArgsConstructor
public class DesignDocumentController {

    private final DesignDocumentService designDocumentService;

    @PostMapping("/upload")
    public Result<DesignDocument> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam("docName") String docName,
                                         @RequestParam(value = "docType", required = false) String docType,
                                         @RequestParam(value = "version", required = false) String version,
                                         @RequestParam(value = "updateNote", required = false) String updateNote) {
        LoginUser loginUser = getCurrentUser();

        DesignDocument doc = new DesignDocument()
                .setSupplierId(loginUser.getUserId())
                .setDocName(docName)
                .setDocType(docType)
                .setVersion(version)
                .setUpdateNote(updateNote)
                .setFileName(file.getOriginalFilename())
                .setFileSize(file.getSize());

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            return Result.fail("读取上传文件失败");
        }
        DesignDocument saved = designDocumentService.upload(doc, bytes);
        return Result.ok(saved);
    }

    @GetMapping("/list")
    public Result<PageResult<DesignDocument>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        LoginUser loginUser = getCurrentUser();

        Page<DesignDocument> page = new Page<>(pageNum, pageSize);
        IPage<DesignDocument> result = designDocumentService.listBySupplier(loginUser.getUserId(), page);

        PageResult<DesignDocument> pageResult = new PageResult<DesignDocument>()
                .setRecords(result.getRecords())
                .setTotal(result.getTotal())
                .setCurrent(result.getCurrent())
                .setSize(result.getSize());
        return Result.ok(pageResult);
    }

    @GetMapping("/{id}")
    public Result<DesignDocument> detail(@PathVariable Long id) {
        LoginUser loginUser = getCurrentUser();
        DesignDocument doc = designDocumentService.getOwned(id, loginUser.getUserId());
        if (doc == null) {
            return Result.fail("文档不存在");
        }
        return Result.ok(doc);
    }

    @PostMapping("/{id}/verify")
    public Result<Boolean> verify(@PathVariable Long id) {
        LoginUser loginUser = getCurrentUser();
        if (designDocumentService.getOwned(id, loginUser.getUserId()) == null) {
            return Result.fail("文档不存在");
        }
        boolean valid = designDocumentService.verifyHash(id);
        return Result.ok(valid);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        designDocumentService.deleteOwnedIfUnused(id, getCurrentUser().getUserId());
        return Result.ok();
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
