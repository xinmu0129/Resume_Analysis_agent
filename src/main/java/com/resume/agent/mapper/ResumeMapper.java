package com.resume.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.resume.agent.entity.Resume;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {
}
