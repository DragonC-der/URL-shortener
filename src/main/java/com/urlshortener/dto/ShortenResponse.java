package com.urlshortener.dto;

import java.time.Instant;

public class ShortenResponse {
    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private Instant expiresAt;

    public ShortenResponse(String shortCode, String shortUrl, String originalUrl, Instant expiresAt) {
        this.shortCode = shortCode;
        this.shortUrl = shortUrl;
        this.originalUrl = originalUrl;
        this.expiresAt = expiresAt;
    }

    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }

    public String getShortUrl() { return shortUrl; }
    public void setShortUrl(String shortUrl) { this.shortUrl = shortUrl; }

    public String getOriginalUrl() { return originalUrl; }
    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
