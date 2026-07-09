package com.resume.agent.service;

import com.resume.agent.model.vo.MatchResultVO;

public interface MatchService {

    /**
     * 分析简历与 JD 的匹配度
     *
     * @param resumeId 简历ID
     * @param jobId    岗位JD ID
     * @return 匹配结果
     */
    MatchResultVO analyze(Long resumeId, Long jobId);

}
