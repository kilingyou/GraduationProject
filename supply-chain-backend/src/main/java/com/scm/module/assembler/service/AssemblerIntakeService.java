package com.scm.module.assembler.service;

import com.scm.module.assembler.dto.IntakeVerifyResult;

import java.util.List;

public interface AssemblerIntakeService {

    IntakeVerifyResult verifyEcid(String ecid);

    List<IntakeVerifyResult> verifyEcids(List<String> ecids);
}
