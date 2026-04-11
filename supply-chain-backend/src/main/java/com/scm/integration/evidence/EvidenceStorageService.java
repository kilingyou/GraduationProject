package com.scm.integration.evidence;

import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.integration.ipfs.IpfsStorageService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Store bytes in IPFS (or stub), compute SHA-256, anchor fingerprint on-chain (or stub).
 */
@Component
@RequiredArgsConstructor
public class EvidenceStorageService {

    private final IpfsStorageService ipfsStorageService;
    private final BlockchainAnchorService blockchainAnchorService;

    public StoredEvidence store(byte[] data, String fileName, String anchorBizType) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Evidence payload is empty");
        }
        //用sha256Hex计算二进制文件哈希
        String fileHash = HashUtil.sha256Hex(data);
        //存入ipfs并返回CID
        String cid = ipfsStorageService.add(data, fileName != null ? fileName : "blob.bin");
        //调用anchor方法执行交易，参数为文件类型（资质证书/营业执照），返回交易哈希
        String txHash = blockchainAnchorService.anchor(anchorBizType, fileHash);
        return new StoredEvidence(fileHash, cid, txHash);
    }

    /**
     * IPFS 存证 + 哈希计算；不上链。用于待监管审核的数据（审核通过后再单独 anchor）。
     */
    public StoredEvidence storeWithoutAnchor(byte[] data, String fileName) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Evidence payload is empty");
        }
        String fileHash = HashUtil.sha256Hex(data);
        String cid = ipfsStorageService.add(data, fileName != null ? fileName : "blob.bin");
        return new StoredEvidence(fileHash, cid, null);
    }

    /**
     * Load payload from IPFS (or stub) and confirm it matches the expected SHA-256.
     */
    public boolean verifyContentHash(String ipfsCid, String expectedFileHash) {
        if (expectedFileHash == null || expectedFileHash.trim().isEmpty()
                || ipfsCid == null || ipfsCid.trim().isEmpty()) {
            return false;
        }
        byte[] data = ipfsStorageService.get(ipfsCid);
        if (data == null || data.length == 0) {
            return false;
        }
        return expectedFileHash.equalsIgnoreCase(HashUtil.sha256Hex(data));
    }

    @Getter
    public static class StoredEvidence {
        private final String fileHash;
        private final String ipfsCid;
        private final String txHash;

        public StoredEvidence(String fileHash, String ipfsCid, String txHash) {
            this.fileHash = fileHash;
            this.ipfsCid = ipfsCid;
            this.txHash = txHash;
        }
    }
}
