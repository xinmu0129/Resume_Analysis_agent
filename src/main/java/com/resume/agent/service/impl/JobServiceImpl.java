package com.resume.agent.service.impl;

import com.resume.agent.common.BusinessException;
import com.resume.agent.common.ErrorCode;
import com.resume.agent.entity.Job;
import com.resume.agent.mapper.JobMapper;
import com.resume.agent.model.dto.JobTextRequest;
import com.resume.agent.model.vo.JobUploadVO;
import com.resume.agent.service.JobService;
import com.resume.agent.util.TextAnalyzer;
import com.resume.agent.util.TextCleaner;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class JobServiceImpl implements JobService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt");

    private final JobMapper jobMapper;
    private final Path uploadDir;
    private final long maxSize;

    public JobServiceImpl(
            JobMapper jobMapper,
            @Value("${job.upload.path}") String uploadPath,
            @Value("${job.upload.max-size}") long maxSize) {
        this.jobMapper = jobMapper;
        this.uploadDir = Path.of(uploadPath).toAbsolutePath().normalize();
        this.maxSize = maxSize;
    }

    // ---------- 上传文件 ----------

    @Override
    public JobUploadVO upload(MultipartFile file) {
        validateFile(file);

        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);

        // 落盘
        String storedName = UUID.randomUUID() + "." + extension;
        Path filePath;
        try {
            Files.createDirectories(uploadDir);
            filePath = uploadDir.resolve(storedName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.error("JD文件写入失败: {}", storedName, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        // Tika 提取
        String rawText;
        try {
            rawText = extractText(file);
        } catch (Exception e) {
            log.error("JD文本提取失败: {}", originalName, e);
            throw new BusinessException(ErrorCode.FILE_PARSE_ERROR);
        }

        String title = stripExtension(originalName);

        // ==== Layer 1+2: 清洗 + 结构化提取 ====
        Job job = buildJobEntity(title, null, rawText, "UPLOAD", filePath.toString(), originalName, file.getSize());
        jobMapper.insert(job);

        Job saved = jobMapper.selectById(job.getId());
        return toVO(saved);
    }

    // ---------- 提交文本 ----------

    @Override
    public JobUploadVO submitText(JobTextRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "title 不能为空");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "content 不能为空");
        }

        String rawText = request.getContent().trim();
        String company = request.getCompany() != null ? request.getCompany().trim() : null;

        // ==== Layer 1+2: 清洗 + 结构化提取 ====
        Job job = buildJobEntity(request.getTitle().trim(), company, rawText, "TEXT", null, null, null);
        jobMapper.insert(job);

        Job saved = jobMapper.selectById(job.getId());
        return toVO(saved);
    }

    // ---------- private: 清洗 + 分析 ----------

    private Job buildJobEntity(String title, String company, String rawText,
                               String sourceType, String sourcePath, String fileName, Long fileSize) {
        String cleanedText = TextCleaner.clean(rawText);
        TextAnalyzer.AnalysisResult analysis = TextAnalyzer.analyze(cleanedText);

        log.info("JD文本清洗: {} chars → {} chars, skills=[{}], experience=[{}]",
                rawText.length(), cleanedText.length(), analysis.skills(), analysis.experience());

        Job job = new Job();
        job.setTitle(title);
        job.setCompany(company);
        job.setRawText(rawText);
        job.setCleanedText(cleanedText);
        job.setSkills(analysis.skills());
        job.setExperienceRequired(analysis.experience());
        job.setSourceType(sourceType);
        job.setSourcePath(sourcePath);
        job.setFileName(fileName);
        job.setFileSize(fileSize);
        job.setStatus("PARSED");
        return job;
    }

    // ---------- private: 文件处理 ----------

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String ext = getExtension(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "仅支持 PDF / DOC / DOCX / TXT");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return null;
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private String stripExtension(String filename) {
        if (filename == null) return "未命名";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String extractText(MultipartFile file) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream in = file.getInputStream()) {
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(in, handler, metadata, context);
        }
        return handler.toString().trim();
    }

    private JobUploadVO toVO(Job job) {
        return JobUploadVO.builder()
                .id(job.getId())
                .title(job.getTitle())
                .company(job.getCompany())
                .createTime(job.getCreateTime())
                .build();
    }

}
