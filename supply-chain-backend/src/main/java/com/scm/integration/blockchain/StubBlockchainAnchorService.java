package com.scm.integration.blockchain;

import com.scm.common.util.HashUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Placeholder until FISCO BCOS SDK wiring; produces a stable-looking pseudo Tx hash.
 */
@Service
@ConditionalOnProperty (
        name = "scm.blockchain.mode",
        havingValue = "stub",
        matchIfMissing = true
)
public class StubBlockchainAnchorService implements BlockchainAnchorService {

    @Override
    public String anchor(String bizType, String payloadHash) {
        String raw = (bizType != null ? bizType : "") + "|" + (payloadHash != null ? payloadHash : "") + "|STUB";
        return "0x" + HashUtil.sha256Hex(raw).substring(0, 64);
    }
}
