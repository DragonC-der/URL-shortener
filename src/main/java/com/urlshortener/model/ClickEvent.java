package com.urlshortener.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "click_events", indexes = {
        @Index(name = "idx_click_url_mapping", columnList = "url_mapping_id")
})
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_mapping_id", nullable = false)
    private UrlMapping urlMapping;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    private String referrer;

    // Stored as-is for a portfolio project; a real production system would
    // hash or truncate this for privacy compliance rather than storing raw IPs.
    private String clientIp;

    public ClickEvent() {}

    public ClickEvent(UrlMapping urlMapping, String referrer, String clientIp) {
        this.urlMapping = urlMapping;
        this.referrer = referrer;
        this.clientIp = clientIp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UrlMapping getUrlMapping() { return urlMapping; }
    public void setUrlMapping(UrlMapping urlMapping) { this.urlMapping = urlMapping; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getReferrer() { return referrer; }
    public void setReferrer(String referrer) { this.referrer = referrer; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
}
