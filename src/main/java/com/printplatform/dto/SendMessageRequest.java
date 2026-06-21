package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SendMessageRequest {
    @NotBlank(message = "Treść wiadomości jest wymagana")
    @Size(max = 2000, message = "Wiadomość może mieć maksymalnie 2000 znaków")
    private String content;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
