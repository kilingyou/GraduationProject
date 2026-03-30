package com.scm.module.distributor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.distributor.entity.TransferEvent;
import com.scm.module.distributor.mapper.TransferEventMapper;
import com.scm.module.distributor.service.TransferEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferEventServiceImpl
        extends ServiceImpl<TransferEventMapper, TransferEvent>
        implements TransferEventService {

    @Override
    public TransferEvent createTransfer(TransferEvent transfer) {
        if (transfer.getStatus() == null) {
            transfer.setStatus("PENDING");
        }
        if (transfer.getShipTime() == null) {
            transfer.setShipTime(LocalDateTime.now());
        }
        save(transfer);
        return transfer;
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
    public List<TransferEvent> listBySn(String sn) {
        return list(new LambdaQueryWrapper<TransferEvent>()
                .eq(TransferEvent::getSn, sn)
                .orderByAsc(TransferEvent::getShipTime));
    }
}
