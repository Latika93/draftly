package com.assignment.draftly.integrations;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.assignment.draftly.dto.OpenAiChatRequest;
import com.assignment.draftly.dto.OpenAiChatResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiClient {

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    public String generate(String systemPrompt, String userPrompt) {

        OpenAiChatRequest request = new OpenAiChatRequest(
                model,
                List.of(
                        new OpenAiChatRequest.Message("system", systemPrompt),
                        new OpenAiChatRequest.Message("user", userPrompt)
                )
        );

        if (apiKey == null || apiKey.isEmpty()) {
            log.error("OpenAI API key is null or empty!");
            throw new IllegalStateException("OpenAI API key is not configured");
        }

        if (!apiKey.startsWith("sk-")) {
            log.warn("OpenAI API key does not start with 'sk-'. This might be incorrect. Key starts with: {}", 
                    apiKey.length() > 5 ? apiKey.substring(0, 5) : apiKey);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String authHeader = headers.getFirst("Authorization");
        log.info("OpenAI API Key (first 10 chars): {}", apiKey.substring(0, Math.min(10, apiKey.length())));
        log.info("Authorization header value (first 30 chars): {}", 
                authHeader != null ? authHeader.substring(0, Math.min(30, authHeader.length())) : "NULL");
        
        if (authHeader != null && authHeader.contains("google")) {
            log.error("ERROR: Authorization header contains 'google'! This should be the OpenAI API key, not Google token!");
            throw new IllegalStateException("Authorization header incorrectly contains Google token instead of OpenAI API key");
        }
        log.info("Model: {}", model);
        log.info("Request URL: https://api.openai.com/v1/chat/completions");

        HttpEntity<OpenAiChatRequest> entity =
                new HttpEntity<>(request, headers);

        try {
            log.info("Making request to OpenAI API...");
            ResponseEntity<OpenAiChatResponse> response =
                    restTemplate.postForEntity(
                            "https://api.openai.com/v1/chat/completions",
                            entity,
                            OpenAiChatResponse.class
                    );

            log.info("OpenAI API response status: {}", response.getStatusCode());

            if (response.getBody() == null || response.getBody().getChoices() == null || response.getBody().getChoices().isEmpty()) {
                log.error("OpenAI API returned empty response");
                throw new RuntimeException("OpenAI API returned empty response");
            }

            return response
                    .getBody()
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();
        } catch (RestClientException e) {
            log.error("Error calling OpenAI API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate email draft: " + e.getMessage(), e);
        }
    }
}
