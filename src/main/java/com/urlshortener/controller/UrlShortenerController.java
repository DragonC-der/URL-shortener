package com.urlshortener.controller;

import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.service.AnalyticsService;
import com.urlshortener.service.RateLimiterService;
import com.urlshortener.service.UrlShortenerService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@RestController
public class UrlShortenerController {

    private final UrlShortenerService urlShortenerService;
    private final AnalyticsService analyticsService;
    private final RateLimiterService rateLimiterService;

    public UrlShortenerController(UrlShortenerService urlShortenerService,
                                   AnalyticsService analyticsService,
                                   RateLimiterService rateLimiterService) {
        this.urlShortenerService = urlShortenerService;
        this.analyticsService = analyticsService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/api/shorten")
    public ResponseEntity<?> shorten(@Valid @RequestBody ShortenRequest request, HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);

        if (!rateLimiterService.allowRequest(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limit exceeded - try again in a moment");
        }

        ShortenResponse response = urlShortenerService.createShortUrl(request);
        return ResponseEntity.ok(response);
    }

    /** The actual redirect - this is the hot path every cache/index decision optimizes for.
     *  The regex constraint excludes anything containing a dot, since short codes are always
     *  plain alphanumeric (see Base62Encoder / ShortenRequest's alias pattern) while static
     *  assets like index.html or favicon.ico always have one - without this, this catch-all
     *  mapping would shadow Spring's static resource serving for the frontend entirely. */
    @GetMapping("/{shortCode:^[^.]*$}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode,
                                          @RequestHeader(value = "Referer", required = false) String referrer,
                                          HttpServletRequest httpRequest) {
        String originalUrl = urlShortenerService.resolveOriginalUrl(shortCode);

        // Recording the click is a side effect of the redirect, not blocking
        // path resolution - the mapping lookup here is a second, cheap query
        // (findByShortCode was already warmed into the cache above).
        urlShortenerService.findMapping(shortCode).ifPresent(mapping -> {
            urlShortenerService.incrementClickCount(mapping);
            analyticsService.recordClick(mapping, referrer, extractClientIp(httpRequest));
        });

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }

    @GetMapping("/api/analytics/{shortCode}")
    public ResponseEntity<AnalyticsResponse> analytics(@PathVariable String shortCode) {
        return ResponseEntity.ok(analyticsService.getAnalytics(shortCode));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<String> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
    }

    /** Render (and most PaaS/load balancer setups) sit in front of the app,
     *  so the real client IP arrives via X-Forwarded-For, not getRemoteAddr(). */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
