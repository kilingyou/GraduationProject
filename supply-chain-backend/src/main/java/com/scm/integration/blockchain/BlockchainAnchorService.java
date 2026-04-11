package com.scm.integration.blockchain;

/**
 * Anchor a business fingerprint on-chain (FISCO BCOS in production).
 */
public interface BlockchainAnchorService {

    class BlockchainAccount {
        //地址
        private final String address;
        //私钥
        private final String privateKeyHex;

        public BlockchainAccount(String address, String privateKeyHex) {
            this.address = address;
            this.privateKeyHex = privateKeyHex;
        }

        public String getAddress() {
            return address;
        }

        public String getPrivateKeyHex() {
            return privateKeyHex;
        }
    }

    /**
     * @param bizType short label e.g. DESIGN_DOC, PRODUCTION_ORDER
     * @param payloadHash SHA-256 hex or similar fingerprint
     * @return transaction hash (stub: deterministic pseudo hash)
     */
    String anchor(String bizType, String payloadHash);

    /**
     * Generate a fresh blockchain account address.
     * Real implementations derive from the chain's crypto suite;
     * stubs return a deterministic pseudo address.
     */

    //随机生成一个账户地址，用于模拟
    default String generateBlockchainAddress() {
        return "0x" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 40);
    }

    /**
     * Generate blockchain account material (address + private key hex).
     * Stub implementation returns pseudo address and empty private key.
     */
    //一个完整的账户，包含账户地址与私钥，默认置为空
    default BlockchainAccount generateBlockchainAccount() {
        return new BlockchainAccount(generateBlockchainAddress(), "");
    }
}
