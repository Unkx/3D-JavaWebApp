package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PageViewRequest {
    @NotBlank
    @Size(max = 500)
    private String path;

    @NotBlank
    @Size(max = 100)
    private String sessionId;

    @Size(max = 500)
    private String referrer;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getReferrer() { return referrer; }
    public void setReferrer(String referrer) { this.referrer = referrer; }
}
