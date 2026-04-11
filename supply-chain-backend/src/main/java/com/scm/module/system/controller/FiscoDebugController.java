package com.scm.module.system.controller;

import com.scm.common.Result;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.integration.blockchain.FiscoBcosBlockchainAnchorService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FISCO BCOS connectivity and contract invocation diagnostics.
 */
@RestController
@RequestMapping("/api/debug/fisco")
public class FiscoDebugController {

    private final BlockchainAnchorService blockchainAnchorService;
    private final ObjectProvider<FiscoBcosBlockchainAnchorService> fiscoProvider;

    @Value("${scm.blockchain.mode:stub}")
    private String blockchainMode;

    public FiscoDebugController(BlockchainAnchorService blockchainAnchorService,
                                ObjectProvider<FiscoBcosBlockchainAnchorService> fiscoProvider) {
        this.blockchainAnchorService = blockchainAnchorService;
        this.fiscoProvider = fiscoProvider;
    }

    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("time", LocalDateTime.now().toString());
        data.put("blockchainMode", blockchainMode);
        data.put("anchorServiceImpl", blockchainAnchorService.getClass().getSimpleName());

        FiscoBcosBlockchainAnchorService fisco = fiscoProvider.getIfAvailable();
        if (fisco == null) {
            data.put("fiscoBeanPresent", false);
            data.put("ok", false);
            data.put("message", "FISCO bean not loaded. Check scm.blockchain.mode=fisco.");
            return Result.ok(data);
        }

        data.put("fiscoBeanPresent", true);
        data.put("sdkAvailable", fisco.isAvailable());
        data.put("contractAddress", fisco.getContractAddress());

        try {
            String payload = HashUtil.sha256Hex("PING|" + System.currentTimeMillis());
            String txHash = blockchainAnchorService.anchor("DEBUG_PING", payload);
            data.put("anchorTxHash", txHash);

            List<Object> countResp = fisco.callContract("anchorCount", new ArrayList<Object>());
            data.put("anchorCountCallResult", countResp);
            data.put("ok", true);
            data.put("message", "FISCO ping success");
            return Result.ok(data);
        } catch (Exception e) {
            data.put("ok", false);
            data.put("message", "FISCO ping failed: " + e.getMessage());
            return Result.<Map<String, Object>>fail("FISCO ping failed").setData(data);
        }
    }

    /**
     * Public read-only health check:
     * - no transaction sent
     * - only validates SDK availability and contract read call.
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("time", LocalDateTime.now().toString());
        data.put("blockchainMode", blockchainMode);
        data.put("anchorServiceImpl", blockchainAnchorService.getClass().getSimpleName());

        FiscoBcosBlockchainAnchorService fisco = fiscoProvider.getIfAvailable();
        if (fisco == null) {
            data.put("fiscoBeanPresent", false);
            data.put("ok", false);
            data.put("message", "FISCO bean not loaded. Check scm.blockchain.mode=fisco.");
            return Result.ok(data);
        }

        data.put("fiscoBeanPresent", true);
        data.put("sdkAvailable", fisco.isAvailable());
        data.put("contractAddress", fisco.getContractAddress());

        try {
            List<Object> countResp = fisco.callContract("anchorCount", new ArrayList<Object>());
            data.put("anchorCountCallResult", countResp);
            data.put("ok", true);
            data.put("message", "FISCO health check success (read-only)");
            return Result.ok(data);
        } catch (Exception e) {
            data.put("ok", false);
            data.put("message", "FISCO health check failed: " + e.getMessage());
            return Result.<Map<String, Object>>fail("FISCO health check failed").setData(data);
        }
    }

    /**
     * Public read-only chain status:
     * - current block number
     * - group peers
     * - connected peers
     */
    @GetMapping("/block-number")
    public Result<Map<String, Object>> blockNumber() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("time", LocalDateTime.now().toString());
        data.put("blockchainMode", blockchainMode);

        FiscoBcosBlockchainAnchorService fisco = fiscoProvider.getIfAvailable();
        if (fisco == null) {
            data.put("ok", false);
            data.put("message", "FISCO bean not loaded. Check scm.blockchain.mode=fisco.");
            return Result.ok(data);
        }

        data.put("sdkAvailable", fisco.isAvailable());
        data.put("contractAddress", fisco.getContractAddress());

        try {
            data.put("groupId", fisco.getClient().getGroupId());
            data.put("blockNumber", fisco.getClient().getBlockNumber().getBlockNumber().toString());
            data.put("groupPeers", fisco.getClient().getGroupPeers().getGroupPeers());
            data.put("connectedPeers", fisco.getClient().getPeers().getPeers());
            data.put("ok", true);
            data.put("message", "FISCO block number check success");
            return Result.ok(data);
        } catch (Exception e) {
            data.put("ok", false);
            data.put("message", "FISCO block number check failed: " + e.getMessage());
            return Result.<Map<String, Object>>fail("FISCO block number check failed").setData(data);
        }
    }

    /**
     * Public read-only transaction receipt query.
     */
    @GetMapping("/tx/{txHash}")
    public Result<Map<String, Object>> txReceipt(@PathVariable String txHash) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("time", LocalDateTime.now().toString());
        data.put("txHash", txHash);
        data.put("blockchainMode", blockchainMode);

        if (txHash == null || txHash.trim().isEmpty()) {
            data.put("ok", false);
            data.put("message", "txHash is empty");
            return Result.<Map<String, Object>>fail("txHash is empty").setData(data);
        }

        FiscoBcosBlockchainAnchorService fisco = fiscoProvider.getIfAvailable();
        if (fisco == null) {
            data.put("ok", false);
            data.put("message", "FISCO bean not loaded. Check scm.blockchain.mode=fisco.");
            return Result.ok(data);
        }

        try {
            java.util.Optional<org.fisco.bcos.sdk.model.TransactionReceipt> optional =
                    fisco.getClient().getTransactionReceipt(txHash).getTransactionReceipt();
            if (!optional.isPresent()) {
                data.put("ok", true);
                data.put("found", false);
                data.put("message", "Transaction not found yet (or not on this group)");
                return Result.ok(data);
            }

            org.fisco.bcos.sdk.model.TransactionReceipt receipt = optional.get();
            data.put("found", true);
            data.put("status", receipt.getStatus());
            data.put("statusOk", receipt.isStatusOK());
            data.put("statusMsg", receipt.getStatusMsg());
            data.put("blockNumber", receipt.getBlockNumber());
            data.put("blockHash", receipt.getBlockHash());
            data.put("from", receipt.getFrom());
            data.put("to", receipt.getTo());
            data.put("gasUsed", receipt.getGasUsed());
            data.put("contractAddress", receipt.getContractAddress());
            data.put("logsCount", receipt.getLogs() == null ? 0 : receipt.getLogs().size());
            data.put("ok", true);
            data.put("message", "Transaction receipt query success");
            return Result.ok(data);
        } catch (Exception e) {
            data.put("ok", false);
            data.put("message", "Transaction receipt query failed: " + e.getMessage());
            return Result.<Map<String, Object>>fail("Transaction receipt query failed").setData(data);
        }
    }

    /**
     * Public read-only contract sanity check:
     * - SDK sender address
     * - contract owner
     * - whether SDK sender == owner (required for setRole onlyOwner).
     */
    @GetMapping("/contract-check")
    public Result<Map<String, Object>> contractCheck() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("time", LocalDateTime.now().toString());
        data.put("blockchainMode", blockchainMode);

        FiscoBcosBlockchainAnchorService fisco = fiscoProvider.getIfAvailable();
        if (fisco == null) {
            data.put("ok", false);
            data.put("message", "FISCO bean not loaded. Check scm.blockchain.mode=fisco.");
            return Result.ok(data);
        }
        try {
            String sdkAddress = fisco.getClient().getCryptoSuite().getCryptoKeyPair().getAddress();
            data.put("sdkAddress", sdkAddress);
            data.put("contractAddress", fisco.getContractAddress());
            List<Object> ownerResp = fisco.callContract("owner", new ArrayList<Object>());
            String owner = ownerResp != null && !ownerResp.isEmpty() && ownerResp.get(0) != null
                    ? String.valueOf(ownerResp.get(0)) : "";
            data.put("owner", owner);
            data.put("ownerMatch", sdkAddress != null && sdkAddress.equalsIgnoreCase(owner));
            data.put("ok", true);
            data.put("message", "Contract check success");
            return Result.ok(data);
        } catch (Exception e) {
            data.put("ok", false);
            data.put("message", "Contract check failed: " + e.getMessage());
            return Result.<Map<String, Object>>fail("Contract check failed").setData(data);
        }
    }
}
