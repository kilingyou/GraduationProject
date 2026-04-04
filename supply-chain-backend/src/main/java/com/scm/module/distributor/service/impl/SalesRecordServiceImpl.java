package com.scm.module.distributor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
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
    private final BlockchainAnchorService blockchainAnchorService;
    private final AssemblyRecordService assemblyRecordService;

    @Override
    public SalesRecord registerSale(String sn, LocalDateTime saleTime, String customerName, String customerPhone,
                                    MultipartFile invoice, Long sellerId, boolean anonymous, String customerSegment)
            throws IOException {
        if (sn == null || sn.trim().isEmpty()) {
            throw new BusinessException("SN 不能为空");
        }
        String snNorm = sn.trim();
        long dup = count(new LambdaQueryWrapper<SalesRecord>().eq(SalesRecord::getSn, snNorm));
        if (dup > 0) {
            throw new BusinessException("该 SN 已登记过销售，不能重复登记");
        }

        AssemblyRecord ar = assemblyRecordService.listBySn(snNorm);
        if (ar == null) {
            throw new BusinessException("未找到该 SN 的组装记录，无法销售");
        }
        if (ar.getCurrentHolderId() == null || !ar.getCurrentHolderId().equals(sellerId)) {
            throw new BusinessException("只有当前货权方可登记销售（请先完成物流收货）");
        }
        boolean saleable = "IN_STOCK".equals(ar.getStatus())
                || ("ON_CHAIN".equals(ar.getStatus()) && sellerId.equals(ar.getAssemblerId()));
        if (!saleable) {
            throw new BusinessException("仅「在库」可销售；组装商可对已上链未出库的自有货权产品登记销售。当前状态: " + ar.getStatus());
        }

        SalesRecord sale = new SalesRecord();
        sale.setSn(snNorm);
        sale.setSellerId(sellerId);
        LocalDateTime st = saleTime != null ? saleTime : LocalDateTime.now();
        sale.setSaleTime(st);

        if (anonymous) {
            sale.setCustomerAnonymous(1);
            sale.setCustomerNameEnc(null);
            sale.setCustomerPhoneEnc(null);
            String anonPayload = "ANONYMOUS|" + snNorm + "|" + st;
            sale.setCustomerHash(HashUtil.sha256Hex(anonPayload.getBytes(StandardCharsets.UTF_8)));
        } else {
            sale.setCustomerAnonymous(0);
            String cn = customerName != null ? customerName : "";
            String cp = customerPhone != null ? customerPhone : "";
            sale.setCustomerNameEnc(Base64.getEncoder().encodeToString(cn.getBytes(StandardCharsets.UTF_8)));
            sale.setCustomerPhoneEnc(Base64.getEncoder().encodeToString(cp.getBytes(StandardCharsets.UTF_8)));
            sale.setCustomerHash(HashUtil.sha256Hex((cn + "|" + cp).getBytes(StandardCharsets.UTF_8)));
        }
        if (customerSegment != null && !customerSegment.trim().isEmpty()) {
            sale.setCustomerSegment(customerSegment.trim().toUpperCase());
        }

        String invoiceHashPart = "";
        if (invoice != null && !invoice.isEmpty()) {
            EvidenceStorageService.StoredEvidence ev = evidenceStorageService.store(
                    invoice.getBytes(), invoice.getOriginalFilename(), "SALE_INVOICE");
            sale.setInvoiceHash(ev.getFileHash());
            sale.setInvoiceCid(ev.getIpfsCid());
            invoiceHashPart = ev.getFileHash();
        }

        String anchorBase = sale.getSn() + "|" + sale.getSaleTime() + "|" + sale.getCustomerHash() + "|" + invoiceHashPart;
        sale.setTxHash(blockchainAnchorService.anchor("SALE_REGISTER", HashUtil.sha256Hex(anchorBase)));

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
