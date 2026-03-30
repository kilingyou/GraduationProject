package com.scm.module.enduser.service.impl;

import com.scm.module.assembler.entity.AssemblyRecord;
import com.scm.module.assembler.service.AssemblyRecordService;
import com.scm.module.distributor.entity.TransferEvent;
import com.scm.module.distributor.service.TransferEventService;
import com.scm.module.enduser.service.TraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TraceServiceImpl implements TraceService {

    private final AssemblyRecordService assemblyRecordService;
    private final TransferEventService transferEventService;

    @Override
    public Map<String, Object> traceProduct(String sn) {
        Map<String, Object> trace = new HashMap<>();
        trace.put("sn", sn);

        AssemblyRecord record = assemblyRecordService.listBySn(sn);
        if (record != null) {
            trace.put("assemblyRecord", record);
            trace.put("ecidList", record.getEcidList());
            trace.put("assemblyTime", record.getAssemblyTime());
            trace.put("firmwareVersion", record.getFirmwareVersion());
            trace.put("status", record.getStatus());
        }

        List<TransferEvent> transfers = transferEventService.listBySn(sn);
        trace.put("transferEvents", transfers);

        return trace;
    }
}
