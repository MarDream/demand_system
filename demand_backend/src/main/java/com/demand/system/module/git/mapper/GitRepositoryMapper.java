package com.demand.system.module.git.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demand.system.module.git.entity.GitRepository;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GitRepositoryMapper extends BaseMapper<GitRepository> {
}
