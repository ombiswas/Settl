package com.settl.backend.expense;

import com.settl.backend.settlement.dto.UserAmountDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, UUID> {

    @Query("SELECT new com.settl.backend.settlement.dto.UserAmountDto(es.user.id, SUM(es.amountOwed)) " +
           "FROM ExpenseShare es WHERE es.expense.group.id = :groupId GROUP BY es.user.id")
    List<UserAmountDto> findTotalOwedPerUserInGroup(@Param("groupId") UUID groupId);

    @Query("SELECT COALESCE(SUM(es.amountOwed), 0) FROM ExpenseShare es WHERE es.expense.group.id = :groupId AND es.user.id = :userId")
    BigDecimal sumOwedByUserIdInGroup(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(es) FROM ExpenseShare es WHERE es.expense.group IS NOT NULL AND es.user.id = :userId")
    long countGroupExpenseSharesByUserId(@Param("userId") UUID userId);

    @Query("SELECT es FROM ExpenseShare es JOIN FETCH es.user WHERE es.expense.id IN :expenseIds")
    List<ExpenseShare> findByExpenseIdIn(@Param("expenseIds") List<UUID> expenseIds);
}
