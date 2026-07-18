package com.printplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column
    private String password;

    @Column(unique = true)
    private String facebookId;

    @Column(unique = true)
    private String googleId;

    // Column-level default of false is required: Hibernate's ddl-auto=update issues a plain
    // ALTER TABLE ADD COLUMN with no default unless @ColumnDefault is present, and this field
    // is a primitive boolean — reading a NULL column into it throws, not just defaults to false.
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean emailVerified = false;

    // Column-level default of false is required: Hibernate's ddl-auto=update issues a plain
    // ALTER TABLE ADD COLUMN with no default unless @ColumnDefault is present, and this field
    // is a primitive boolean — reading a NULL column into it throws, not just defaults to false.
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean suspended = false;

    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    private String firstName;
    private String lastName;

    // --- Sensitive PII: never expose when the entity is serialized as a nested object
    //     (listings/offers/messages are publicly readable). The owner reads these via
    //     the /api/users/me UserProfileDto, not through the raw entity. ---
    @JsonIgnore private String phone;
    @JsonIgnore private String gender;

    @JsonIgnore
    @Column(length = 500)
    private String bio;

    @JsonIgnore
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @JsonIgnore private String street;
    @JsonIgnore private String houseNumber;
    @JsonIgnore private String city;
    @JsonIgnore private String postalCode;

    @JsonIgnore private String stripeCustomerId;

    private LocalDateTime createdAt = LocalDateTime.now();

    // Column-level default is required: Hibernate's ddl-auto=update issues a plain
    // ALTER TABLE ADD COLUMN with no default unless @ColumnDefault is present, and this field
    // is a primitive boolean — reading a NULL column into it throws, not just defaults to false.
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean avatarImportSkipped = false;

    @JsonIgnore
    @Column(columnDefinition = "bytea")
    private byte[] avatarData;

    private String avatarContentType;

    private String avatarUrl;

    private String googleAvatarUrl;

    private LocalDateTime lastLoginAt;

    private String nickname;

    // Column-level default is required: Hibernate's ddl-auto=update issues a plain
    // ALTER TABLE ADD COLUMN with no default unless @ColumnDefault is present, and this field
    // is a primitive boolean — reading a NULL column into it throws, not just defaults to false.
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean showCity = false;

    // Column-level default is required: same reasoning as showCity above — defaults to true so
    // existing users keep showing their real name until they explicitly opt into a nickname.
    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean showRealName = true;

    // --- UserDetails ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getUsername()              { return email; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return !suspended; }

    // --- Getters / Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFacebookId() { return facebookId; }
    public void setFacebookId(String facebookId) { this.facebookId = facebookId; }

    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public boolean isSuspended() { return suspended; }
    public void setSuspended(boolean suspended) { this.suspended = suspended; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getHouseNumber() { return houseNumber; }
    public void setHouseNumber(String houseNumber) { this.houseNumber = houseNumber; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isAvatarImportSkipped() { return avatarImportSkipped; }
    public void setAvatarImportSkipped(boolean avatarImportSkipped) { this.avatarImportSkipped = avatarImportSkipped; }

    public byte[] getAvatarData() { return avatarData; }
    public void setAvatarData(byte[] avatarData) { this.avatarData = avatarData; }

    public String getAvatarContentType() { return avatarContentType; }
    public void setAvatarContentType(String avatarContentType) { this.avatarContentType = avatarContentType; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getGoogleAvatarUrl() { return googleAvatarUrl; }
    public void setGoogleAvatarUrl(String googleAvatarUrl) { this.googleAvatarUrl = googleAvatarUrl; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public boolean isShowCity() { return showCity; }
    public void setShowCity(boolean showCity) { this.showCity = showCity; }

    public boolean isShowRealName() { return showRealName; }
    public void setShowRealName(boolean showRealName) { this.showRealName = showRealName; }
}
