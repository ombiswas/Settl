package com.settl.backend.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, UUID> {

    @Query("SELECT COALESCE(SUM(es.amountOwed), 0) FROM ExpenseShare es WHERE es.expense.group.id = :groupId AND es.user.id = :userId")
    BigDecimal sumOwedByUserIdInGroup(@Param("groupId") UUID groupId, @Param("userId") UUID userId);
}
