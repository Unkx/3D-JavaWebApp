package com.printplatform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateRatingRequest {
    @NotNull(message = "Ocena jest wymagana")
    @Min(value = 1, message = "Ocena musi być od 1 do 5")
    @Max(value = 5, message = "Ocena musi być od 1 do 5")
    private Integer stars;

    @Size(max = 500, message = "Komentarz jest zbyt długi")
    private String comment;

    public Integer getStars() { return stars; }
    public void setStars(Integer stars) { this.stars = stars; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
