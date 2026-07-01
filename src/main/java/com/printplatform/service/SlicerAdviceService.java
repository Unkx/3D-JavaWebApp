package com.printplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printplatform.dto.SlicerAdviceRequest;
import com.printplatform.dto.SlicerAdviceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class SlicerAdviceService {

    private static final Logger log = LoggerFactory.getLogger(SlicerAdviceService.class);

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.api.provider:claude}")
    private String provider;

    @Value("${ai.grok.model:grok-4-latest}")
    private String grokModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SlicerAdviceResponse getAdvice(SlicerAdviceRequest request) {
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                return getAiAdvice(request);
            } catch (Exception e) {
                log.warn("AI advice failed, falling back to rules: {}", e.getMessage());
            }
        }
        return getRuleBasedAdvice(request);
    }

    private SlicerAdviceResponse getAiAdvice(SlicerAdviceRequest request) {
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

    private String buildPrompt(SlicerAdviceRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Jesteś ekspertem od druku 3D. Użytkownik opisuje co chce wydrukować. ");
        sb.append("Odpowiedz WYŁĄCZNIE poprawnym JSON (bez markdown, bez ```). ");
        sb.append("Format:\n");
        sb.append("{\"recommendedMaterial\":\"PLA\",\"materialReason\":\"...\",");
        sb.append("\"nozzleTemp\":210,\"bedTemp\":60,\"layerHeight\":\"0.2mm\",");
        sb.append("\"infillPercent\":20,\"infillPattern\":\"grid\",");
        sb.append("\"supportsNeeded\":false,\"supportType\":\"none\",");
        sb.append("\"printSpeed\":\"50mm/s\",");
        sb.append("\"warnings\":[\"...\"],\"tips\":[\"...\"]}\n\n");
        sb.append("Opis użytkownika: ").append(request.getDescription());
        if (request.getMaterial() != null && !request.getMaterial().isBlank()) {
            sb.append("\nPreferowany materiał: ").append(request.getMaterial());
        }
        if (request.getSize() != null && !request.getSize().isBlank()) {
            sb.append("\nWielkość: ").append(request.getSize());
        }
        if (request.getQuality() != null && !request.getQuality().isBlank()) {
            sb.append("\nJakość: ").append(request.getQuality());
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

    private SlicerAdviceResponse parseAiResponse(String content) {
        try {
            String json = content.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            SlicerAdviceResponse resp = objectMapper.readValue(json, SlicerAdviceResponse.class);
            resp.setAiGenerated(true);
            return resp;
        } catch (Exception e) {
            log.warn("Failed to parse AI JSON, using rule-based: {}", e.getMessage());
            return getRuleBasedAdvice(new SlicerAdviceRequest());
        }
    }

    // ── Rule-based fallback ─────────────────────────────────────────

    private SlicerAdviceResponse getRuleBasedAdvice(SlicerAdviceRequest request) {
        SlicerAdviceResponse r = new SlicerAdviceResponse();
        r.setAiGenerated(false);

        String desc = (request.getDescription() != null ? request.getDescription() : "").toLowerCase();
        String mat = (request.getMaterial() != null ? request.getMaterial() : "").toUpperCase();
        String size = (request.getSize() != null ? request.getSize() : "medium").toLowerCase();
        String quality = (request.getQuality() != null ? request.getQuality() : "normal").toLowerCase();

        // Material detection
        if (mat.isEmpty()) {
            mat = detectMaterial(desc);
        }
        r.setRecommendedMaterial(mat);
        r.setMaterialReason(materialReason(mat, desc));

        // Temperature
        Map<String, int[]> temps = Map.of(
                "PLA", new int[]{210, 60},
                "PETG", new int[]{235, 80},
                "ABS", new int[]{245, 100},
                "ASA", new int[]{250, 100},
                "TPU", new int[]{225, 50},
                "RESIN", new int[]{0, 0}
        );
        int[] t = temps.getOrDefault(mat, new int[]{210, 60});
        r.setNozzleTemp(t[0]);
        r.setBedTemp(t[1]);

        // Layer height by quality
        switch (quality) {
            case "fast" -> r.setLayerHeight("0.3mm");
            case "ultra" -> r.setLayerHeight("0.1mm");
            default -> r.setLayerHeight("0.2mm");
        }

        // Infill
        boolean functional = desc.matches(".*(mechanicz|wytrzym|mocn|funkcjon|narzędzi|uchwyt|mount|bracket|gear|zębat).*");
        boolean decorative = desc.matches(".*(ozdoba|figurka|wazon|bust|model|dekorac|rzeźb).*");

        if (functional) {
            r.setInfillPercent(size.equals("small") ? 60 : 40);
            r.setInfillPattern("cubic");
        } else if (decorative) {
            r.setInfillPercent(15);
            r.setInfillPattern("gyroid");
        } else {
            r.setInfillPercent(20);
            r.setInfillPattern("grid");
        }

        // Supports
        boolean needsSupports = desc.matches(".*(zwis|overhang|most|bridge|łuk|arch|wisząc|nawis).*")
                || desc.matches(".*(figurka|bust|posąg|rzeźb).*");
        r.setSupportsNeeded(needsSupports);
        r.setSupportType(needsSupports ? "tree" : "none");

        // Speed
        switch (quality) {
            case "fast" -> r.setPrintSpeed("80mm/s");
            case "ultra" -> r.setPrintSpeed("30mm/s");
            default -> r.setPrintSpeed("50mm/s");
        }
        if ("TPU".equals(mat)) r.setPrintSpeed("25mm/s");

        // Warnings
        List<String> warnings = new ArrayList<>();
        if ("ABS".equals(mat) || "ASA".equals(mat)) {
            warnings.add("Materiał wymaga zamkniętej komory druku — może się odklejać i pękać bez niej.");
        }
        if ("TPU".equals(mat)) {
            warnings.add("TPU wymaga bezpośredniego podajnika (direct drive). Bowden tube może powodować problemy.");
        }
        if (size.equals("large")) {
            warnings.add("Duże wydruki są podatne na odklejanie od stołu — użyj kleju lub lakieru do włosów.");
        }
        if (functional && r.getInfillPercent() < 30) {
            warnings.add("Dla części mechanicznych rozważ wyższy wypełnienie (40%+).");
        }
        r.setWarnings(warnings);

        // Tips
        List<String> tips = new ArrayList<>();
        tips.add("Pierwsza warstwa: zmniejsz prędkość do 20mm/s dla lepszej adhezji.");
        if (decorative) {
            tips.add("Dla lepszej jakości powierzchni włącz 'ironing' na górnych warstwach.");
        }
        if (needsSupports) {
            tips.add("Supporty drzewkowe (tree) łatwiej usunąć i zostawiają mniej śladów.");
        }
        if ("PETG".equals(mat)) {
            tips.add("PETG lubi się ciągnąć — włącz retrakcję (6mm, 40mm/s) i wyłącz 'combing'.");
        }
        if (quality.equals("ultra")) {
            tips.add("Przy warstwie 0.1mm rozważ dyszkę 0.3mm dla jeszcze lepszych detali.");
        }
        r.setTips(tips);

        return r;
    }

    private String detectMaterial(String desc) {
        if (desc.matches(".*(elast|gięt|flex|gumow|tpu).*")) return "TPU";
        if (desc.matches(".*(zewn|uv|słońc|pogod|asa).*")) return "ASA";
        if (desc.matches(".*(temp|gorąc|ciepło|abs|komor).*")) return "ABS";
        if (desc.matches(".*(wod|wilgo|chemicz|petg|żywnoś).*")) return "PETG";
        if (desc.matches(".*(żywic|resin|detail|miniatur|detal).*")) return "RESIN";
        return "PLA";
    }

    private String materialReason(String mat, String desc) {
        return switch (mat) {
            case "TPU" -> "Elastyczny materiał, idealny do giętkich elementów i amortyzujących części.";
            case "ASA" -> "Odporny na UV i warunki atmosferyczne — świetny do zastosowań zewnętrznych.";
            case "ABS" -> "Dobra odporność termiczna i mechaniczna, ale wymaga zamkniętej komory.";
            case "PETG" -> "Dobra odporność chemiczna i na wilgoć, bezpieczny w kontakcie z żywnością.";
            case "RESIN" -> "Najwyższe detale, idealny do miniatur i precyzyjnych modeli.";
            default -> "PLA to uniwersalny materiał — łatwy w druku, biodegradowalny, dobry na większość zastosowań.";
        };
    }
}
