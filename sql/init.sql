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
    status      VARCHAR(20)     NOT NULL  DEFAULT 'UPLOADED' COMMENT '状态: UPLOADED/PARSING/PARSED/FAILED',
    create_time DATETIME        NOT NULL  DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT(1)      NOT NULL  DEFAULT 0     COMMENT '逻辑删除 (0=正常, 1=已删除)',
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历表';
