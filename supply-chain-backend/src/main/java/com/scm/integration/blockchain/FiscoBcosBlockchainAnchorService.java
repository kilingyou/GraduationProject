package com.scm.integration.blockchain;

import com.scm.common.util.HashUtil;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.transaction.manager.AssembleTransactionProcessor;
import org.fisco.bcos.sdk.transaction.manager.TransactionProcessorFactory;
import org.fisco.bcos.sdk.transaction.model.dto.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

/**
 * 真实 FISCO BCOS 区块链锚定服务。
 * 通过 Java SDK 连接 FISCO BCOS 2.x 节点，调用 SupplyChainTraceability 合约的 anchor 方法。
 * 激活条件：scm.blockchain.mode=fisco
 */
@Service
@ConditionalOnProperty(name = "scm.blockchain.mode", havingValue = "fisco")
public class FiscoBcosBlockchainAnchorService implements BlockchainAnchorService {

    private static final Logger log = LoggerFactory.getLogger(FiscoBcosBlockchainAnchorService.class);
    private static final String CONTRACT_NAME = "SupplyChainTraceability";

    @Value("${scm.blockchain.fisco.config-file:conf/config.toml}")
    private String configFile;

    @Value("${scm.blockchain.fisco.group-id:1}")
    private int groupId;

    @Value("${scm.blockchain.fisco.contract-address:}")
    private String contractAddress;

    @Value("${scm.blockchain.fisco.abi-path:conf/abi}")
    private String abiPath;

    @Value("${scm.blockchain.fisco.bin-path:conf/bin}")
    private String binPath;

    private BcosSDK sdk;
    private Client client;
    private AssembleTransactionProcessor txProcessor;
    private boolean available = false;

    @PostConstruct
    public void init() {
        try {
            log.info("Initializing FISCO BCOS SDK — configFile={}, groupId={}", configFile, groupId);

            sdk = BcosSDK.build(configFile);
            client = sdk.getClient(groupId);
            CryptoKeyPair keyPair = client.getCryptoSuite().getCryptoKeyPair();

            log.info("SDK account address: {}", keyPair.getAddress());

            txProcessor = TransactionProcessorFactory.createAssembleTransactionProcessor(
                    client, keyPair, abiPath, binPath);

            available = true;

            if (contractAddress == null || contractAddress.trim().isEmpty()) {
                log.warn("scm.blockchain.fisco.contract-address is EMPTY — "
                        + "please deploy SupplyChainTraceability.sol and configure the address");
            } else {
                log.info("FISCO BCOS SDK initialized — group={}, contract={}", groupId, contractAddress);
            }
        } catch (Exception e) {
            log.error("FISCO BCOS SDK initialization FAILED: {}", e.getMessage(), e);
            log.warn("Blockchain anchoring will be unavailable. "
                    + "Check conf/config.toml, certificates, and node connectivity.");
        }
    }

    @Override
    public String anchor(String bizType, String payloadHash) {
        if (!available) {
            log.warn("FISCO BCOS unavailable — returning fallback hash for bizType={}", bizType);
            String raw = (bizType != null ? bizType : "") + "|"
                    + (payloadHash != null ? payloadHash : "") + "|FISCO_OFFLINE";
            return "0x" + HashUtil.sha256Hex(raw).substring(0, 64);
        }

        if (contractAddress == null || contractAddress.trim().isEmpty()) {
            log.warn("Contract address not configured — returning fallback hash for bizType={}", bizType);
            String raw = (bizType != null ? bizType : "") + "|"
                    + (payloadHash != null ? payloadHash : "") + "|NO_CONTRACT";
            return "0x" + HashUtil.sha256Hex(raw).substring(0, 64);
        }

        try {
            List<Object> params = new ArrayList<>();
            params.add(bizType != null ? bizType : "");
            params.add(payloadHash != null ? payloadHash : "");

            TransactionResponse resp = txProcessor.sendTransactionAndGetResponseByContractLoader(
                    CONTRACT_NAME, contractAddress, "anchor", params);

            String txHash = resp.getTransactionReceipt().getTransactionHash();
            log.info("Anchored on FISCO BCOS — bizType={}, txHash={}", bizType, txHash);
            return txHash;
        } catch (Exception e) {
            log.error("FISCO BCOS anchor FAILED for bizType={}: {}", bizType, e.getMessage(), e);
            throw new RuntimeException("Blockchain anchor failed: " + e.getMessage(), e);
        }
    }

    /**
     * 调用合约的只读方法（call），用于链上数据查询。
     */
    public List<Object> callContract(String functionName, List<Object> params) throws Exception {
        if (!available || contractAddress == null || contractAddress.trim().isEmpty()) {
            throw new IllegalStateException("FISCO BCOS not available or contract address not set");
        }
        return txProcessor.sendCallByContractLoader(
                CONTRACT_NAME, contractAddress, functionName, params
        ).getReturnObject();
    }

    /**
     * 发送合约交易（非 anchor 的业务专用方法），返回 txHash。
     */
    public String sendTransaction(String functionName, List<Object> params) throws Exception {
        if (!available || contractAddress == null || contractAddress.trim().isEmpty()) {
            throw new IllegalStateException("FISCO BCOS not available or contract address not set");
        }
        TransactionResponse resp = txProcessor.sendTransactionAndGetResponseByContractLoader(
                CONTRACT_NAME, contractAddress, functionName, params);
        org.fisco.bcos.sdk.model.TransactionReceipt receipt = resp.getTransactionReceipt();
        if (receipt == null) {
            throw new IllegalStateException("Empty transaction receipt for function: " + functionName);
        }
        if (!receipt.isStatusOK()) {
            throw new RuntimeException("Transaction failed: function=" + functionName
                    + ", status=" + receipt.getStatus()
                    + ", statusMsg=" + receipt.getStatusMsg()
                    + ", txHash=" + receipt.getTransactionHash());
        }
        return receipt.getTransactionHash();
    }

    @Override
    public String generateBlockchainAddress() {
        if (!available) {
            return BlockchainAnchorService.super.generateBlockchainAddress();
        }
        CryptoKeyPair newKeyPair = client.getCryptoSuite().createKeyPair();
        return newKeyPair.getAddress();
    }

    @Override
    public BlockchainAccount generateBlockchainAccount() {
        if (!available) {
            return BlockchainAnchorService.super.generateBlockchainAccount();
        }
        //随机生成公私钥
        CryptoKeyPair newKeyPair = client.getCryptoSuite().createKeyPair();
        //取出由公钥算出的地址，与私钥并返回
        return new BlockchainAccount(newKeyPair.getAddress(), newKeyPair.getHexPrivateKey());
    }

    public String sendTransactionByPrivateKey(String privateKeyHex, String functionName, List<Object> params) throws Exception {
        if (!available || contractAddress == null || contractAddress.trim().isEmpty()) {
            throw new IllegalStateException("FISCO BCOS not available or contract address not set");
        }
        if (privateKeyHex == null || privateKeyHex.trim().isEmpty()) {
            throw new IllegalArgumentException("privateKeyHex is empty");
        }
        CryptoKeyPair keyPair = client.getCryptoSuite().createKeyPair(privateKeyHex.trim());
        AssembleTransactionProcessor processor = TransactionProcessorFactory.createAssembleTransactionProcessor(
                client, keyPair, abiPath, binPath);
        TransactionResponse resp = processor.sendTransactionAndGetResponseByContractLoader(
                CONTRACT_NAME, contractAddress, functionName, params);
        org.fisco.bcos.sdk.model.TransactionReceipt receipt = resp.getTransactionReceipt();
        if (receipt == null) {
            throw new IllegalStateException("Empty transaction receipt for function: " + functionName);
        }
        if (!receipt.isStatusOK()) {
            throw new RuntimeException("Transaction failed: function=" + functionName
                    + ", status=" + receipt.getStatus()
                    + ", statusMsg=" + receipt.getStatusMsg()
                    + ", txHash=" + receipt.getTransactionHash());
        }
        return receipt.getTransactionHash();
    }

    public boolean isAvailable() {
        return available;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public Client getClient() {
        return client;
    }

    @PreDestroy
    public void destroy() {
        if (sdk != null) {
            try {
                sdk.stopAll();
                log.info("FISCO BCOS SDK stopped");
            } catch (Exception e) {
                log.warn("Error stopping FISCO BCOS SDK: {}", e.getMessage());
            }
        }
    }
}
