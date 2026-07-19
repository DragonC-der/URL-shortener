package com.urlshortener.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "url_mappings", indexes = {
        @Index(name = "idx_short_code", columnList = "shortCode", unique = true)
})
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Indexed + unique: this is the column every redirect request looks up by,
    // so it needs to be fast under high read load - a DB index turns that
    // lookup from a table scan into a direct index lookup.
    //
    // Not NOT-NULL at the column level: for auto-generated codes, the short
    // code is derived from this row's own auto-increment ID (see
    // UrlShortenerService), so the row must be inserted first to obtain that
    // ID, then updated with the encoded short code. It is never left null
    // once the service call returns.
    @Column(unique = true, length = 20)
    private String shortCode;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @Column(nullable = false)
    private boolean customAlias = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant expiresAt;

    @Column(nullable = false)
    private long clickCount = 0;

    public UrlMapping() {}

    public UrlMapping(String shortCode, String originalUrl, boolean customAlias, Instant expiresAt) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.customAlias = customAlias;
        this.expiresAt = expiresAt;
    }

    /** Used for auto-generated codes, where shortCode isn't known until after
     *  the initial insert produces this row's ID (see UrlShortenerService). */
    public UrlMapping(String originalUrl, Instant expiresAt) {
        this.originalUrl = originalUrl;
        this.customAlias = false;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }

    public String getOriginalUrl() { return originalUrl; }
    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }

    public boolean isCustomAlias() { return customAlias; }
    public void setCustomAlias(boolean customAlias) { this.customAlias = customAlias; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public long getClickCount() { return clickCount; }
    public void setClickCount(long clickCount) { this.clickCount = clickCount; }
}
