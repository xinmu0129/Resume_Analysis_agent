package com.resume.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 文件类型 (pdf/doc/docx) */
    private String fileType;

    /** 存储相对路径 */
    private String filePath;

    /** 文件大小(字节) */
    private Long fileSize;

    /** Tika提取的原始文本 */
    private String rawText;

    /** 清洗后文本 (供LLM使用) */
    private String cleanedText;

    /** 提取的技能(逗号分隔) */
    private String skills;

    /** 工作年限 */
    private String experienceYears;

    /** 学历 */
    private String education;

    /** 状态: UPLOADED / PARSING / PARSED / FAILED */
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
