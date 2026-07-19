package com.urlshortener.dto;

import java.time.Instant;
import java.util.List;

public class AnalyticsResponse {
    private String shortCode;
    private String originalUrl;
    private long totalClicks;
    private Instant createdAt;
    private Instant expiresAt; // null if the URL never expires
    private List<ClickSummary> recentClicks;

    public AnalyticsResponse(String shortCode, String originalUrl, long totalClicks,
                              Instant createdAt, Instant expiresAt, List<ClickSummary> recentClicks) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.totalClicks = totalClicks;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.recentClicks = recentClicks;
    }

    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }

    public String getOriginalUrl() { return originalUrl; }
    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }

    public long getTotalClicks() { return totalClicks; }
    public void setTotalClicks(long totalClicks) { this.totalClicks = totalClicks; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public List<ClickSummary> getRecentClicks() { return recentClicks; }
    public void setRecentClicks(List<ClickSummary> recentClicks) { this.recentClicks = recentClicks; }

    public static class ClickSummary {
        private Instant timestamp;
        private String referrer;

        public ClickSummary(Instant timestamp, String referrer) {
            this.timestamp = timestamp;
            this.referrer = referrer;
        }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        public String getReferrer() { return referrer; }
        public void setReferrer(String referrer) { this.referrer = referrer; }
    }
}
