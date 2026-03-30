package com.scm.module.distributor.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scm.module.distributor.entity.TransferEvent;

import java.util.List;

public interface TransferEventService extends IService<TransferEvent> {

    TransferEvent createTransfer(TransferEvent transfer);

    IPage<TransferEvent> listBySender(Long senderId, Page<TransferEvent> page);

    IPage<TransferEvent> listByReceiver(Long receiverId, Page<TransferEvent> page);

    List<TransferEvent> listBySn(String sn);
}
