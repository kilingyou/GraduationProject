package com.scm.module.assembler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scm.module.assembler.entity.AssemblyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssemblyRecordMapper extends BaseMapper<AssemblyRecord> {

    @Select("SELECT * FROM bus_assembly_record WHERE JSON_CONTAINS(ecid_list, JSON_QUOTE(#{ecid}), '$') LIMIT 1")
    AssemblyRecord findOneByContainingEcid(@Param("ecid") String ecid);

    @Select("SELECT COALESCE(SUM(JSON_LENGTH(ecid_list)), 0) FROM bus_assembly_record WHERE assembler_id = #{assemblerId}")
    Long sumEcidCountByAssembler(@Param("assemblerId") Long assemblerId);
}
