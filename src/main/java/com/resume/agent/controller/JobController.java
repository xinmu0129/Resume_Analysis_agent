package com.resume.agent.controller;

import com.resume.agent.common.BusinessException;
import com.resume.agent.common.ErrorCode;
import com.resume.agent.common.Result;
import com.resume.agent.model.dto.JobTextRequest;
import com.resume.agent.model.vo.JobUploadVO;
import com.resume.agent.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/job")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /**
     * 上传 JD 文件 (pdf/doc/docx/txt)
     */
    @PostMapping("/upload")
    public Result<JobUploadVO> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要上传的文件");
        }
        JobUploadVO vo = jobService.upload(file);
        log.info("JD上传成功: id={}, title={}", vo.getId(), vo.getTitle());
        return Result.success(vo);
    }

    /**
     * 提交 JD 文本 (JSON: title, company, content)
     */
    @PostMapping("/text")
    public Result<JobUploadVO> submitText(@RequestBody JobTextRequest request) {
        JobUploadVO vo = jobService.submitText(request);
        log.info("JD文本提交成功: id={}, title={}", vo.getId(), vo.getTitle());
        return Result.success(vo);
    }

}
