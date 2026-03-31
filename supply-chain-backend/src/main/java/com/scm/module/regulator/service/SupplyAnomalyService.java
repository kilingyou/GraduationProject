package com.scm.module.regulator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scm.module.distributor.entity.SalesRecord;
import com.scm.module.distributor.entity.TransferEvent;
import com.scm.module.distributor.service.SalesRecordService;
import com.scm.module.distributor.service.TransferEventService;
import com.scm.module.enduser.entity.UserProduct;
import com.scm.module.enduser.service.UserProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 串货 / 流通一致性异常分析（销售、物流、多用户绑定等规则）。
 */
@Service
@RequiredArgsConstructor
public class SupplyAnomalyService {

    private final TransferEventService transferEventService;
    private final SalesRecordService salesRecordService;
    private final UserProductService userProductService;

    public Map<String, Object> analyzeSn(String sn) {
        Map<String, Object> out = new HashMap<>();
        if (sn == null || sn.trim().isEmpty()) {
            out.put("sn", sn);
            out.put("riskFlags", new ArrayList<String>());
            out.put("riskLevel", "LOW");
            return out;
        }
        String norm = sn.trim();
        out.put("sn", norm);

        List<TransferEvent> transfers = transferEventService.listBySn(norm);
        SalesRecord sale = salesRecordService.getLatestBySn(norm);
        Long bindCount = userProductService.count(new LambdaQueryWrapper<UserProduct>()
                .eq(UserProduct::getSn, norm));

        boolean noTransfer = transfers == null || transfers.isEmpty();
        boolean noSale = sale == null;
        boolean multiBind = bindCount != null && bindCount > 1;
        boolean bindWithoutSale = (bindCount != null && bindCount > 0) && noSale;
        boolean soldWithoutDelivery = !noSale && noTransfer;

        Long finalReceiver = null;
        if (!noTransfer) {
            TransferEvent last = transfers.get(transfers.size() - 1);
            finalReceiver = last.getReceiverId();
        }
        boolean receiverSellerMismatch = !noSale && finalReceiver != null
                && sale.getSellerId() != null && !sale.getSellerId().equals(finalReceiver);

        List<String> risks = new ArrayList<>();
        if (soldWithoutDelivery) risks.add("SALE_WITHOUT_TRANSFER");
        if (receiverSellerMismatch) risks.add("RECEIVER_SELLER_MISMATCH");
        if (multiBind) risks.add("MULTI_USER_BIND");
        if (bindWithoutSale) risks.add("BIND_WITHOUT_SALE");
        if (noSale) risks.add("UNSOLD");

        out.put("transferCount", transfers == null ? 0 : transfers.size());
        out.put("latestTransfer", noTransfer ? null : transfers.get(transfers.size() - 1));
        out.put("latestSale", sale);
        out.put("bindCount", bindCount);
        out.put("riskFlags", risks);
        out.put("riskLevel", risks.size() >= 3 ? "HIGH" : (risks.isEmpty() ? "LOW" : "MEDIUM"));
        return out;
    }
}
