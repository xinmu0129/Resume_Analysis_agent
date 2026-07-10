-- ============================================
-- resume-analysis-agent — 初始化数据库
-- ============================================

CREATE DATABASE IF NOT EXISTS resume_analysis
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE resume_analysis;

-- 简历表
CREATE TABLE IF NOT EXISTS resume (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    file_name   VARCHAR(255)    NOT NULL                COMMENT '原始文件名',
    file_type   VARCHAR(20)     NOT NULL                COMMENT '文件类型 (pdf/doc/docx)',
    file_path   VARCHAR(500)    NOT NULL                COMMENT '存储相对路径',
    file_size   BIGINT          NOT NULL  DEFAULT 0     COMMENT '文件大小(字节)',
    raw_text    LONGTEXT        NULL                    COMMENT 'Tika提取的原始文本',
    cleaned_text LONGTEXT       NULL                    COMMENT '清洗后文本 (供LLM使用)',
    skills      TEXT            NULL                    COMMENT '提取的技能(逗号分隔)',
    experience_years VARCHAR(100) NULL                  COMMENT '工作年限',
    education   VARCHAR(100)    NULL                    COMMENT '学历',
    status      VARCHAR(20)     NOT NULL  DEFAULT 'UPLOADED' COMMENT '状态: UPLOADED/PARSING/PARSED/FAILED',
    create_time DATETIME        NOT NULL  DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT(1)      NOT NULL  DEFAULT 0     COMMENT '逻辑删除 (0=正常, 1=已删除)',
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历表';

-- 岗位JD表
CREATE TABLE IF NOT EXISTS job (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    title       VARCHAR(255)    NOT NULL                COMMENT '岗位名称',
    company     VARCHAR(255)    NULL                    COMMENT '公司名称',
    raw_text    LONGTEXT        NULL                    COMMENT 'JD原始文本',
    cleaned_text LONGTEXT       NULL                    COMMENT '清洗后文本 (供LLM使用)',
    skills      TEXT            NULL                    COMMENT '要求的技能(逗号分隔)',
    experience_required VARCHAR(100) NULL               COMMENT '经验要求',
    source_type VARCHAR(20)     NOT NULL                COMMENT '录入方式: UPLOAD / TEXT',
    source_path VARCHAR(500)    NULL                    COMMENT '文件路径 (UPLOAD时)',
    file_name   VARCHAR(255)    NULL                    COMMENT '原始文件名 (UPLOAD时)',
    file_size   BIGINT          NULL      DEFAULT 0     COMMENT '文件大小(字节) (UPLOAD时)',
    status      VARCHAR(20)     NOT NULL  DEFAULT 'PARSED' COMMENT '状态: UPLOADING/PARSING/PARSED/FAILED',
    create_time DATETIME        NOT NULL  DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT(1)      NOT NULL  DEFAULT 0     COMMENT '逻辑删除 (0=正常, 1=已删除)',
    INDEX idx_source_type (source_type),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位JD表';

-- 匹配记录表
CREATE TABLE IF NOT EXISTS match_record (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    resume_id   BIGINT          NOT NULL                COMMENT '简历ID',
    job_id      BIGINT          NOT NULL                COMMENT '岗位JD ID',
    match_score INT             NOT NULL  DEFAULT 0     COMMENT '匹配得分 (0-100)',
    is_matched  TINYINT(1)      NOT NULL  DEFAULT 0     COMMENT '是否匹配 (0=否, 1=是)',
    strengths   TEXT            NULL                    COMMENT '优势列表 (JSON数组)',
    gaps        TEXT            NULL                    COMMENT '差距列表 (JSON数组)',
    suggestions TEXT            NULL                    COMMENT '优化建议列表 (JSON数组)',
    raw_response LONGTEXT       NULL                    COMMENT 'LLM原始响应',
    status      VARCHAR(20)     NOT NULL  DEFAULT 'COMPLETED' COMMENT '状态: PROCESSING/COMPLETED/FAILED',
    create_time DATETIME        NOT NULL  DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT(1)      NOT NULL  DEFAULT 0     COMMENT '逻辑删除',
    INDEX idx_resume_id (resume_id),
    INDEX idx_job_id (job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='匹配记录表';

-- ============================================
-- 迁移: Token 优化新增列 (已有数据库执行)
-- 首次建库会自动包含上述列，迁移仅对旧库生效
-- ============================================
ALTER TABLE resume
    ADD COLUMN IF NOT EXISTS cleaned_text    LONGTEXT       NULL COMMENT '清洗后文本 (供LLM使用)' AFTER raw_text,
    ADD COLUMN IF NOT EXISTS skills          TEXT           NULL COMMENT '提取的技能(逗号分隔)'   AFTER cleaned_text,
    ADD COLUMN IF NOT EXISTS experience_years VARCHAR(100)  NULL COMMENT '工作年限'              AFTER skills,
    ADD COLUMN IF NOT EXISTS education       VARCHAR(100)   NULL COMMENT '学历'                  AFTER experience_years;

ALTER TABLE job
    ADD COLUMN IF NOT EXISTS cleaned_text        LONGTEXT   NULL COMMENT '清洗后文本 (供LLM使用)' AFTER raw_text,
    ADD COLUMN IF NOT EXISTS skills              TEXT       NULL COMMENT '要求的技能(逗号分隔)'   AFTER cleaned_text,
    ADD COLUMN IF NOT EXISTS experience_required VARCHAR(100) NULL COMMENT '经验要求'            AFTER skills;
