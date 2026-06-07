package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Client-supplied fields for creating a listing. Server controls id/user/status/files. */
public class CreateListingRequest {

    @NotBlank(message = "Tytuł jest wymagany")
    @Size(max = 100, message = "Tytuł może mieć maksymalnie 100 znaków")
    private String title;

    @Size(max = 2000, message = "Opis może mieć maksymalnie 2000 znaków")
    private String description;

    @NotBlank(message = "Materiał jest wymagany")
    @Size(max = 50, message = "Nazwa materiału jest zbyt długa")
    private String requiredMaterial;

    @Positive(message = "Budżet musi być większy od zera")
    private BigDecimal maxBudget; // optional

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getRequiredMaterial() { return requiredMaterial; }
    public void setRequiredMaterial(String requiredMaterial) { this.requiredMaterial = requiredMaterial; }

    public BigDecimal getMaxBudget() { return maxBudget; }
    public void setMaxBudget(BigDecimal maxBudget) { this.maxBudget = maxBudget; }
}
