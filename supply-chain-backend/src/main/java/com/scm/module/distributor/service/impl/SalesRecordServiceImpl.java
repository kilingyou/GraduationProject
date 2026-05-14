package com.scm.module.distributor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.SmartContractInvokeService;
import com.scm.integration.evidence.EvidenceStorageService;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.module.distributor.entity.SalesRecord;
import com.scm.module.distributor.mapper.SalesRecordMapper;
import com.scm.module.distributor.service.SalesRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class SalesRecordServiceImpl
        extends ServiceImpl<SalesRecordMapper, SalesRecord>
        implements SalesRecordService {

    private final EvidenceStorageService evidenceStorageService;
    private final SmartContractInvokeService smartContractInvokeService;
    private final AssemblyRecordService assemblyRecordService;

    /**
     * 登记产品销售记录，完成客户信息处理、发票存证、合约销售事件上链与状态流转。
     *
     * @param sn 产品唯一序列号
     * @param saleTime 销售时间，为空则使用当前时间
     * @param customerName 客户姓名（匿名销售可为空）
     * @param customerPhone 客户手机号（匿名销售可为空）
     * @param invoice 发票文件（可选）
     * @param sellerId 销售登记人 ID（需为当前货权方）
     * @param anonymous 是否匿名销售
     * @param customerSegment 客户分层标签（可选）
     * @return 已保存的销售记录
     * @throws IOException 发票文件读取或存证失败时抛出
     */
    @Override
    public SalesRecord registerSale(String sn, LocalDateTime saleTime, String customerName, String customerPhone,
                                    MultipartFile invoice, Long sellerId, boolean anonymous, String customerSegment)
            throws IOException {
        // 基础参数校验：SN 不能为空
        if (sn == null || sn.trim().isEmpty()) {
            throw new BusinessException("SN 不能为空");
        }
        String snNorm = sn.trim();
        // 幂等保护：同一 SN 只允许登记一次销售记录
        long dup = count(new LambdaQueryWrapper<SalesRecord>().eq(SalesRecord::getSn, snNorm));
        if (dup > 0) {
            throw new BusinessException("该 SN 已登记过销售，不能重复登记");
        }

        // 校验组装记录存在且当前登录方拥有该产品货权
        AssemblyRecord ar = assemblyRecordService.listBySn(snNorm);
        if (ar == null) {
            throw new BusinessException("未找到该 SN 的组装记录，无法销售");
        }
        if (ar.getCurrentHolderId() == null || !ar.getCurrentHolderId().equals(sellerId)) {
            throw new BusinessException("只有当前货权方可登记销售（请先完成物流收货）");
        }
        // 仅允许在库状态销售；组装商可销售已上链但未出库且货权仍归自己的产品
        boolean saleable = "IN_STOCK".equals(ar.getStatus())
                || ("ON_CHAIN".equals(ar.getStatus()) && sellerId.equals(ar.getAssemblerId()));
        if (!saleable) {
            throw new BusinessException("仅「在库」可销售；组装商可对已上链未出库的自有货权产品登记销售。当前状态: " + ar.getStatus());
        }

        // 构建销售记录主体，销售时间为空时默认当前时间
        SalesRecord sale = new SalesRecord();
        sale.setSn(snNorm);
        sale.setSellerId(sellerId);
        LocalDateTime st = saleTime != null ? saleTime : LocalDateTime.now();
        sale.setSaleTime(st);

        // 匿名销售：不保存客户明文信息，仅保存匿名标识与客户哈希
        if (anonymous) {
            sale.setCustomerAnonymous(1);
            sale.setCustomerNameEnc(null);
            sale.setCustomerPhoneEnc(null);
            String anonPayload = "ANONYMOUS|" + snNorm + "|" + st;
            sale.setCustomerHash(HashUtil.sha256Hex(anonPayload.getBytes(StandardCharsets.UTF_8)));
        } else {
            // 实名销售：客户姓名/手机号采用 Base64 编码存储，并生成哈希摘要用于核验
            sale.setCustomerAnonymous(0);
            String cn = customerName != null ? customerName : "";
            String cp = customerPhone != null ? customerPhone : "";
            sale.setCustomerNameEnc(Base64.getEncoder().encodeToString(cn.getBytes(StandardCharsets.UTF_8)));
            sale.setCustomerPhoneEnc(Base64.getEncoder().encodeToString(cp.getBytes(StandardCharsets.UTF_8)));
            sale.setCustomerHash(HashUtil.sha256Hex((cn + "|" + cp).getBytes(StandardCharsets.UTF_8)));
        }
        // 客群标签统一规范为大写后保存
        if (customerSegment != null && !customerSegment.trim().isEmpty()) {
            sale.setCustomerSegment(customerSegment.trim().toUpperCase());
        }

        // 可选发票附件：存证后记录文件哈希与 CID
        if (invoice != null && !invoice.isEmpty()) {
            EvidenceStorageService.StoredEvidence ev = evidenceStorageService.store(
                    invoice.getBytes(), invoice.getOriginalFilename(), "SALE_INVOICE");
            sale.setInvoiceHash(ev.getFileHash());
            sale.setInvoiceCid(ev.getIpfsCid());
        }

        // 仅通过合约 registerSale 一笔交易上链；落库 tx_hash 为该交易哈希（不再额外 anchor）
        sale.setTxHash(smartContractInvokeService.registerSale(
                sale.getSn(), sale.getCustomerHash(), sale.getInvoiceHash()));

        // 持久化销售记录，并将组装记录状态流转为已售
        save(sale);

        ar.setStatus("SOLD");
        assemblyRecordService.updateById(ar);

        return sale;
    }

    @Override
    public IPage<SalesRecord> listBySeller(Long sellerId, Page<SalesRecord> page) {
        return page(page, new LambdaQueryWrapper<SalesRecord>()
                .eq(SalesRecord::getSellerId, sellerId)
                .orderByDesc(SalesRecord::getCreateTime));
    }

    @Override
    public SalesRecord getLatestBySn(String sn) {
        return getOne(new LambdaQueryWrapper<SalesRecord>()
                .eq(SalesRecord::getSn, sn)
                .orderByDesc(SalesRecord::getCreateTime)
                .last("LIMIT 1"));
    }
}
