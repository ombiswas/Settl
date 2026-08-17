package com.settl.backend.recurring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {

    @Query("SELECT r FROM RecurringExpense r JOIN FETCH r.group JOIN FETCH r.paidBy WHERE r.active = true AND r.nextRunAt <= :threshold")
    List<RecurringExpense> findDueRecurringExpenses(@Param("threshold") Instant threshold);

    @Query("SELECT r FROM RecurringExpense r WHERE r.group.id = :groupId ORDER BY r.createdAt DESC")
    List<RecurringExpense> findByGroupIdOrderByCreatedAtDesc(@Param("groupId") UUID groupId);

    @Query("SELECT r FROM RecurringExpense r WHERE r.id = :id AND r.group.id = :groupId")
    Optional<RecurringExpense> findByIdAndGroupId(@Param("id") UUID id, @Param("groupId") UUID groupId);
}
