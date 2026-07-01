package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.PriceEstimateRequest;
import com.printplatform.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/ai/price-estimate is NOT in the SecurityConfig permitAll list (only /api/auth/**, GET
 * /api/listings/**, GET /api/offers/listing/** and GET /api/offers/fee-breakdown are), so it
 * falls through to anyRequest().authenticated() and requires a valid bearer token.
 * ai.api.key is blank in the test properties, so PriceEstimateService always falls back to the
 * rule-based estimate (no outbound HTTP call is made).
 */
@Transactional
class PriceEstimateControllerTest extends AbstractControllerTest {

    @Test
    void getPriceEstimate_authenticated_returns200RuleBasedEstimate() throws Exception {
        User user = persistUser();

        PriceEstimateRequest request = new PriceEstimateRequest();
        request.setDescription("A small decorative vase");
        request.setMaterial("PLA");
        request.setSize("small");
        request.setQuality("normal");

        mockMvc.perform(post("/api/ai/price-estimate")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiGenerated").value(false))
                .andExpect(jsonPath("$.priceLow").isNumber())
                .andExpect(jsonPath("$.priceHigh").isNumber());
    }

    @Test
    void getPriceEstimate_noAuth_returns403() throws Exception {
        PriceEstimateRequest request = new PriceEstimateRequest();
        request.setDescription("A small decorative vase");

        mockMvc.perform(post("/api/ai/price-estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPriceEstimate_blankDescription_returns400() throws Exception {
        User user = persistUser();

        PriceEstimateRequest request = new PriceEstimateRequest();
        request.setDescription(""); // @NotBlank violation

        mockMvc.perform(post("/api/ai/price-estimate")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
