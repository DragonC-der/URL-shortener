package com.urlshortener.repository;

import com.urlshortener.model.ClickEvent;
import com.urlshortener.model.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {
    List<ClickEvent> findTop20ByUrlMappingOrderByTimestampDesc(UrlMapping urlMapping);
    long countByUrlMapping(UrlMapping urlMapping);
}
