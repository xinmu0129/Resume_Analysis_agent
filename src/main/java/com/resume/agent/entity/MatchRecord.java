package com.resume.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("match_record")
public class MatchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 简历ID */
    private Long resumeId;

    /** 岗位JD ID */
    private Long jobId;

    /** 匹配得分 (0-100) */
    private Integer matchScore;

    /** 是否匹配 (0=否, 1=是) */
    private Integer isMatched;

    /** 优势列表 (JSON数组) */
    private String strengths;

    /** 差距列表 (JSON数组) */
    private String gaps;

    /** 优化建议列表 (JSON数组) */
    private String suggestions;

    /** LLM原始响应 */
    private String rawResponse;

    /** 状态: PROCESSING / COMPLETED / FAILED */
    private String status;

    /** 创建时间 (MySQL DEFAULT CURRENT_TIMESTAMP) */
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private LocalDateTime createTime;

    /** 更新时间 (MySQL ON UPDATE CURRENT_TIMESTAMP) */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updateTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

}
