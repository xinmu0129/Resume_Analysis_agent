package com.resume.agent.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.agent.common.BusinessException;
import com.resume.agent.common.ErrorCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * DeepSeek / OpenAI 兼容 API 客户端
 */
@Slf4j
@Component
public class DeepSeekClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public DeepSeekClient(
            ObjectMapper objectMapper,
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.model}") String model) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);  // 10s
        factory.setReadTimeout(120_000);    // 120s
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 发送 chat 请求，返回 LLM 回复文本
     */
    public String chat(String systemPrompt, String userPrompt) {
        log.info("LLM 请求发送中, model={}", model);

        ChatRequest request = new ChatRequest();
        request.setModel(model);
        request.setMessages(List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
        ));
        request.setTemperature(0.3);
        request.setMaxTokens(4096);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            String url = baseUrl + "/v1/chat/completions";
            HttpEntity<ChatRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText();

            log.info("LLM 响应成功, 长度={}", content.length());
            return content;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            throw new BusinessException(ErrorCode.LLM_ERROR);
        }
    }

    // ---------- DTO ----------

    @Data
    private static class ChatRequest {
        private String model;
        private List<Message> messages;
        private double temperature;
        @JsonProperty("max_tokens")
        private int maxTokens;
    }

    @Data
    private static class Message {
        private String role;
        private String content;

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
