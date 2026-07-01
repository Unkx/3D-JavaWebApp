package com.printplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printplatform.dto.PriceEstimateRequest;
import com.printplatform.dto.PriceEstimateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class PriceEstimateService {

    private static final Logger log = LoggerFactory.getLogger(PriceEstimateService.class);

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.api.provider:claude}")
    private String provider;

    @Value("${ai.grok.model:grok-4-latest}")
    private String grokModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PriceEstimateResponse getEstimate(PriceEstimateRequest request) {
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                return getAiEstimate(request);
            } catch (Exception e) {
                log.warn("AI price estimate failed, falling back to rules: {}", e.getMessage());
            }
        }
        return getRuleBasedEstimate(request);
    }

    private PriceEstimateResponse getAiEstimate(PriceEstimateRequest request) {
        String prompt = buildPrompt(request);

        String url;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body;

        if ("openai".equalsIgnoreCase(provider)) {
            url = "https://api.openai.com/v1/chat/completions";
            headers.setBearerAuth(apiKey);
            body = buildOpenAiBody(prompt);
        } else if ("grok".equalsIgnoreCase(provider)) {
            url = "https://api.x.ai/v1/chat/completions";
            headers.setBearerAuth(apiKey);
            body = buildGrokBody(prompt);
        } else {
            url = "https://api.anthropic.com/v1/messages";
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");
            body = buildClaudeBody(prompt);
        }

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        String content = extractContent(response.getBody());
        return parseAiResponse(content);
    }

    private String buildPrompt(PriceEstimateRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Jesteś ekspertem od wyceny druku 3D na zlecenie w Polsce. Użytkownik opisuje co chce wydrukować. ");
        sb.append("Wyceń w złotówkach (PLN) realny koszt wydruku u drukarza (materiał + robocizna + zużycie drukarki), ");
        sb.append("jako widełki priceLow-priceHigh. Odpowiedz WYŁĄCZNIE poprawnym JSON (bez markdown, bez ```). ");
        sb.append("Format:\n");
        sb.append("{\"priceLow\":45,\"priceHigh\":70,\"reasoning\":\"...\",");
        sb.append("\"assumedWeightGrams\":150,\"assumedPrintHours\":4.0,\"warnings\":[\"...\"]}\n\n");
        sb.append("Opis zlecenia: ").append(request.getDescription());
        if (request.getMaterial() != null && !request.getMaterial().isBlank()) {
            sb.append("\nMateriał: ").append(request.getMaterial());
        }
        if (request.getSize() != null && !request.getSize().isBlank()) {
            sb.append("\nWielkość obiektu: ").append(request.getSize());
        }
        if (request.getQuality() != null && !request.getQuality().isBlank()) {
            sb.append("\nJakość wykończenia: ").append(request.getQuality());
        }
        return sb.toString();
    }

    private String buildClaudeBody(String prompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", "claude-sonnet-4-20250514");
            body.put("max_tokens", 1024);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String buildOpenAiBody(String prompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", "gpt-4o-mini");
            body.put("max_tokens", 1024);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String buildGrokBody(String prompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", grokModel);
            body.put("max_tokens", 1024);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if ("openai".equalsIgnoreCase(provider) || "grok".equalsIgnoreCase(provider)) {
                return root.at("/choices/0/message/content").asText();
            }
            return root.at("/content/0/text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI response", e);
        }
    }

    private PriceEstimateResponse parseAiResponse(String content) {
        try {
            String json = content.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            PriceEstimateResponse resp = objectMapper.readValue(json, PriceEstimateResponse.class);
            resp.setAiGenerated(true);
            if (resp.getWarnings() == null) resp.setWarnings(new ArrayList<>());
            return resp;
        } catch (Exception e) {
            log.warn("Failed to parse AI JSON, using rule-based: {}", e.getMessage());
            return getRuleBasedEstimate(new PriceEstimateRequest());
        }
    }

    // ── Rule-based fallback (mirrors the old frontend `printEstimate` formula) ──

    private PriceEstimateResponse getRuleBasedEstimate(PriceEstimateRequest request) {
        String material = (request.getMaterial() != null && !request.getMaterial().isBlank()
                ? request.getMaterial() : "PLA").toUpperCase();
        String size = (request.getSize() != null && !request.getSize().isBlank()
                ? request.getSize() : "medium").toLowerCase();
        String quality = (request.getQuality() != null && !request.getQuality().isBlank()
                ? request.getQuality() : "normal").toLowerCase();

        Map<String, Integer> gramsBySize = Map.of("small", 50, "medium", 150, "large", 350);
        Map<String, Double> hoursBySize = Map.of("small", 1.5, "medium", 4.0, "large", 10.0);
        Map<String, Double> qualityTimeScale = Map.of("fast", 0.7, "normal", 1.0, "ultra", 1.4);
        Map<String, Double> qualityFilamentScale = Map.of("fast", 1.0, "normal", 1.0, "ultra", 1.1);
        Map<String, Double> pricePerGram = Map.of(
                "PLA", 0.05, "PETG", 0.07, "ABS", 0.06, "ASA", 0.08, "TPU", 0.12, "RESIN", 0.15
        );

        int grams = gramsBySize.getOrDefault(size, 150);
        double hours = hoursBySize.getOrDefault(size, 4.0);
        double timeScale = qualityTimeScale.getOrDefault(quality, 1.0);
        double filamentScale = qualityFilamentScale.getOrDefault(quality, 1.0);
        double pricePerGramValue = pricePerGram.getOrDefault(material, 0.06);

        double base = grams * filamentScale * pricePerGramValue + hours * timeScale * 5;

        PriceEstimateResponse resp = new PriceEstimateResponse();
        resp.setPriceLow(Math.max(1, (int) Math.round(base * 0.85)));
        resp.setPriceHigh((int) Math.round(base * 1.3));
        resp.setAssumedWeightGrams(grams);
        resp.setAssumedPrintHours(Math.round(hours * timeScale * 10) / 10.0);
        resp.setReasoning("Szacunek na podstawie typowej wagi i czasu druku dla wybranego rozmiaru i jakości wykończenia — bez analizy AI (brak klucza API lub błąd usługi).");
        resp.setWarnings(new ArrayList<>());
        resp.setAiGenerated(false);
        return resp;
    }
}
