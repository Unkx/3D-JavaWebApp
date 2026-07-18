package com.printplatform.dto;

public class UpdatePrivacyRequest {
    private boolean showCity;
    private boolean showRealName;

    public boolean isShowCity()      { return showCity; }
    public void setShowCity(boolean showCity) { this.showCity = showCity; }

    public boolean isShowRealName()  { return showRealName; }
    public void setShowRealName(boolean showRealName) { this.showRealName = showRealName; }
}
