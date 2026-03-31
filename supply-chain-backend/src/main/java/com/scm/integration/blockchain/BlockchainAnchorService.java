package com.scm.integration.blockchain;

/**
 * Anchor a business fingerprint on-chain (FISCO BCOS in production).
 */
public interface BlockchainAnchorService {

    /**
     * @param bizType short label e.g. DESIGN_DOC, PRODUCTION_ORDER
     * @param payloadHash SHA-256 hex or similar fingerprint
     * @return transaction hash (stub: deterministic pseudo hash)
     */
    String anchor(String bizType, String payloadHash);
}
