package com.resume.agent.service.impl;

import com.resume.agent.common.BusinessException;
import com.resume.agent.common.ErrorCode;
import com.resume.agent.entity.Resume;
import com.resume.agent.mapper.ResumeMapper;
import com.resume.agent.model.vo.ResumeUploadVO;
import com.resume.agent.service.ResumeService;
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
public class ResumeServiceImpl implements ResumeService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");

    private final ResumeMapper resumeMapper;
    private final Path uploadDir;
    private final long maxSize;

    public ResumeServiceImpl(
            ResumeMapper resumeMapper,
            @Value("${resume.upload.path}") String uploadPath,
            @Value("${resume.upload.max-size}") long maxSize) {
        this.resumeMapper = resumeMapper;
        this.uploadDir = Path.of(uploadPath).toAbsolutePath().normalize();
        this.maxSize = maxSize;
    }

    @Override
    public ResumeUploadVO upload(MultipartFile file) {
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
            log.error("文件写入失败: {}", storedName, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        // Tika 提取文本
        String rawText;
        try {
            rawText = extractText(file);
        } catch (Exception e) {
            log.error("文本提取失败: {}", originalName, e);
            throw new BusinessException(ErrorCode.FILE_PARSE_ERROR);
        }

        // ==== Layer 1: 文本清洗 ====
        String cleanedText = TextCleaner.clean(rawText);
        log.info("文本清洗完成: {} chars → {} chars (减少 {}%)",
                rawText.length(), cleanedText.length(),
                rawText.length() > 0 ? (100 - cleanedText.length() * 100 / rawText.length()) : 0);

        // ==== Layer 2: 结构化提取 ====
        TextAnalyzer.AnalysisResult analysis = TextAnalyzer.analyze(cleanedText);

        // 入库
        Resume resume = new Resume();
        resume.setFileName(originalName);
        resume.setFileType(extension.toLowerCase());
        resume.setFilePath(filePath.toString());
        resume.setFileSize(file.getSize());
        resume.setRawText(rawText);              // 原始 Tika 输出
        resume.setCleanedText(cleanedText);      // 清洗后文本
        resume.setSkills(analysis.skills());
        resume.setExperienceYears(analysis.experience());
        resume.setEducation(analysis.education());
        resume.setStatus("PARSED");

        resumeMapper.insert(resume);

        Resume saved = resumeMapper.selectById(resume.getId());
        log.info("简历结构化提取: skills=[{}], experience=[{}], education=[{}]",
                analysis.skills(), analysis.experience(), analysis.education());

        return ResumeUploadVO.builder()
                .id(saved.getId())
                .fileName(saved.getFileName())
                .fileType(saved.getFileType())
                .fileSize(saved.getFileSize())
                .status(saved.getStatus())
                .createTime(saved.getCreateTime())
                .build();
    }

    // ---------- private ----------

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String ext = getExtension(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "仅支持 PDF / DOC / DOCX");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return null;
        return filename.substring(filename.lastIndexOf('.') + 1);
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

}
