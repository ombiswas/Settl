package com.settl.backend.expense;

import com.settl.backend.expense.dto.CurrencyCategorySpendingDto;
import com.settl.backend.expense.dto.CurrencySummaryDto;
import com.settl.backend.expense.dto.ExpenseDateAmountDto;
import com.settl.backend.settlement.dto.UserAmountDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("SELECT new com.settl.backend.settlement.dto.UserAmountDto(e.paidBy.id, SUM(e.amount)) " +
           "FROM Expense e WHERE e.group.id = :groupId GROUP BY e.paidBy.id")
    List<UserAmountDto> findTotalPaidPerUserInGroup(@Param("groupId") UUID groupId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.group.id = :groupId AND e.paidBy.id = :userId")
    BigDecimal sumPaidByUserIdInGroup(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("SELECT e FROM Expense e WHERE e.group.id = :groupId ORDER BY e.createdAt DESC")
    List<Expense> findByGroupIdOrderByCreatedAtDesc(@Param("groupId") UUID groupId);

    @Query(
            value = "SELECT e FROM Expense e JOIN FETCH e.paidBy JOIN FETCH e.group WHERE e.group.id = :groupId ORDER BY e.createdAt DESC, e.id DESC",
            countQuery = "SELECT count(e) FROM Expense e WHERE e.group.id = :groupId"
    )
    Page<Expense> findByGroupIdOrderByCreatedAtDesc(@Param("groupId") UUID groupId, Pageable pageable);

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

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId")
    void deletePersonalExpensesByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(e) FROM Expense e WHERE e.group IS NOT NULL AND e.paidBy.id = :userId")
    long countGroupExpensesPaidByUserId(@Param("userId") UUID userId);

    // --- Personal Expense Analytics Aggregations ---

    @Query("SELECT new com.settl.backend.expense.dto.CurrencySummaryDto(e.currency, COALESCE(SUM(e.amount), 0), COUNT(e)) " +
           "FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId " +
           "GROUP BY e.currency ORDER BY e.currency ASC")
    List<CurrencySummaryDto> findPersonalTotalsByUserId(@Param("userId") UUID userId);

    @Query("SELECT new com.settl.backend.expense.dto.CurrencySummaryDto(e.currency, COALESCE(SUM(e.amount), 0), COUNT(e)) " +
           "FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId " +
           "AND e.createdAt >= :startDate AND e.createdAt <= :endDate " +
           "GROUP BY e.currency ORDER BY e.currency ASC")
    List<CurrencySummaryDto> findPersonalTotalsByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("SELECT new com.settl.backend.expense.dto.CurrencySummaryDto(e.currency, COALESCE(SUM(e.amount), 0), COUNT(e)) " +
           "FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId AND e.currency = :currency " +
           "GROUP BY e.currency")
    List<CurrencySummaryDto> findPersonalTotalsByUserIdAndCurrency(
            @Param("userId") UUID userId,
            @Param("currency") String currency
    );

    @Query("SELECT new com.settl.backend.expense.dto.CurrencySummaryDto(e.currency, COALESCE(SUM(e.amount), 0), COUNT(e)) " +
           "FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId AND e.currency = :currency " +
           "AND e.createdAt >= :startDate AND e.createdAt <= :endDate " +
           "GROUP BY e.currency")
    List<CurrencySummaryDto> findPersonalTotalsByUserIdAndCurrencyAndDateRange(
            @Param("userId") UUID userId,
            @Param("currency") String currency,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("SELECT new com.settl.backend.expense.dto.CurrencyCategorySpendingDto(e.currency, e.category, COALESCE(SUM(e.amount), 0), COUNT(e)) " +
           "FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId " +
           "GROUP BY e.currency, e.category ORDER BY e.currency ASC, SUM(e.amount) DESC")
    List<CurrencyCategorySpendingDto> findPersonalCategoriesByUserId(@Param("userId") UUID userId);

    @Query("SELECT new com.settl.backend.expense.dto.CurrencyCategorySpendingDto(e.currency, e.category, COALESCE(SUM(e.amount), 0), COUNT(e)) " +
           "FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId " +
           "AND e.createdAt >= :startDate AND e.createdAt <= :endDate " +
           "GROUP BY e.currency, e.category ORDER BY e.currency ASC, SUM(e.amount) DESC")
    List<CurrencyCategorySpendingDto> findPersonalCategoriesByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("SELECT new com.settl.backend.expense.dto.CurrencyCategorySpendingDto(e.currency, e.category, COALESCE(SUM(e.amount), 0), COUNT(e)) " +
           "FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId AND e.currency = :currency " +
           "GROUP BY e.currency, e.category ORDER BY SUM(e.amount) DESC")
    List<CurrencyCategorySpendingDto> findPersonalCategoriesByUserIdAndCurrency(
            @Param("userId") UUID userId,
            @Param("currency") String currency
    );

    @Query("SELECT new com.settl.backend.expense.dto.CurrencyCategorySpendingDto(e.currency, e.category, COALESCE(SUM(e.amount), 0), COUNT(e)) " +
           "FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId AND e.currency = :currency " +
           "AND e.createdAt >= :startDate AND e.createdAt <= :endDate " +
           "GROUP BY e.currency, e.category ORDER BY SUM(e.amount) DESC")
    List<CurrencyCategorySpendingDto> findPersonalCategoriesByUserIdAndCurrencyAndDateRange(
            @Param("userId") UUID userId,
            @Param("currency") String currency,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("SELECT new com.settl.backend.expense.dto.ExpenseDateAmountDto(e.currency, e.createdAt, e.amount) " +
           "FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId " +
           "ORDER BY e.createdAt ASC")
    List<ExpenseDateAmountDto> findPersonalExpenseDatesByUserId(@Param("userId") UUID userId);

    @Query("SELECT new com.settl.backend.expense.dto.ExpenseDateAmountDto(e.currency, e.createdAt, e.amount) " +
           "FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId " +
           "AND e.createdAt >= :startDate AND e.createdAt <= :endDate " +
           "ORDER BY e.createdAt ASC")
    List<ExpenseDateAmountDto> findPersonalExpenseDatesByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("SELECT new com.settl.backend.expense.dto.ExpenseDateAmountDto(e.currency, e.createdAt, e.amount) " +
           "FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId AND e.currency = :currency " +
           "ORDER BY e.createdAt ASC")
    List<ExpenseDateAmountDto> findPersonalExpenseDatesByUserIdAndCurrency(
            @Param("userId") UUID userId,
            @Param("currency") String currency
    );

    @Query("SELECT new com.settl.backend.expense.dto.ExpenseDateAmountDto(e.currency, e.createdAt, e.amount) " +
           "FROM Expense e WHERE e.group IS NULL AND e.paidBy.id = :userId AND e.currency = :currency " +
           "AND e.createdAt >= :startDate AND e.createdAt <= :endDate " +
           "ORDER BY e.createdAt ASC")
    List<ExpenseDateAmountDto> findPersonalExpenseDatesByUserIdAndCurrencyAndDateRange(
            @Param("userId") UUID userId,
            @Param("currency") String currency,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
}
