package com.urlshortener.service;

import com.urlshortener.util.TokenBucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * One token bucket per client IP. Buckets are created lazily on first
 * request and live for the lifetime of the application - for a portfolio
 * project this is fine, but it's worth naming the real limitation: this
 * map grows unboundedly with the number of distinct IPs seen, and a
 * production version would need an eviction policy (e.g. remove buckets
 * untouched for N minutes) or move this state to Redis so it doesn't
 * live in a single instance's heap at all.
 */
@Service
public class RateLimiterService {

    private final double capacity;
    private final double refillPerMinute;
    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiterService(
            @Value("${app.rate-limit.capacity}") double capacity,
            @Value("${app.rate-limit.refill-per-minute}") double refillPerMinute) {
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
    }

    public boolean allowRequest(String clientKey) {
        TokenBucket bucket = buckets.computeIfAbsent(clientKey,
                k -> new TokenBucket(capacity, refillPerMinute));
        return bucket.tryConsume();
    }
}
