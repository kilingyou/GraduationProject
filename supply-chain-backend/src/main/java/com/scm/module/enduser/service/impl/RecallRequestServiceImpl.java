package com.scm.module.enduser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.integration.evidence.EvidenceStorageService;
import com.scm.module.enduser.entity.RecallRequest;
import com.scm.module.enduser.mapper.RecallRequestMapper;
import com.scm.module.enduser.service.RecallRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class RecallRequestServiceImpl
        extends ServiceImpl<RecallRequestMapper, RecallRequest>
        implements RecallRequestService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Random RANDOM = new Random();

    private final EvidenceStorageService evidenceStorageService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final ObjectMapper objectMapper;

    @Override
    public RecallRequest createRequest(RecallRequest request) {
        try {
            return createRequest(request, Collections.<MultipartFile>emptyList());
        } catch (IOException e) {
            // empty list path shouldn't throw, but keep method safe
            throw new RuntimeException(e);
        }
    }

    @Override
    public RecallRequest createRequest(RecallRequest request, List<MultipartFile> evidenceFiles) throws IOException {
        if (request.getRequestNo() == null || request.getRequestNo().isEmpty()) {
            String dateStr = LocalDate.now().format(DATE_FMT);
            String random = String.format("%06d", RANDOM.nextInt(1000000));
            request.setRequestNo("RR-" + dateStr + "-" + random);
        }
        if (request.getStatus() == null) {
            request.setStatus("SUBMITTED");
        }

        List<String> evidenceCids = new ArrayList<>();
        List<String> evidenceHashes = new ArrayList<>();

        List<MultipartFile> files = evidenceFiles != null ? evidenceFiles : Collections.emptyList();
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            EvidenceStorageService.StoredEvidence ev = evidenceStorageService.store(
                    f.getBytes(),
                    f.getOriginalFilename(),
                    "RECALL_EVIDENCE"
            );
            evidenceCids.add(ev.getIpfsCid());
            evidenceHashes.add(ev.getFileHash());
        }

        if (!evidenceCids.isEmpty()) {
            request.setEvidenceUrls(objectMapper.writeValueAsString(evidenceCids));
        } else if (request.getEvidenceUrls() == null) {
            request.setEvidenceUrls("[]");
        }

        String payload = request.getRequestNo() + "|" + request.getSn()
                + "|" + (request.getFaultType() != null ? request.getFaultType() : "")
                + "|" + (request.getFaultDesc() != null ? request.getFaultDesc() : "")
                + "|" + String.join(",", evidenceHashes);

        request.setTxHash(blockchainAnchorService.anchor(
                "RECALL_REQUEST",
                HashUtil.sha256Hex(payload)
        ));

        save(request);
        return request;
    }

    @Override
    public IPage<RecallRequest> listByUser(Long userId, Page<RecallRequest> page) {
        return page(page, new LambdaQueryWrapper<RecallRequest>()
                .eq(RecallRequest::getUserId, userId)
                .orderByDesc(RecallRequest::getCreateTime));
    }
}
