package com.printplatform.dto;

public class UpdateProfileRequest {
    private String firstName;
    private String lastName;
    private String phone;
    private String gender;
    private String bio;
    private String dateOfBirth; // "yyyy-MM-dd"

    public String getFirstName()   { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName()    { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhone()       { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getGender()      { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getBio()         { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
}
