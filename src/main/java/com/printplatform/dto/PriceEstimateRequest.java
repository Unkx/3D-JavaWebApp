package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PriceEstimateRequest {

    @NotBlank(message = "Opis jest wymagany")
    @Size(max = 2000)
    private String description;

    private String material;
    private String size;
    private String quality;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getQuality() { return quality; }
    public void setQuality(String quality) { this.quality = quality; }
}
