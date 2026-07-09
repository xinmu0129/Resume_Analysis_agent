package com.resume.agent.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MatchResultVO {

    /** 匹配记录ID */
    private Long id;

    /** 简历ID */
    private Long resumeId;

    /** 岗位JD ID */
    private Long jobId;

    /** 匹配得分 (0-100) */
    private Integer matchScore;

    /** 是否匹配 */
    private Boolean isMatched;

    /** 优势列表 */
    private List<String> strengths;

    /** 差距列表 */
    private List<String> gaps;

    /** 优化建议列表 */
    private List<String> suggestions;

    /** 创建时间 */
    private LocalDateTime createTime;

}
