package com.urlshortener.service;

import com.urlshortener.cache.LruCache;
import com.urlshortener.dto.ShortenRequest;
import com.urlshortener.dto.ShortenResponse;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.util.Base62Encoder;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class UrlShortenerService {

    private final UrlMappingRepository repository;
    private final LruCache<String, String> cache;
    private final String baseUrl;
    private final int defaultExpiryDays;

    public UrlShortenerService(UrlMappingRepository repository,
                                LruCache<String, String> cache,
                                @Value("${app.base-url}") String baseUrl,
                                @Value("${app.default-expiry-days}") int defaultExpiryDays) {
        this.repository = repository;
        this.cache = cache;
        this.baseUrl = baseUrl;
        this.defaultExpiryDays = defaultExpiryDays;
    }

    public ShortenResponse createShortUrl(ShortenRequest request) {
        Instant expiresAt = Instant.now().plus(
                request.getExpiresInDays() != null ? request.getExpiresInDays() : defaultExpiryDays,
                ChronoUnit.DAYS);

        String shortCode;

        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            // Custom aliases are the one place a real collision can occur,
            // since they don't come from the auto-increment ID encoding.
            if (repository.existsByShortCode(request.getCustomAlias())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Alias already in use: " + request.getCustomAlias());
            }
            UrlMapping mapping = new UrlMapping(request.getCustomAlias(), request.getOriginalUrl(), true, expiresAt);
            repository.save(mapping);
            shortCode = request.getCustomAlias();
        } else {
            // Two-step insert: the row must exist to have an ID, and the ID
            // is what gets Base62-encoded into the short code.
            UrlMapping mapping = new UrlMapping(request.getOriginalUrl(), expiresAt);
            mapping = repository.save(mapping);
            shortCode = Base62Encoder.encode(mapping.getId());
            mapping.setShortCode(shortCode);
            repository.save(mapping);
        }

        cache.put(shortCode, request.getOriginalUrl());

        return new ShortenResponse(shortCode, baseUrl + "/" + shortCode, request.getOriginalUrl(), expiresAt);
    }

    /** Cache-aside lookup: check the LRU cache first, only hit the DB on a miss. */
    public String resolveOriginalUrl(String shortCode) {
        String cached = cache.get(shortCode);
        if (cached != null) {
            return cached;
        }

        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new EntityNotFoundException("No URL found for code: " + shortCode));

        if (mapping.isExpired()) {
            throw new ResponseStatusException(HttpStatus.GONE, "This short URL has expired");
        }

        cache.put(shortCode, mapping.getOriginalUrl());
        return mapping.getOriginalUrl();
    }

    /** Used by the redirect endpoint to find the mapping to record a click against. */
    public Optional<UrlMapping> findMapping(String shortCode) {
        return repository.findByShortCode(shortCode);
    }

    public void incrementClickCount(UrlMapping mapping) {
        mapping.setClickCount(mapping.getClickCount() + 1);
        repository.save(mapping);
    }
}
