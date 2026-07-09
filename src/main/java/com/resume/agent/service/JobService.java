package com.resume.agent.service;

import com.resume.agent.model.dto.JobTextRequest;
import com.resume.agent.model.vo.JobUploadVO;
import org.springframework.web.multipart.MultipartFile;

public interface JobService {

    /**
     * 上传 JD 文件 → Tika 提取文本 → 入库
     */
    JobUploadVO upload(MultipartFile file);

    /**
     * 提交 JD 文本 → 入库
     */
    JobUploadVO submitText(JobTextRequest request);

}
