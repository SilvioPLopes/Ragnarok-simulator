package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MarketListingRepository extends JpaRepository<MarketListingEntity, Long> {
    List<MarketListingEntity> findByStatus(String status);
    List<MarketListingEntity> findByItemIdAndStatus(Long itemId, String status);
    long countByStatus(String status);
}
