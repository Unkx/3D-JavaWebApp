package com.printplatform.dto;

import java.time.LocalDateTime;

public class UserProfileDto {
    private String id;
    private String email;
    private String role;
    private LocalDateTime createdAt;
    private long listingsCount;
    private long offersCount;
    private String firstName;
    private String lastName;
    private String phone;
    private String gender;
    private String bio;
    private String dateOfBirth;
    private String street;
    private String houseNumber;
    private String city;
    private String postalCode;
    private boolean showCity;
    private boolean showRealName;

    public UserProfileDto(String id, String email, String role, LocalDateTime createdAt,
                          long listingsCount, long offersCount,
                          String firstName, String lastName, String phone,
                          String gender, String bio, String dateOfBirth,
                          String street, String houseNumber, String city, String postalCode,
                          boolean showCity, boolean showRealName) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
        this.listingsCount = listingsCount;
        this.offersCount = offersCount;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.gender = gender;
        this.bio = bio;
        this.dateOfBirth = dateOfBirth;
        this.street = street;
        this.houseNumber = houseNumber;
        this.city = city;
        this.postalCode = postalCode;
        this.showCity = showCity;
        this.showRealName = showRealName;
    }

    public String getId()            { return id; }
    public String getEmail()         { return email; }
    public String getRole()          { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public long getListingsCount()   { return listingsCount; }
    public long getOffersCount()     { return offersCount; }
    public String getFirstName()     { return firstName; }
    public String getLastName()      { return lastName; }
    public String getPhone()         { return phone; }
    public String getGender()        { return gender; }
    public String getBio()           { return bio; }
    public String getDateOfBirth()   { return dateOfBirth; }
    public String getStreet()        { return street; }
    public String getHouseNumber()   { return houseNumber; }
    public String getCity()          { return city; }
    public String getPostalCode()    { return postalCode; }
    public boolean isShowCity()      { return showCity; }
    public boolean isShowRealName()  { return showRealName; }
}
