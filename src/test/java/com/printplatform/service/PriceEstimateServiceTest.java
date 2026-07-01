package com.printplatform.service;

import com.printplatform.dto.PriceEstimateRequest;
import com.printplatform.dto.PriceEstimateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceEstimateServiceTest {

    @InjectMocks
    private PriceEstimateService priceEstimateService;

    @Mock
    private RestTemplate restTemplate;

    private PriceEstimateRequest requestFor(String material, String size, String quality) {
        PriceEstimateRequest request = new PriceEstimateRequest();
        request.setDescription("Figurka smoka");
        request.setMaterial(material);
        request.setSize(size);
        request.setQuality(quality);
        return request;
    }

    @BeforeEach
    void injectRestTemplate() {
        // production field is a private final `new RestTemplate()`; swap it out so we can verify/mock HTTP calls
        ReflectionTestUtils.setField(priceEstimateService, "restTemplate", restTemplate);
    }

    @Test
    void getEstimate_blankApiKey_usesRuleBasedFallbackAndNeverCallsRestTemplate() {
        ReflectionTestUtils.setField(priceEstimateService, "apiKey", "");

        PriceEstimateResponse response = priceEstimateService.getEstimate(requestFor("PLA", "medium", "normal"));

        assertThat(response.isAiGenerated()).isFalse();
        assertThat(response.getAssumedWeightGrams()).isEqualTo(150);
        assertThat(response.getAssumedPrintHours()).isEqualTo(4.0);
        assertThat(response.getPriceLow()).isGreaterThan(0);
        assertThat(response.getPriceHigh()).isGreaterThanOrEqualTo(response.getPriceLow());
        assertThat(response.getWarnings()).isNotNull().isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getEstimate_nullApiKey_usesRuleBasedFallback() {
        ReflectionTestUtils.setField(priceEstimateService, "apiKey", null);

        PriceEstimateResponse response = priceEstimateService.getEstimate(requestFor(null, null, null));

        assertThat(response.isAiGenerated()).isFalse();
        // defaults: medium size, normal quality, PLA material
        assertThat(response.getAssumedWeightGrams()).isEqualTo(150);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getRuleBasedEstimate_smallSizeFastQuality_computesExpectedWeightAndHours() {
        ReflectionTestUtils.setField(priceEstimateService, "apiKey", "");

        PriceEstimateResponse response = priceEstimateService.getEstimate(requestFor("PETG", "small", "fast"));

        assertThat(response.getAssumedWeightGrams()).isEqualTo(50);
        // hours = 1.5 * 0.7 (fast time scale) = 1.0499999999999998 due to double precision,
        // rounds to 1.0 (not 1.1) via Math.round(hours * 10) / 10.0
        assertThat(response.getAssumedPrintHours()).isEqualTo(1.0);
    }

    @Test
    void getEstimate_nonBlankApiKey_callsRestTemplateForClaudeProvider() {
        ReflectionTestUtils.setField(priceEstimateService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(priceEstimateService, "provider", "claude");

        String aiJson = "{\"content\":[{\"text\":\"{\\\"priceLow\\\":40,\\\"priceHigh\\\":60,"
                + "\\\"reasoning\\\":\\\"ok\\\",\\\"assumedWeightGrams\\\":120,"
                + "\\\"assumedPrintHours\\\":3.0,\\\"warnings\\\":[]}\"}]}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiJson, org.springframework.http.HttpStatus.OK));

        PriceEstimateResponse response = priceEstimateService.getEstimate(requestFor("PLA", "medium", "normal"));

        assertThat(response.isAiGenerated()).isTrue();
        assertThat(response.getPriceLow()).isEqualTo(40);
        assertThat(response.getPriceHigh()).isEqualTo(60);
        verify(restTemplate).exchange(eq("https://api.anthropic.com/v1/messages"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class));
    }

    @Test
    void getEstimate_nonBlankApiKey_restTemplateThrows_fallsBackToRuleBased() {
        ReflectionTestUtils.setField(priceEstimateService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(priceEstimateService, "provider", "claude");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("network down"));

        PriceEstimateResponse response = priceEstimateService.getEstimate(requestFor("PLA", "medium", "normal"));

        assertThat(response.isAiGenerated()).isFalse();
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void getEstimate_openAiProvider_usesOpenAiUrlAndBearerAuth() {
        ReflectionTestUtils.setField(priceEstimateService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(priceEstimateService, "provider", "openai");

        String aiJson = "{\"choices\":[{\"message\":{\"content\":"
                + "\"{\\\"priceLow\\\":10,\\\"priceHigh\\\":20,\\\"reasoning\\\":\\\"ok\\\","
                + "\\\"assumedWeightGrams\\\":100,\\\"assumedPrintHours\\\":2.0,\\\"warnings\\\":[]}\"}}]}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(aiJson, org.springframework.http.HttpStatus.OK));

        PriceEstimateResponse response = priceEstimateService.getEstimate(requestFor("PLA", "medium", "normal"));

        assertThat(response.isAiGenerated()).isTrue();
        verify(restTemplate).exchange(eq("https://api.openai.com/v1/chat/completions"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class));
    }
}
