package com.scm.module.manufacturer.dto;

import lombok.Data;

import java.util.List;

@Data
public class DeviceRegisterRequest {

    private List<Long> ids;

    private List<String> ecids;
}
