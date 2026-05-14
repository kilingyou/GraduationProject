package com.scm.module.enduser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.integration.blockchain.SmartContractInvokeService;
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
    private final SmartContractInvokeService smartContractInvokeService;
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

    /**
     * 创建召回/投诉单：补全单号与状态，落盘证据文件，将摘要上链并调用合约登记，最后持久化。
     */
    @Override
    public RecallRequest createRequest(RecallRequest request, List<MultipartFile> evidenceFiles) throws IOException {
        // 业务单号：RR-日期-6位随机数，调用方未传时自动生成
        if (request.getRequestNo() == null || request.getRequestNo().isEmpty()) {
            String dateStr = LocalDate.now().format(DATE_FMT);
            String random = String.format("%06d", RANDOM.nextInt(1000000));
            request.setRequestNo("RR-" + dateStr + "-" + random);
        }
        if (request.getStatus() == null) {
            request.setStatus("SUBMITTED");
        }
        // 与上传文件一一对应的 IPFS CID 与文件哈希，后续写入 evidenceUrls 并参与链上 payload
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
        // evidenceUrls 存 JSON 数组字符串；无新上传且字段未设时写空数组，避免 null
        if (!evidenceCids.isEmpty()) {
            request.setEvidenceUrls(objectMapper.writeValueAsString(evidenceCids));
        } else if (request.getEvidenceUrls() == null) {
            request.setEvidenceUrls("[]");
        }
        // 将单号、SN、故障信息与证据哈希拼接后做 SHA256，作为锚定内容摘要
        String payload = request.getRequestNo() + "|" + request.getSn()
                + "|" + (request.getFaultType() != null ? request.getFaultType() : "")
                + "|" + (request.getFaultDesc() != null ? request.getFaultDesc() : "")
                + "|" + String.join(",", evidenceHashes);
        request.setTxHash(blockchainAnchorService.anchor(
                "RECALL_REQUEST",
                HashUtil.sha256Hex(payload)
        ));
        // 链上业务合约：登记该 SN 的召回/投诉请求
        smartContractInvokeService.requestRecall(request.getSn(), request.getFaultType(), request.getFaultDesc());
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
