package com.resume.agent.service;

import com.resume.agent.model.vo.ResumeUploadVO;
import org.springframework.web.multipart.MultipartFile;

public interface ResumeService {

    /**
     * 上传简历文件 — 校验 → 落盘 → Tika提取文本 → 入库
     *
     * @param file 上传文件
     * @return 简历摘要信息
     */
    ResumeUploadVO upload(MultipartFile file);

}
