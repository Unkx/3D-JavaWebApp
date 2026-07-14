package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.PageViewRequest;
import com.printplatform.repository.PageViewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AnalyticsControllerTest extends AbstractControllerTest {

    @Autowired
    private PageViewRepository pageViewRepository;

    @Test
    void trackPageView_anonymous_recordsRow() throws Exception {
        PageViewRequest request = new PageViewRequest();
        request.setPath("/zlecenia");
        request.setSessionId("session-123");

        mockMvc.perform(post("/api/analytics/pageview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        assertThat(pageViewRepository.findAll()).hasSize(1);
        assertThat(pageViewRepository.findAll().get(0).getPath()).isEqualTo("/zlecenia");
        assertThat(pageViewRepository.findAll().get(0).getSessionId()).isEqualTo("session-123");
    }

    @Test
    void trackPageView_blankPath_returns400() throws Exception {
        PageViewRequest request = new PageViewRequest();
        request.setPath("");
        request.setSessionId("session-123");

        mockMvc.perform(post("/api/analytics/pageview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
