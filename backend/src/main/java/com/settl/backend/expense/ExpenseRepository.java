package com.settl.backend.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.group.id = :groupId AND e.paidBy.id = :userId")
    BigDecimal sumPaidByUserIdInGroup(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("SELECT e FROM Expense e WHERE e.group.id = :groupId ORDER BY e.createdAt DESC")
    List<Expense> findByGroupIdOrderByCreatedAtDesc(@Param("groupId") UUID groupId);

    @Query("SELECT e FROM Expense e WHERE e.id = :expenseId AND e.group.id = :groupId")
    Optional<Expense> findByIdAndGroupId(@Param("expenseId") UUID expenseId, @Param("groupId") UUID groupId);

    @Query("SELECT e FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId ORDER BY e.createdAt DESC")
    List<Expense> findPersonalExpensesByUserId(@Param("userId") UUID userId);

    @Query("SELECT e FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId AND e.category = :category ORDER BY e.createdAt DESC")
    List<Expense> findPersonalExpensesByUserIdAndCategory(@Param("userId") UUID userId, @Param("category") ExpenseCategory category);

    @Query("SELECT e FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId AND e.createdAt >= :startDate AND e.createdAt <= :endDate ORDER BY e.createdAt DESC")
    List<Expense> findPersonalExpensesByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("SELECT e FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId AND e.category = :category AND e.createdAt >= :startDate AND e.createdAt <= :endDate ORDER BY e.createdAt DESC")
    List<Expense> findPersonalExpensesByUserIdAndCategoryAndDateRange(
            @Param("userId") UUID userId,
            @Param("category") ExpenseCategory category,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("SELECT e FROM Expense e WHERE e.id = :expenseId AND e.group IS NULL AND e.paidBy.id = :userId")
    Optional<Expense> findPersonalExpenseByIdAndUserId(@Param("expenseId") UUID expenseId, @Param("userId") UUID userId);
}
