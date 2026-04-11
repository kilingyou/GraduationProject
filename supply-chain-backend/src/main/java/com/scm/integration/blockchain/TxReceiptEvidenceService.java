package com.scm.integration.blockchain;

import lombok.RequiredArgsConstructor;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TxReceiptEvidenceService {

    private final ObjectProvider<FiscoBcosBlockchainAnchorService> fiscoProvider;

    public Map<String, Object> checkTx(String txHash) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("txHash", txHash);
        if (!StringUtils.hasText(txHash)) {
            out.put("ok", false);
            out.put("message", "empty txHash");
            return out;
        }
        FiscoBcosBlockchainAnchorService fisco = fiscoProvider.getIfAvailable();
        if (fisco == null || !fisco.isAvailable()) {
            out.put("ok", false);
            out.put("message", "fisco unavailable");
            return out;
        }
        try {
            Optional<TransactionReceipt> optional =
                    fisco.getClient().getTransactionReceipt(txHash.trim()).getTransactionReceipt();
            if (!optional.isPresent()) {
                out.put("ok", true);
                out.put("found", false);
                out.put("message", "receipt not found");
                return out;
            }
            TransactionReceipt r = optional.get();
            out.put("ok", true);
            out.put("found", true);
            out.put("status", r.getStatus());
            out.put("statusOk", r.isStatusOK());
            out.put("statusMsg", r.getStatusMsg());
            out.put("blockNumber", r.getBlockNumber());
            out.put("from", r.getFrom());
            out.put("to", r.getTo());
            out.put("gasUsed", r.getGasUsed());
            return out;
        } catch (Exception e) {
            out.put("ok", false);
            out.put("message", "query failed: " + e.getMessage());
            return out;
        }
    }
}
