package com.printplatform.dto;

import java.util.List;

public class PriceEstimateResponse {

    private int priceLow;
    private int priceHigh;
    private String reasoning;
    private int assumedWeightGrams;
    private double assumedPrintHours;
    private List<String> warnings;
    private boolean aiGenerated;

    public int getPriceLow() { return priceLow; }
    public void setPriceLow(int v) { this.priceLow = v; }
    public int getPriceHigh() { return priceHigh; }
    public void setPriceHigh(int v) { this.priceHigh = v; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String v) { this.reasoning = v; }
    public int getAssumedWeightGrams() { return assumedWeightGrams; }
    public void setAssumedWeightGrams(int v) { this.assumedWeightGrams = v; }
    public double getAssumedPrintHours() { return assumedPrintHours; }
    public void setAssumedPrintHours(double v) { this.assumedPrintHours = v; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> v) { this.warnings = v; }
    public boolean isAiGenerated() { return aiGenerated; }
    public void setAiGenerated(boolean v) { this.aiGenerated = v; }
}
