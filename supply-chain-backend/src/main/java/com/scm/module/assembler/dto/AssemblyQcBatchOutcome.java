package com.scm.module.assembler.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class AssemblyQcBatchOutcome {

    private int updated;
    private final List<String> failed = new ArrayList<>();
}
