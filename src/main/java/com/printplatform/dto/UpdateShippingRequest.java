package com.printplatform.dto;

import jakarta.validation.constraints.Size;

public class UpdateShippingRequest {
    @Size(max = 255, message = "Ulica może mieć maksymalnie 255 znaków")
    private String street;

    @Size(max = 255, message = "Numer domu może mieć maksymalnie 255 znaków")
    private String houseNumber;

    @Size(max = 255, message = "Miasto może mieć maksymalnie 255 znaków")
    private String city;

    @Size(max = 255, message = "Kod pocztowy może mieć maksymalnie 255 znaków")
    private String postalCode;

    public String getStreet()      { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getHouseNumber() { return houseNumber; }
    public void setHouseNumber(String houseNumber) { this.houseNumber = houseNumber; }

    public String getCity()        { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPostalCode()  { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
}
