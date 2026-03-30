package com.scm.module.enduser.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scm.module.enduser.entity.UserProduct;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProductMapper extends BaseMapper<UserProduct> {
}
