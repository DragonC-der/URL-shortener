package com.urlshortener.util;

/**
 * Classic token bucket: a bucket starts full, each request consumes one
 * token, and tokens refill continuously over time up to the bucket's
 * capacity. This allows short bursts (spend the full bucket at once)
 * while still enforcing a steady-state average rate - unlike a naive
 * fixed-window counter, which allows a burst of 2x the limit right at
 * a window boundary (e.g. 10 requests in the last second of one window,
 * then another 10 in the first second of the next).
 */
public class TokenBucket {

    private final double capacity;
    private final double refillPerMillisecond;
    private double tokens;
    private long lastRefillTimestampMs;

    public TokenBucket(double capacity, double refillPerMinute) {
        this.capacity = capacity;
        this.tokens = capacity; // start full
        this.refillPerMillisecond = refillPerMinute / 60000.0;
        this.lastRefillTimestampMs = System.currentTimeMillis();
    }

    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsedMs = now - lastRefillTimestampMs;
        if (elapsedMs <= 0) return;

        double tokensToAdd = elapsedMs * refillPerMillisecond;
        tokens = Math.min(capacity, tokens + tokensToAdd);
        lastRefillTimestampMs = now;
    }
}
