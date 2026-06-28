package com.printplatform.dto;

import java.util.List;

public class SlicerAdviceResponse {

    private String recommendedMaterial;
    private String materialReason;
    private int nozzleTemp;
    private int bedTemp;
    private String layerHeight;
    private int infillPercent;
    private String infillPattern;
    private boolean supportsNeeded;
    private String supportType;
    private String printSpeed;
    private List<String> warnings;
    private List<String> tips;
    private boolean aiGenerated;

    public String getRecommendedMaterial() { return recommendedMaterial; }
    public void setRecommendedMaterial(String v) { this.recommendedMaterial = v; }
    public String getMaterialReason() { return materialReason; }
    public void setMaterialReason(String v) { this.materialReason = v; }
    public int getNozzleTemp() { return nozzleTemp; }
    public void setNozzleTemp(int v) { this.nozzleTemp = v; }
    public int getBedTemp() { return bedTemp; }
    public void setBedTemp(int v) { this.bedTemp = v; }
    public String getLayerHeight() { return layerHeight; }
    public void setLayerHeight(String v) { this.layerHeight = v; }
    public int getInfillPercent() { return infillPercent; }
    public void setInfillPercent(int v) { this.infillPercent = v; }
    public String getInfillPattern() { return infillPattern; }
    public void setInfillPattern(String v) { this.infillPattern = v; }
    public boolean isSupportsNeeded() { return supportsNeeded; }
    public void setSupportsNeeded(boolean v) { this.supportsNeeded = v; }
    public String getSupportType() { return supportType; }
    public void setSupportType(String v) { this.supportType = v; }
    public String getPrintSpeed() { return printSpeed; }
    public void setPrintSpeed(String v) { this.printSpeed = v; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> v) { this.warnings = v; }
    public List<String> getTips() { return tips; }
    public void setTips(List<String> v) { this.tips = v; }
    public boolean isAiGenerated() { return aiGenerated; }
    public void setAiGenerated(boolean v) { this.aiGenerated = v; }
}
