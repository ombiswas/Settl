package com.settl.backend.expense;

import com.settl.backend.auth.CustomUserPrincipal;
import com.settl.backend.common.ApiResponse;
import com.settl.backend.expense.dto.CreateExpenseRequest;
import com.settl.backend.expense.dto.ExpenseResponse;
import com.settl.backend.expense.dto.UpdateExpenseRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
@Tag(name = "Group Expenses", description = "Group expense creation, multi-strategy splits (EQUAL, EXACT, PERCENTAGE, SHARES), editing, and deletion")
@SecurityRequirement(name = "BearerAuth")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    @Operation(summary = "Create group expense", description = "Records a new expense in the group and calculates member debt shares according to split strategy")
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @PathVariable("groupId") UUID groupId,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CreateExpenseRequest request
    ) {
        ExpenseResponse response = expenseService.createGroupExpense(groupId, principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Expense created successfully"));
    }

    @GetMapping
    @Operation(summary = "List group expenses", description = "Retrieves all expenses recorded in the group (member-only)")
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getGroupExpenses(
            @PathVariable("groupId") UUID groupId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        List<ExpenseResponse> expenses = expenseService.getGroupExpenses(groupId, principal.id());
        return ResponseEntity.ok(ApiResponse.success(expenses, "Group expenses retrieved successfully"));
    }

    @GetMapping("/{expenseId}")
    @Operation(summary = "Get group expense details", description = "Retrieves details and exact share breakdowns of a group expense")
    public ResponseEntity<ApiResponse<ExpenseResponse>> getExpenseById(
            @PathVariable("groupId") UUID groupId,
            @PathVariable("expenseId") UUID expenseId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        ExpenseResponse response = expenseService.getGroupExpenseById(groupId, expenseId, principal.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{expenseId}")
    @Operation(summary = "Update group expense", description = "Updates an expense and recalculates shares (creator or group admin only)")
    public ResponseEntity<ApiResponse<ExpenseResponse>> updateExpense(
            @PathVariable("groupId") UUID groupId,
            @PathVariable("expenseId") UUID expenseId,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody UpdateExpenseRequest request
    ) {
        ExpenseResponse response = expenseService.updateGroupExpense(groupId, expenseId, principal.id(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Expense updated successfully"));
    }

    @DeleteMapping("/{expenseId}")
    @Operation(summary = "Delete group expense", description = "Removes an expense from the group (creator or group admin only)")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(
            @PathVariable("groupId") UUID groupId,
            @PathVariable("expenseId") UUID expenseId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        expenseService.deleteGroupExpense(groupId, expenseId, principal.id());
        return ResponseEntity.ok(ApiResponse.success(null, "Expense deleted successfully"));
    }
}
