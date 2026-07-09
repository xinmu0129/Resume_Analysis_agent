package com.resume.agent.controller;

import com.resume.agent.common.BusinessException;
import com.resume.agent.common.ErrorCode;
import com.resume.agent.common.Result;
import com.resume.agent.model.vo.ResumeUploadVO;
import com.resume.agent.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /**
     * 上传简历文件
     *
     * @param file 简历文件 (pdf/doc/docx, 最大10MB)
     * @return 简历摘要信息 (id, fileName, fileType, fileSize, status, createTime)
     */
    @PostMapping("/upload")
    public Result<ResumeUploadVO> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要上传的文件");
        }
        ResumeUploadVO vo = resumeService.upload(file);
        log.info("简历上传成功: id={}, file={}", vo.getId(), vo.getFileName());
        return Result.success(vo);
    }

}
