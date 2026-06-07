package com.printplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Client-supplied fields for creating an offer. Server controls id/user/status. */
public class CreateOfferRequest {

    @NotNull(message = "Identyfikator zlecenia jest wymagany")
    private UUID listingId;

    @NotNull(message = "Cena jest wymagana")
    @Positive(message = "Cena musi być większa od zera")
    private BigDecimal price;

    @NotNull(message = "Czas druku jest wymagany")
    @Positive(message = "Czas druku musi być większy od zera")
    private Double printingTimeHours;

    @NotNull(message = "Zużycie filamentu jest wymagane")
    @Positive(message = "Zużycie filamentu musi być większe od zera")
    private Integer filamentGrams;

    @NotBlank(message = "Model drukarki jest wymagany")
    @Size(max = 100, message = "Nazwa drukarki jest zbyt długa")
    private String printerModel;

    public UUID getListingId() { return listingId; }
    public void setListingId(UUID listingId) { this.listingId = listingId; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Double getPrintingTimeHours() { return printingTimeHours; }
    public void setPrintingTimeHours(Double printingTimeHours) { this.printingTimeHours = printingTimeHours; }

    public Integer getFilamentGrams() { return filamentGrams; }
    public void setFilamentGrams(Integer filamentGrams) { this.filamentGrams = filamentGrams; }

    public String getPrinterModel() { return printerModel; }
    public void setPrinterModel(String printerModel) { this.printerModel = printerModel; }
}
