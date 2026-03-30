package com.scm.module.assembler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.mapper.AssemblyRecordMapper;
import com.scm.module.assembler.service.AssemblyRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AssemblyRecordServiceImpl
        extends ServiceImpl<AssemblyRecordMapper, AssemblyRecord>
        implements AssemblyRecordService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Random RANDOM = new Random();

    @Override
    public AssemblyRecord createRecord(AssemblyRecord record) {
        if (record.getSn() == null || record.getSn().isEmpty()) {
            record.setSn(generateSn());
        }
        if (record.getStatus() == null) {
            record.setStatus("ASSEMBLED");
        }
        if (record.getChainRegistered() == null) {
            record.setChainRegistered(0);
        }
        if (record.getAssemblyTime() == null) {
            record.setAssemblyTime(LocalDateTime.now());
        }
        save(record);
        return record;
    }

    @Override
    public IPage<AssemblyRecord> listByBatch(String batchNo, Page<AssemblyRecord> page) {
        return page(page, new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getAssemblyBatchNo, batchNo)
                .orderByDesc(AssemblyRecord::getCreateTime));
    }

    @Override
    public AssemblyRecord listBySn(String sn) {
        return getOne(new LambdaQueryWrapper<AssemblyRecord>()
                .eq(AssemblyRecord::getSn, sn));
    }

    @Override
    public boolean registerOnChain(List<Long> ids) {
        List<AssemblyRecord> records = listByIds(ids);
        for (AssemblyRecord record : records) {
            record.setChainRegistered(1);
            record.setTxHash("0x_stub_" + record.getSn());
        }
        return updateBatchById(records);
    }

    private String generateSn() {
        String dateStr = LocalDate.now().format(DATE_FMT);
        String random = String.format("%06d", RANDOM.nextInt(1000000));
        return "SN-" + dateStr + "-" + random;
    }
}
