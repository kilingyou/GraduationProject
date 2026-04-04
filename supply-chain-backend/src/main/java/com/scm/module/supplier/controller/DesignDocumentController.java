package com.scm.module.supplier.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scm.common.PageResult;
import com.scm.common.Result;
import com.scm.integration.ipfs.IpfsStorageService;
import com.scm.module.supplier.entity.DesignDocument;
import com.scm.module.supplier.service.DesignDocumentService;
import com.scm.module.supplier.service.SupplierAuditGuardService;
import com.scm.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@RestController
@RequestMapping("/api/supplier/design")
@RequiredArgsConstructor
public class DesignDocumentController {

    private final DesignDocumentService designDocumentService;
    private final SupplierAuditGuardService supplierAuditGuardService;
    private final IpfsStorageService ipfsStorageService;

    @PostMapping("/upload")
    public Result<DesignDocument> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam("docName") String docName,
                                         @RequestParam(value = "docType", required = false) String docType,
                                         @RequestParam(value = "version", required = false) String version,
                                         @RequestParam(value = "updateNote", required = false) String updateNote) {
        LoginUser loginUser = getCurrentUser();
        supplierAuditGuardService.ensureApproved(loginUser.getUserId());

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

    /**
     * 下载/预览：仅文档所属供应商可访问，从 IPFS 取回原始字节。
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long id) {
        LoginUser loginUser = getCurrentUser();
        DesignDocument doc = designDocumentService.getOwned(id, loginUser.getUserId());
        if (doc == null || !StringUtils.hasText(doc.getIpfsCid())) {
            return ResponseEntity.notFound().build();
        }
        byte[] data = ipfsStorageService.get(doc.getIpfsCid());
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }

        String guessed = null;
        try {
            guessed = java.net.URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(data));
        } catch (Exception ignore) {
        }
        MediaType mediaType = StringUtils.hasText(guessed)
                ? MediaType.parseMediaType(guessed)
                : MediaType.APPLICATION_OCTET_STREAM;

        String filename = StringUtils.hasText(doc.getFileName()) ? doc.getFileName() : "design-document";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(ContentDisposition.inline().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(data);
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
        LoginUser loginUser = getCurrentUser();
        supplierAuditGuardService.ensureApproved(loginUser.getUserId());
        designDocumentService.deleteOwnedIfUnused(id, loginUser.getUserId());
        return Result.ok();
    }

    private LoginUser getCurrentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
