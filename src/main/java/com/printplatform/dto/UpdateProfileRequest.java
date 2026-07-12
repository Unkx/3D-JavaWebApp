package com.printplatform.dto;

import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {
    @Size(max = 255, message = "Imię może mieć maksymalnie 255 znaków")
    private String firstName;

    @Size(max = 255, message = "Nazwisko może mieć maksymalnie 255 znaków")
    private String lastName;

    @Size(max = 255, message = "Numer telefonu może mieć maksymalnie 255 znaków")
    private String phone;

    @Size(max = 255, message = "Pole 'płeć' może mieć maksymalnie 255 znaków")
    private String gender;

    @Size(max = 500, message = "Bio może mieć maksymalnie 500 znaków")
    private String bio;

    @Size(max = 10, message = "Nieprawidłowy format daty urodzenia")
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
