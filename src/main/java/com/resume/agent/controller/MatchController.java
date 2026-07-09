package com.resume.agent.controller;

import com.resume.agent.common.BusinessException;
import com.resume.agent.common.ErrorCode;
import com.resume.agent.common.Result;
import com.resume.agent.model.vo.MatchResultVO;
import com.resume.agent.service.MatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    /**
     * 简历-JD 匹配分析
     *
     * @param body { "resumeId": 1, "jobId": 1 }
     */
    @PostMapping("/analyze")
    public Result<MatchResultVO> analyze(@RequestBody Map<String, Long> body) {
        Long resumeId = body.get("resumeId");
        Long jobId = body.get("jobId");

        if (resumeId == null || jobId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "resumeId 和 jobId 均为必填");
        }

        log.info("匹配分析请求: resumeId={}, jobId={}", resumeId, jobId);
        MatchResultVO vo = matchService.analyze(resumeId, jobId);
        log.info("匹配分析完成: id={}, score={}, matched={}", vo.getId(), vo.getMatchScore(), vo.getIsMatched());
        return Result.success(vo);
    }

}
