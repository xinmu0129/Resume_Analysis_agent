package com.resume.agent.service.impl;

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
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class MatchServiceImpl implements MatchService {

    private final ResumeMapper resumeMapper;
    private final JobMapper jobMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    public MatchServiceImpl(ResumeMapper resumeMapper,
                            JobMapper jobMapper,
                            MatchRecordMapper matchRecordMapper,
                            DeepSeekClient deepSeekClient,
                            ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.jobMapper = jobMapper;
        this.matchRecordMapper = matchRecordMapper;
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = objectMapper;
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

        // 2. 构建 Prompt
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(resume.getRawText(), job.getTitle(), job.getCompany(), job.getRawText());

        // 3. 调用 LLM
        String llmResponse = deepSeekClient.chat(systemPrompt, userPrompt);

        // 4. 解析响应
        JsonNode json = extractJson(llmResponse);

        int matchScore = json.path("matchScore").asInt(0);
        boolean isMatched = json.path("isMatched").asBoolean(false);

        List<String> strengths  = parseStringList(json.path("strengths"));
        List<String> gaps       = parseStringList(json.path("gaps"));
        List<String> suggestions = parseStringList(json.path("suggestions"));

        // 5. 存入 match_record
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

        return MatchResultVO.builder()
                .id(saved.getId())
                .resumeId(saved.getResumeId())
                .jobId(saved.getJobId())
                .matchScore(saved.getMatchScore())
                .isMatched(saved.getIsMatched() == 1)
                .strengths(strengths)
                .gaps(gaps)
                .suggestions(suggestions)
                .createTime(saved.getCreateTime())
                .build();
    }

    // ---------- Prompt ----------

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

    private String buildUserPrompt(String resumeText, String jobTitle, String company, String jobText) {
        String companyLine = company != null && !company.isBlank() ? "公司：" + company + "\n" : "";
        return String.format("""
                请分析以下候选人与岗位的匹配度：

                === 简历内容 ===
                %s

                === 岗位信息 ===
                职位：%s
                %s
                JD详情：
                %s
                """, resumeText, jobTitle, companyLine, jobText);
    }

    // ---------- JSON ----------

    /** 从 LLM 响应中提取 JSON，兼容 ```json 包裹 */
    private JsonNode extractJson(String llmResponse) {
        try {
            String jsonStr = llmResponse.trim();
            // 去除 markdown 代码块包裹
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

}
