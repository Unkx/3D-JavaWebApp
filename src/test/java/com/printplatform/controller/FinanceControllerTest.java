package com.printplatform.controller;

import com.printplatform.controller.support.AbstractControllerTest;
import com.printplatform.dto.CostSettingsRequest;
import com.printplatform.dto.RecurringCostRequest;
import com.printplatform.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class FinanceControllerTest extends AbstractControllerTest {

    private RecurringCostRequest recurringCostRequest(String name, String amount) {
        RecurringCostRequest request = new RecurringCostRequest();
        request.setName(name);
        request.setMonthlyAmount(new BigDecimal(amount));
        return request;
    }

    @Test
    void getSummary_authenticatedSeller_returns200WithKpisAndTwelveMonths() throws Exception {
        User seller = persistUser();

        mockMvc.perform(get("/api/finance/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReleased").exists())
                .andExpect(jsonPath("$.totalHeld").exists())
                .andExpect(jsonPath("$.monthProfit").exists())
                .andExpect(jsonPath("$.monthCosts").exists())
                .andExpect(jsonPath("$.months").isArray())
                .andExpect(jsonPath("$.months.length()").value(12));
    }

    @Test
    void getPipeline_authenticatedSeller_returnsSevenEntriesInOrder() throws Exception {
        User seller = persistUser();

        mockMvc.perform(get("/api/finance/pipeline")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1].status").value("SELECTED"))
                .andExpect(jsonPath("$[2].status").value("PAID"))
                .andExpect(jsonPath("$[3].status").value("PRINTING"))
                .andExpect(jsonPath("$[4].status").value("SHIPPED"))
                .andExpect(jsonPath("$[5].status").value("DELIVERED"))
                .andExpect(jsonPath("$[6].status").value("REJECTED"));
    }

    @Test
    void getAlerts_authenticatedSeller_returns200List() throws Exception {
        User seller = persistUser();

        mockMvc.perform(get("/api/finance/alerts")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createCost_blankName_returns400() throws Exception {
        User seller = persistUser();
        RecurringCostRequest request = recurringCostRequest("", "20.00");

        mockMvc.perform(post("/api/finance/costs")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCost_valid_returns201WithBody() throws Exception {
        User seller = persistUser();
        RecurringCostRequest request = recurringCostRequest("Filament storage", "25.50");

        mockMvc.perform(post("/api/finance/costs")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Filament storage"))
                .andExpect(jsonPath("$.monthlyAmount").value(25.50));
    }

    @Test
    void updateCost_ownedCost_passesIdAndPrincipalToService() throws Exception {
        User seller = persistUser();
        RecurringCostRequest createRequest = recurringCostRequest("Electricity", "40.00");
        String createResponse = mockMvc.perform(post("/api/finance/costs")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID costId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        RecurringCostRequest updateRequest = recurringCostRequest("Electricity updated", "45.00");

        mockMvc.perform(put("/api/finance/costs/" + costId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(costId.toString()))
                .andExpect(jsonPath("$.name").value("Electricity updated"))
                .andExpect(jsonPath("$.monthlyAmount").value(45.00));
    }

    @Test
    void deleteCost_ownedCost_returns204() throws Exception {
        User seller = persistUser();
        RecurringCostRequest createRequest = recurringCostRequest("Rent", "100.00");
        String createResponse = mockMvc.perform(post("/api/finance/costs")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID costId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(delete("/api/finance/costs/" + costId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
                .andExpect(status().isNoContent());
    }

    @Test
    void getSettings_authenticatedSeller_returns200AndCreatesIfMissing() throws Exception {
        User seller = persistUser();

        mockMvc.perform(get("/api/finance/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filamentPricePerKg").exists())
                .andExpect(jsonPath("$.costPerPrintHour").exists());
    }

    @Test
    void updateSettings_zeroValue_returns400() throws Exception {
        User seller = persistUser();
        CostSettingsRequest request = new CostSettingsRequest();
        request.setFilamentPricePerKg(BigDecimal.ZERO);
        request.setCostPerPrintHour(BigDecimal.valueOf(1.5));

        mockMvc.perform(put("/api/finance/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allEndpoints_noAuth_return403() throws Exception {
        mockMvc.perform(get("/api/finance/summary"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/finance/pipeline"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/finance/alerts"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/finance/costs"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/finance/costs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recurringCostRequest("Test", "10.00"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/finance/costs/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recurringCostRequest("Test", "10.00"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/finance/costs/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/finance/settings"))
                .andExpect(status().isForbidden());

        CostSettingsRequest settingsRequest = new CostSettingsRequest();
        settingsRequest.setFilamentPricePerKg(BigDecimal.valueOf(120));
        settingsRequest.setCostPerPrintHour(BigDecimal.valueOf(1.5));
        mockMvc.perform(put("/api/finance/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settingsRequest)))
                .andExpect(status().isForbidden());
    }
}
