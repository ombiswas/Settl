package com.settl.backend.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.group.id = :groupId AND s.fromUser.id = :userId")
    BigDecimal sumSettlementsPaidByUserIdInGroup(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.group.id = :groupId AND s.toUser.id = :userId")
    BigDecimal sumSettlementsReceivedByUserIdInGroup(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("SELECT s FROM Settlement s WHERE s.group.id = :groupId ORDER BY s.settledAt DESC")
    List<Settlement> findByGroupIdOrderBySettledAtDesc(@Param("groupId") UUID groupId);
}
