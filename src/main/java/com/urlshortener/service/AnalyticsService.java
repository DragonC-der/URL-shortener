package com.urlshortener.service;

import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.model.ClickEvent;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlMappingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final ClickEventRepository clickEventRepository;
    private final UrlMappingRepository urlMappingRepository;

    public AnalyticsService(ClickEventRepository clickEventRepository,
                             UrlMappingRepository urlMappingRepository) {
        this.clickEventRepository = clickEventRepository;
        this.urlMappingRepository = urlMappingRepository;
    }

    public void recordClick(UrlMapping mapping, String referrer, String clientIp) {
        clickEventRepository.save(new ClickEvent(mapping, referrer, clientIp));
    }

    public AnalyticsResponse getAnalytics(String shortCode) {
        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new EntityNotFoundException("No URL found for code: " + shortCode));

        long totalClicks = clickEventRepository.countByUrlMapping(mapping);
        List<AnalyticsResponse.ClickSummary> recent =
                clickEventRepository.findTop20ByUrlMappingOrderByTimestampDesc(mapping).stream()
                        .map(e -> new AnalyticsResponse.ClickSummary(e.getTimestamp(), e.getReferrer()))
                        .collect(Collectors.toList());

        return new AnalyticsResponse(shortCode, mapping.getOriginalUrl(), totalClicks,
                mapping.getCreatedAt(), mapping.getExpiresAt(), recent);
    }
}
