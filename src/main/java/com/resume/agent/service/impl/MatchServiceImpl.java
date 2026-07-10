package com.resume.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.agent.common.BusinessException;
import com.resume.agent.common.ErrorCode;
import com.resume.agent.config.DeepSeekClient;
import com.resume.agent.entity.Job;
import com.resume.agent.entity.MatchRecord;
import com.resume.agent.entity.Resume;
import com.resume.agent.mapper.JobMapper;
import com.resume.agent.mapper.MatchRecordMapper;
import com.resume.agent.mapper.ResumeMapper;
import com.resume.agent.model.vo.MatchResultVO;
import com.resume.agent.service.MatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class MatchServiceImpl implements MatchService {

    /** 输入文本最大字符数 (1 char ≈ 0.3~0.5 token) */
    private final int maxInputChars;

    private final ResumeMapper resumeMapper;
    private final JobMapper jobMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    public MatchServiceImpl(
            ResumeMapper resumeMapper,
            JobMapper jobMapper,
            MatchRecordMapper matchRecordMapper,
            DeepSeekClient deepSeekClient,
            ObjectMapper objectMapper,
            @Value("${match.prompt.max-input-chars:6000}") int maxInputChars) {
        this.resumeMapper = resumeMapper;
        this.jobMapper = jobMapper;
        this.matchRecordMapper = matchRecordMapper;
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = objectMapper;
        this.maxInputChars = maxInputChars;
    }

    @Override
    public MatchResultVO analyze(Long resumeId, Long jobId) {
        // 1. 加载数据
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "简历不存在: id=" + resumeId);
        }
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "岗位不存在: id=" + jobId);
        }

        // ==== Layer 3: 缓存检查 ====
        MatchRecord cached = findCachedResult(resumeId, jobId);
        if (cached != null && isCacheValid(cached, resume, job)) {
            log.info("缓存命中: matchId={}, resumeId={}, jobId={}", cached.getId(), resumeId, jobId);
            return buildVOFromRecord(cached);
        }

        // 2. 选择最优文本 (优先 cleaned_text, 降级 raw_text)
        String resumeText = pickText(resume.getCleanedText(), resume.getRawText());
        String jobText = pickText(job.getCleanedText(), job.getRawText());

        // ==== Layer 4: Token Guard — 估算 + 截断 ====
        resumeText = truncateIfNeeded(resumeText, "简历");
        jobText = truncateIfNeeded(jobText, "JD");

        // 3. 构建 Prompt (含结构化摘要)
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(resume, job, resumeText, jobText);

        int estimatedTokens = (systemPrompt.length() + userPrompt.length()) / 3;
        log.info("LLM调用 — 估算input tokens: {}, resume chars: {}, job chars: {}",
                estimatedTokens, resumeText.length(), jobText.length());

        // 4. 调用 LLM
        String llmResponse = deepSeekClient.chat(systemPrompt, userPrompt);

        // 5. 解析响应
        JsonNode json = extractJson(llmResponse);
        int matchScore = json.path("matchScore").asInt(0);
        boolean isMatched = json.path("isMatched").asBoolean(false);
        List<String> strengths  = parseStringList(json.path("strengths"));
        List<String> gaps       = parseStringList(json.path("gaps"));
        List<String> suggestions = parseStringList(json.path("suggestions"));

        // 6. 入库
        MatchRecord record = new MatchRecord();
        record.setResumeId(resumeId);
        record.setJobId(jobId);
        record.setMatchScore(matchScore);
        record.setIsMatched(isMatched ? 1 : 0);
        record.setStrengths(toJson(strengths));
        record.setGaps(toJson(gaps));
        record.setSuggestions(toJson(suggestions));
        record.setRawResponse(llmResponse);
        record.setStatus("COMPLETED");

        matchRecordMapper.insert(record);

        MatchRecord saved = matchRecordMapper.selectById(record.getId());
        return buildVOFromRecord(saved);
    }

    // ====== Layer 3: 缓存 ======

    private MatchRecord findCachedResult(Long resumeId, Long jobId) {
        return matchRecordMapper.selectOne(
                new LambdaQueryWrapper<MatchRecord>()
                        .eq(MatchRecord::getResumeId, resumeId)
                        .eq(MatchRecord::getJobId, jobId)
                        .eq(MatchRecord::getStatus, "COMPLETED")
                        .orderByDesc(MatchRecord::getCreateTime)
                        .last("LIMIT 1"));
    }

    /** 若 resume/job 未更新过，缓存有效 */
    private boolean isCacheValid(MatchRecord cached, Resume resume, Job job) {
        if (cached.getCreateTime() == null) return false;
        if (resume.getUpdateTime() != null && resume.getUpdateTime().isAfter(cached.getCreateTime())) {
            log.info("缓存失效: resume已更新, updateTime={}", resume.getUpdateTime());
            return false;
        }
        if (job.getUpdateTime() != null && job.getUpdateTime().isAfter(cached.getCreateTime())) {
            log.info("缓存失效: job已更新, updateTime={}", job.getUpdateTime());
            return false;
        }
        return true;
    }

    // ====== Layer 4: Token Guard ======

    /** 优先使用清洗文本，降级到原始文本 */
    private String pickText(String cleaned, String raw) {
        return (cleaned != null && !cleaned.isBlank()) ? cleaned : raw;
    }

    /** 文本超过阈值时截断: 保留前 70% + 后 30% */
    private String truncateIfNeeded(String text, String label) {
        if (text == null || text.length() <= maxInputChars) return text;

        int keep = (int) (maxInputChars * 0.7);
        int tail = maxInputChars - keep;
        String truncated = text.substring(0, keep)
                + "\n\n...(" + label + "中间内容已省略)...\n\n"
                + text.substring(text.length() - tail);

        log.warn("{} 文本过长已截断: {} chars → {} chars (省约 {} tokens)",
                label, text.length(), truncated.length(),
                (text.length() - truncated.length()) / 3);
        return truncated;
    }

    // ====== Prompt 构建 ======

    private String buildSystemPrompt() {
        return """
                你是一位资深 HR 和职业规划专家，擅长评估候选人与岗位的匹配度。
                请严格按以下 JSON 格式输出分析结果，不要输出任何其他内容：

                {
                  "matchScore": 数字(0-100),
                  "isMatched": true或false(得分>=60建议为true),
                  "strengths": ["优势1", "优势2"],
                  "gaps": ["差距1", "差距2"],
                  "suggestions": ["建议1", "建议2"]
                }

                评估维度：
                - 技能匹配：技术栈、工具、框架的覆盖程度
                - 经验匹配：工作年限、行业背景、项目经验
                - 教育背景：学历、专业相关性
                - 综合素质：软技能、管理经验等
                """;
    }

    private String buildUserPrompt(Resume resume, Job job, String resumeText, String jobText) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下候选人与岗位的匹配度：\n\n");

        // 简历结构化摘要
        sb.append("=== 简历摘要 ===\n");
        appendField(sb, "技能", resume.getSkills());
        appendField(sb, "经验", resume.getExperienceYears());
        appendField(sb, "学历", resume.getEducation());
        sb.append("\n");

        // 岗位结构化摘要
        sb.append("=== 岗位摘要 ===\n");
        sb.append("职位：").append(job.getTitle()).append("\n");
        if (job.getCompany() != null && !job.getCompany().isBlank()) {
            sb.append("公司：").append(job.getCompany()).append("\n");
        }
        appendField(sb, "要求技能", job.getSkills());
        appendField(sb, "经验要求", job.getExperienceRequired());
        sb.append("\n");

        // 简历详情
        sb.append("=== 简历详情 ===\n");
        sb.append(resumeText != null ? resumeText : "(无)").append("\n\n");

        // JD详情
        sb.append("=== JD详情 ===\n");
        sb.append(jobText != null ? jobText : "(无)").append("\n");

        return sb.toString();
    }

    private void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append("：").append(value).append("\n");
        }
    }

    // ====== JSON 解析 ======

    private JsonNode extractJson(String llmResponse) {
        try {
            String jsonStr = llmResponse.trim();
            if (jsonStr.startsWith("```")) {
                int start = jsonStr.indexOf("\n");
                int end = jsonStr.lastIndexOf("```");
                if (start > 0 && end > start) {
                    jsonStr = jsonStr.substring(start, end).trim();
                }
            }
            return objectMapper.readTree(jsonStr);
        } catch (JsonProcessingException e) {
            log.error("LLM响应JSON解析失败: {}", llmResponse, e);
            throw new BusinessException(ErrorCode.LLM_PARSE_ERROR);
        }
    }

    private List<String> parseStringList(JsonNode node) {
        try {
            return objectMapper.readValue(node.toString(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    // ====== VO 构建 ======

    private MatchResultVO buildVOFromRecord(MatchRecord r) {
        return MatchResultVO.builder()
                .id(r.getId())
                .resumeId(r.getResumeId())
                .jobId(r.getJobId())
                .matchScore(r.getMatchScore())
                .isMatched(r.getIsMatched() == 1)
                .strengths(parseJsonList(r.getStrengths()))
                .gaps(parseJsonList(r.getGaps()))
                .suggestions(parseJsonList(r.getSuggestions()))
                .createTime(r.getCreateTime())
                .build();
    }

    private List<String> parseJsonList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

}
