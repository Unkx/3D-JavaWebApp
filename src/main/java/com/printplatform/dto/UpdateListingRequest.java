package com.printplatform.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class UpdateListingRequest {

    @Size(max = 2000, message = "Opis może mieć maksymalnie 2000 znaków")
    private String description;

    @Size(max = 50, message = "Nazwa materiału jest zbyt długa")
    private String requiredMaterial;

    @Positive(message = "Budżet musi być większy od zera")
    private BigDecimal maxBudget;

    @Size(max = 20)
    private String estimatorSize;

    @Size(max = 20)
    private String estimatorQuality;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRequiredMaterial() { return requiredMaterial; }
    public void setRequiredMaterial(String requiredMaterial) { this.requiredMaterial = requiredMaterial; }

    public BigDecimal getMaxBudget() { return maxBudget; }
    public void setMaxBudget(BigDecimal maxBudget) { this.maxBudget = maxBudget; }

    public String getEstimatorSize() { return estimatorSize; }
    public void setEstimatorSize(String s) { this.estimatorSize = s; }

    public String getEstimatorQuality() { return estimatorQuality; }
    public void setEstimatorQuality(String estimatorQuality) { this.estimatorQuality = estimatorQuality; }
}
