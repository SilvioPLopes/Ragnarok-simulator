package com.ragnarok.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CashShopItemRepository extends JpaRepository<CashShopItemEntity, Long> {
    List<CashShopItemEntity> findByActiveTrue();
    long countByActiveTrue();
}
