package com.StockLab.backend.Order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findByUidOrderByCreatedAtDesc(String uid);
}

@Repository
interface PositionRepository extends JpaRepository<PositionEntity, Long> {
    List<PositionEntity> findByUid(String uid);
    Optional<PositionEntity> findByUidAndSymbol(String uid, String symbol);
}

@Repository
interface CashLedgerRepository extends JpaRepository<CashLedgerEntity, Long> {
    List<CashLedgerEntity> findByUidOrderByCreatedAtDesc(String uid);
}
