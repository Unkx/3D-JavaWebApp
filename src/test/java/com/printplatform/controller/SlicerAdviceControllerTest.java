package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.SlicerAdviceRequest;
import com.printplatform.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/ai/slicer-advice is likewise not in the permitAll list, so it requires authentication.
 * ai.api.key is blank in the test properties, so SlicerAdviceService always falls back to the
 * rule-based advice (no outbound HTTP call is made).
 */
@Transactional
class SlicerAdviceControllerTest extends AbstractControllerTest {

    @Test
    void getSlicerAdvice_authenticated_returns200RuleBasedAdvice() throws Exception {
        User user = persistUser();

        SlicerAdviceRequest request = new SlicerAdviceRequest();
        request.setDescription("A functional mounting bracket");
        request.setMaterial("PETG");
        request.setSize("medium");
        request.setQuality("normal");

        mockMvc.perform(post("/api/ai/slicer-advice")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiGenerated").value(false))
                .andExpect(jsonPath("$.recommendedMaterial").value("PETG"));
    }

    @Test
    void getSlicerAdvice_noAuth_returns403() throws Exception {
        SlicerAdviceRequest request = new SlicerAdviceRequest();
        request.setDescription("A functional mounting bracket");

        mockMvc.perform(post("/api/ai/slicer-advice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSlicerAdvice_blankDescription_returns400() throws Exception {
        User user = persistUser();

        SlicerAdviceRequest request = new SlicerAdviceRequest();
        request.setDescription(""); // @NotBlank violation

        mockMvc.perform(post("/api/ai/slicer-advice")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
