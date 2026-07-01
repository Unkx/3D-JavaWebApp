package com.printplatform.service;

import com.printplatform.dto.SlicerAdviceRequest;
import com.printplatform.dto.SlicerAdviceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlicerAdviceServiceTest {

    @InjectMocks
    private SlicerAdviceService slicerAdviceService;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    void injectRestTemplate() {
        ReflectionTestUtils.setField(slicerAdviceService, "restTemplate", restTemplate);
    }

    private SlicerAdviceRequest requestFor(String description, String material, String size, String quality) {
        SlicerAdviceRequest request = new SlicerAdviceRequest();
        request.setDescription(description);
        request.setMaterial(material);
        request.setSize(size);
        request.setQuality(quality);
        return request;
    }

    @Test
    void getAdvice_blankApiKey_usesRuleBasedFallbackAndNeverCallsRestTemplate() {
        ReflectionTestUtils.setField(slicerAdviceService, "apiKey", "");

        SlicerAdviceResponse response = slicerAdviceService.getAdvice(requestFor("prosty przedmiot codziennego użytku", null, "medium", "normal"));

        assertThat(response.isAiGenerated()).isFalse();
        assertThat(response.getRecommendedMaterial()).isEqualTo("PLA");
        assertThat(response.getNozzleTemp()).isEqualTo(210);
        assertThat(response.getBedTemp()).isEqualTo(60);
        assertThat(response.getLayerHeight()).isEqualTo("0.2mm");
        assertThat(response.getInfillPercent()).isEqualTo(20);
        assertThat(response.getInfillPattern()).isEqualTo("grid");
        assertThat(response.isSupportsNeeded()).isFalse();
        assertThat(response.getSupportType()).isEqualTo("none");
        assertThat(response.getPrintSpeed()).isEqualTo("50mm/s");
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getAdvice_nullApiKey_usesRuleBasedFallback() {
        ReflectionTestUtils.setField(slicerAdviceService, "apiKey", null);

        SlicerAdviceResponse response = slicerAdviceService.getAdvice(requestFor("opis", null, null, null));

        assertThat(response.isAiGenerated()).isFalse();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getRuleBasedAdvice_detectsFlexibleMaterialAsTpuAndSlowsPrintSpeed() {
        ReflectionTestUtils.setField(slicerAdviceService, "apiKey", "");

        SlicerAdviceResponse response = slicerAdviceService.getAdvice(
                requestFor("potrzebuję giętkiej elastycznej podkładki", null, "medium", "normal"));

        assertThat(response.getRecommendedMaterial()).isEqualTo("TPU");
        assertThat(response.getNozzleTemp()).isEqualTo(225);
        assertThat(response.getBedTemp()).isEqualTo(50);
        assertThat(response.getPrintSpeed()).isEqualTo("25mm/s");
        assertThat(response.getWarnings()).anyMatch(w -> w.contains("direct drive"));
    }

    @Test
    void getRuleBasedAdvice_absMaterialAddsEnclosureWarning() {
        ReflectionTestUtils.setField(slicerAdviceService, "apiKey", "");

        SlicerAdviceResponse response = slicerAdviceService.getAdvice(
                requestFor("część odporna na wysoką temperaturę", "ABS", "medium", "normal"));

        assertThat(response.getRecommendedMaterial()).isEqualTo("ABS");
        assertThat(response.getWarnings()).anyMatch(w -> w.contains("zamkniętej komory"));
    }

    @Test
    void getRuleBasedAdvice_functionalPartSmallSize_setsHigherCubicInfill() {
        ReflectionTestUtils.setField(slicerAdviceService, "apiKey", "");

        SlicerAdviceResponse response = slicerAdviceService.getAdvice(
                requestFor("mechaniczny uchwyt narzędziowy", "PLA", "small", "normal"));

        assertThat(response.getInfillPercent()).isEqualTo(60);
        assertThat(response.getInfillPattern()).isEqualTo("cubic");
    }

    @Test
    void getRuleBasedAdvice_functionalPartLargeSize_setsFortyPercentCubicInfill() {
        ReflectionTestUtils.setField(slicerAdviceService, "apiKey", "");

        SlicerAdviceResponse response = slicerAdviceService.getAdvice(
                requestFor("wytrzymały bracket mocujący", "PLA", "large", "normal"));

        assertThat(response.getInfillPercent()).isEqualTo(40);
        assertThat(response.getInfillPattern()).isEqualTo("cubic");
        // large size also triggers the bed-adhesion warning
        assertThat(response.getWarnings()).anyMatch(w -> w.toLowerCase().contains("odklejanie"));
    }

    @Test
    void getRuleBasedAdvice_decorativeFigurine_needsSupportsAndTreeSupportType() {
        ReflectionTestUtils.setField(slicerAdviceService, "apiKey", "");

        SlicerAdviceResponse response = slicerAdviceService.getAdvice(
                requestFor("figurka dekoracyjna smoka", "PLA", "medium", "normal"));

        assertThat(response.getInfillPercent()).isEqualTo(15);
        assertThat(response.getInfillPattern()).isEqualTo("gyroid");
        assertThat(response.isSupportsNeeded()).isTrue();
        assertThat(response.getSupportType()).isEqualTo("tree");
        assertThat(response.getTips()).anyMatch(t -> t.contains("ironing"));
        assertThat(response.getTips()).anyMatch(t -> t.contains("Supporty drzewkowe"));
    }

    @Test
    void getRuleBasedAdvice_qualityFastAndUltra_changeLayerHeightAndSpeed() {
        ReflectionTestUtils.setField(slicerAdviceService, "apiKey", "");

        SlicerAdviceResponse fast = slicerAdviceService.getAdvice(requestFor("model", "PLA", "medium", "fast"));
        assertThat(fast.getLayerHeight()).isEqualTo("0.3mm");
        assertThat(fast.getPrintSpeed()).isEqualTo("80mm/s");

        SlicerAdviceResponse ultra = slicerAdviceService.getAdvice(requestFor("model", "PLA", "medium", "ultra"));
        assertThat(ultra.getLayerHeight()).isEqualTo("0.1mm");
        assertThat(ultra.getPrintSpeed()).isEqualTo("30mm/s");
        assertThat(ultra.getTips()).anyMatch(t -> t.contains("dyszkę 0.3mm"));
    }

    @Test
    void getAdvice_nonBlankApiKey_callsRestTemplateForClaudeProvider() {
        ReflectionTestUtils.setField(slicerAdviceService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(slicerAdviceService, "provider", "claude");

        String aiJson = "{\"content\":[{\"text\":\"{\\\"recommendedMaterial\\\":\\\"PETG\\\","
                + "\\\"materialReason\\\":\\\"ok\\\",\\\"nozzleTemp\\\":235,\\\"bedTemp\\\":80,"
                + "\\\"layerHeight\\\":\\\"0.2mm\\\",\\\"infillPercent\\\":20,\\\"infillPattern\\\":\\\"grid\\\","
                + "\\\"supportsNeeded\\\":false,\\\"supportType\\\":\\\"none\\\",\\\"printSpeed\\\":\\\"50mm/s\\\","
                + "\\\"warnings\\\":[],\\\"tips\\\":[]}\"}]}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiJson, HttpStatus.OK));

        SlicerAdviceResponse response = slicerAdviceService.getAdvice(requestFor("opis", "PLA", "medium", "normal"));

        assertThat(response.isAiGenerated()).isTrue();
        assertThat(response.getRecommendedMaterial()).isEqualTo("PETG");
        verify(restTemplate).exchange(eq("https://api.anthropic.com/v1/messages"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class));
    }

    @Test
    void getAdvice_nonBlankApiKey_restTemplateThrows_fallsBackToRuleBased() {
        ReflectionTestUtils.setField(slicerAdviceService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(slicerAdviceService, "provider", "claude");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("network down"));

        SlicerAdviceResponse response = slicerAdviceService.getAdvice(requestFor("opis", "PLA", "medium", "normal"));

        assertThat(response.isAiGenerated()).isFalse();
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void getAdvice_groqProvider_usesGroqUrl() {
        ReflectionTestUtils.setField(slicerAdviceService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(slicerAdviceService, "provider", "groq");

        String aiJson = "{\"choices\":[{\"message\":{\"content\":"
                + "\"{\\\"recommendedMaterial\\\":\\\"PLA\\\",\\\"materialReason\\\":\\\"ok\\\","
                + "\\\"nozzleTemp\\\":210,\\\"bedTemp\\\":60,\\\"layerHeight\\\":\\\"0.2mm\\\","
                + "\\\"infillPercent\\\":20,\\\"infillPattern\\\":\\\"grid\\\",\\\"supportsNeeded\\\":false,"
                + "\\\"supportType\\\":\\\"none\\\",\\\"printSpeed\\\":\\\"50mm/s\\\",\\\"warnings\\\":[],"
                + "\\\"tips\\\":[]}\"}}]}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiJson, HttpStatus.OK));

        SlicerAdviceResponse response = slicerAdviceService.getAdvice(requestFor("opis", "PLA", "medium", "normal"));

        assertThat(response.isAiGenerated()).isTrue();
        verify(restTemplate).exchange(eq("https://api.groq.com/openai/v1/chat/completions"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class));
    }
}
