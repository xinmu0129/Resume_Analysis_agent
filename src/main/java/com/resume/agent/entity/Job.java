package com.resume.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("job")
public class Job {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位名称 */
    private String title;

    /** 公司名称 */
    private String company;

    /** JD原始文本 */
    private String rawText;

    /** 录入方式: UPLOAD / TEXT */
    private String sourceType;

    /** 文件路径 (UPLOAD时) */
    private String sourcePath;

    /** 原始文件名 (UPLOAD时) */
    private String fileName;

    /** 文件大小(字节) (UPLOAD时) */
    private Long fileSize;

    /** 状态: UPLOADING / PARSING / PARSED / FAILED */
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
