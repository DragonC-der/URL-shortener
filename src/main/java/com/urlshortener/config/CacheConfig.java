package com.urlshortener.config;

import com.urlshortener.cache.LruCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public LruCache<String, String> urlCache(@Value("${app.cache.capacity}") int capacity) {
        // Maps shortCode -> originalUrl for the hottest recently-accessed links,
        // so repeat redirects skip the DB round-trip entirely.
        return new LruCache<>(capacity);
    }
}
