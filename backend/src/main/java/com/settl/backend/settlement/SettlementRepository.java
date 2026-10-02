package com.settl.backend.settlement;

import com.settl.backend.settlement.dto.UserAmountDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    @Query("SELECT new com.settl.backend.settlement.dto.UserAmountDto(s.fromUser.id, SUM(s.amount)) " +
           "FROM Settlement s WHERE s.group.id = :groupId GROUP BY s.fromUser.id")
    List<UserAmountDto> findTotalSettlementsPaidPerUserInGroup(@Param("groupId") UUID groupId);

    @Query("SELECT new com.settl.backend.settlement.dto.UserAmountDto(s.toUser.id, SUM(s.amount)) " +
           "FROM Settlement s WHERE s.group.id = :groupId GROUP BY s.toUser.id")
    List<UserAmountDto> findTotalSettlementsReceivedPerUserInGroup(@Param("groupId") UUID groupId);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.group.id = :groupId AND s.fromUser.id = :userId")
    BigDecimal sumSettlementsPaidByUserIdInGroup(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.group.id = :groupId AND s.toUser.id = :userId")
    BigDecimal sumSettlementsReceivedByUserIdInGroup(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("SELECT s FROM Settlement s WHERE s.group.id = :groupId ORDER BY s.settledAt DESC")
    List<Settlement> findByGroupIdOrderBySettledAtDesc(@Param("groupId") UUID groupId);

    @Query(
            value = "SELECT s FROM Settlement s JOIN FETCH s.fromUser JOIN FETCH s.toUser JOIN FETCH s.group WHERE s.group.id = :groupId ORDER BY s.settledAt DESC, s.id DESC",
            countQuery = "SELECT count(s) FROM Settlement s WHERE s.group.id = :groupId"
    )
    Page<Settlement> findByGroupIdOrderBySettledAtDesc(@Param("groupId") UUID groupId, Pageable pageable);

    @Query("SELECT COUNT(s) FROM Settlement s WHERE s.fromUser.id = :userId OR s.toUser.id = :userId")
    long countSettlementsByUserId(@Param("userId") UUID userId);
}
