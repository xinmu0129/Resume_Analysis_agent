package com.resume.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.resume.agent.entity.Job;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobMapper extends BaseMapper<Job> {
}
