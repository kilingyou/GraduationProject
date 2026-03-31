package com.scm.module.enduser.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("bus_user_product")
public class UserProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sn;

    private LocalDateTime bindTime;

    private String verifyStatus;

    private String txHash;
}
