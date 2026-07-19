package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.URL;

public class ShortenRequest {

    @NotBlank
    @URL(message = "must be a valid URL, including http:// or https://")
    private String originalUrl;

    // Optional: user-supplied short code instead of an auto-generated one
    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,20}$", message = "alias must be 3-20 alphanumeric characters, - or _")
    private String customAlias;

    @Positive
    private Integer expiresInDays; // optional; falls back to app.default-expiry-days

    public String getOriginalUrl() { return originalUrl; }
    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }

    public String getCustomAlias() { return customAlias; }
    public void setCustomAlias(String customAlias) { this.customAlias = customAlias; }

    public Integer getExpiresInDays() { return expiresInDays; }
    public void setExpiresInDays(Integer expiresInDays) { this.expiresInDays = expiresInDays; }
}
