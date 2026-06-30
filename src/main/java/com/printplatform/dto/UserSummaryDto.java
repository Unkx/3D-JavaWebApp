package com.printplatform.dto;

import com.printplatform.model.User;
import java.time.LocalDateTime;

public class UserSummaryDto {
    private String id;
    private String email;
    private String role;
    private String firstName;
    private String lastName;
    private LocalDateTime createdAt;

    public UserSummaryDto(User u) {
        this.id        = u.getId().toString();
        this.email     = u.getEmail();
        this.role      = u.getRole().name();
        this.firstName = u.getFirstName();
        this.lastName  = u.getLastName();
        this.createdAt = u.getCreatedAt();
    }

    public String getId()            { return id; }
    public String getEmail()         { return email; }
    public String getRole()          { return role; }
    public String getFirstName()     { return firstName; }
    public String getLastName()      { return lastName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
