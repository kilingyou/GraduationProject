package com.scm.module.distributor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.common.exception.BusinessException;
import com.scm.common.util.HashUtil;
import com.scm.integration.blockchain.BlockchainAnchorService;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.module.distributor.entity.TransferEvent;
import com.scm.module.distributor.mapper.TransferEventMapper;
import com.scm.module.distributor.service.TransferEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferEventServiceImpl
        extends ServiceImpl<TransferEventMapper, TransferEvent>
        implements TransferEventService {

    private final AssemblyRecordService assemblyRecordService;
    private final BlockchainAnchorService blockchainAnchorService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferEvent shipTransfer(String sn, Long senderId, Long receiverId, String logisticsCompany,
                                      String trackingNumber, LocalDateTime shipTime, LocalDateTime estimatedArrival,
                                      String transferType) {
        if (!StringUtils.hasText(sn)) {
            throw new BusinessException("SN 不能为空");
        }
        if (receiverId == null || senderId.equals(receiverId)) {
            throw new BusinessException("接收方无效");
        }
        if (!StringUtils.hasText(trackingNumber)) {
            throw new BusinessException("请填写物流单号");
        }
        if (!StringUtils.hasText(logisticsCompany)) {
            throw new BusinessException("请填写物流公司");
        }

        String snNorm = sn.trim();
        AssemblyRecord ar = assemblyRecordService.listBySn(snNorm);
        if (ar == null) {
            throw new BusinessException("未找到该 SN 的组装记录，无法发货");
        }
        if (ar.getCurrentHolderId() == null || !ar.getCurrentHolderId().equals(senderId)) {
            throw new BusinessException("无权发货：您不是该产品的当前货权方（需先完成上链与收货流程）");
        }
        if ("SOLD".equals(ar.getStatus()) || "DECOMMISSIONED".equals(ar.getStatus())) {
            throw new BusinessException("该产品状态不允许再流转");
        }

        long open = count(new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getSn, snNorm)
                .in(TransferEvent::getStatus, "PENDING", "IN_TRANSIT"));
        if (open > 0) {
            throw new BusinessException("该 SN 已有在途物流单，请先由收货方确认收货");
        }

        LocalDateTime st = shipTime != null ? shipTime : LocalDateTime.now();
        TransferEvent te = new TransferEvent()
                .setSn(snNorm)
                .setBatchNo(ar.getAssemblyBatchNo())
                .setLogisticsCompany(logisticsCompany.trim())
                .setTrackingNumber(trackingNumber.trim())
                .setSenderId(senderId)
                .setReceiverId(receiverId)
                .setTransferType(StringUtils.hasText(transferType) ? transferType.trim() : "SHIP")
                .setShipTime(st)
                .setEstimatedArrival(estimatedArrival)
                .setStatus("IN_TRANSIT");

        String payload = snNorm + "|" + te.getTrackingNumber() + "|" + senderId + "|" + receiverId + "|" + st;
        te.setTxHash(blockchainAnchorService.anchor("TRANSFER_EVENT", HashUtil.sha256Hex(payload)));

        save(te);

        ar.setStatus("IN_TRANSIT");
        assemblyRecordService.updateById(ar);
        return te;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TransferEvent> shipBatch(List<String> sns, Long senderId, Long receiverId, String logisticsCompany,
                                         String trackingNumber, LocalDateTime shipTime, LocalDateTime estimatedArrival,
                                         String transferType) {
        if (sns == null || sns.isEmpty()) {
            throw new BusinessException("请提供至少一个 SN");
        }
        List<String> distinct = sns.stream().filter(StringUtils::hasText).map(String::trim).distinct()
                .collect(Collectors.toList());
        List<TransferEvent> out = new ArrayList<>(distinct.size());
        for (String sn : distinct) {
            out.add(shipTransfer(sn, senderId, receiverId, logisticsCompany, trackingNumber, shipTime,
                    estimatedArrival, transferType));
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferEvent receiveTransfer(Long receiverId, Long transferId, String trackingNumber, String sn) {
        LambdaQueryWrapper<TransferEvent> w = new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getReceiverId, receiverId)
                .in(TransferEvent::getStatus, "PENDING", "IN_TRANSIT");
        if (transferId != null) {
            w.eq(TransferEvent::getId, transferId);
        }
        if (StringUtils.hasText(trackingNumber)) {
            w.eq(TransferEvent::getTrackingNumber, trackingNumber.trim());
        }
        if (StringUtils.hasText(sn)) {
            w.eq(TransferEvent::getSn, sn.trim());
        }
        w.orderByAsc(TransferEvent::getId);
        List<TransferEvent> pending = list(w);
        if (pending.isEmpty()) {
            throw new BusinessException("未找到待收货记录，请核对运单号或 SN");
        }

        LocalDateTime now = LocalDateTime.now();
        TransferEvent first = null;
        for (TransferEvent te : pending) {
            if (first == null) {
                first = te;
            }
            AssemblyRecord ar = assemblyRecordService.listBySn(te.getSn());
            if (ar == null) {
                throw new BusinessException("组装记录异常，SN: " + te.getSn());
            }
            te.setStatus("RECEIVED");
            te.setActualArrival(now);
            String recvPayload = te.getSn() + "|" + te.getTrackingNumber() + "|"
                    + te.getSenderId() + "|" + receiverId + "|RECEIVED|" + now;
            te.setReceiveTxHash(blockchainAnchorService.anchor(
                    "TRANSFER_RECEIVE", HashUtil.sha256Hex(recvPayload)));
            updateById(te);
            ar.setCurrentHolderId(receiverId);
            ar.setStatus("IN_STOCK");
            assemblyRecordService.updateById(ar);
        }
        return first;
    }

    @Override
    public IPage<TransferEvent> listBySender(Long senderId, Page<TransferEvent> page) {
        return page(page, new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getSenderId, senderId)
                .orderByDesc(TransferEvent::getCreateTime));
    }

    @Override
    public IPage<TransferEvent> listByReceiver(Long receiverId, Page<TransferEvent> page) {
        return page(page, new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getReceiverId, receiverId)
                .orderByDesc(TransferEvent::getCreateTime));
    }

    @Override
    public IPage<TransferEvent> listForParticipant(Long userId, Page<TransferEvent> page) {
        return page(page, new LambdaQueryWrapper<TransferEvent>()
                .and(q -> q.eq(TransferEvent::getSenderId, userId).or().eq(TransferEvent::getReceiverId, userId))
                .orderByDesc(TransferEvent::getCreateTime));
    }

    @Override
    public List<TransferEvent> listBySn(String sn) {
        return list(new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getSn, sn)
                .orderByAsc(TransferEvent::getShipTime)
                .orderByAsc(TransferEvent::getCreateTime));
    }
}
