package com.printplatform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @Email(message = "Podaj poprawny adres email")
    @NotBlank(message = "Email jest wymagany")
    private String email;

    @NotBlank(message = "Hasło jest wymagane")
    @Size(min = 8, message = "Hasło musi mieć co najmniej 8 znaków")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "Hasło musi zawierać wielką literę, małą literę i cyfrę"
    )
    private String password;

    /** Optional: if a valid admin code is supplied, the new account becomes an administrator. */
    private String adminCode;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getAdminCode() { return adminCode; }
    public void setAdminCode(String adminCode) { this.adminCode = adminCode; }
}
