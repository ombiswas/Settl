package com.settl.backend.recurring;

import com.settl.backend.auth.CustomUserPrincipal;
import com.settl.backend.common.ApiResponse;
import com.settl.backend.recurring.dto.CreateRecurringExpenseRequest;
import com.settl.backend.recurring.dto.RecurringExpenseResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/recurring-expenses")
@Tag(name = "Recurring Expenses", description = "Scheduled automated recurring expenses (weekly/monthly) with automated debt shares")
@SecurityRequirement(name = "BearerAuth")
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseController(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    @PostMapping
    @Operation(summary = "Create recurring expense template", description = "Sets up an automated recurring expense (e.g., rent, subscriptions) that triggers periodically")
    public ResponseEntity<ApiResponse<RecurringExpenseResponse>> createRecurringExpense(
            @PathVariable("groupId") UUID groupId,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CreateRecurringExpenseRequest request
    ) {
        RecurringExpenseResponse response = recurringExpenseService.createRecurringExpense(groupId, principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Recurring expense created successfully"));
    }

    @GetMapping
    @Operation(summary = "List group recurring expenses", description = "Retrieves all active and historical recurring expense templates in the group")
    public ResponseEntity<ApiResponse<List<RecurringExpenseResponse>>> getGroupRecurringExpenses(
            @PathVariable("groupId") UUID groupId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        List<RecurringExpenseResponse> list = recurringExpenseService.getGroupRecurringExpenses(groupId, principal.id());
        return ResponseEntity.ok(ApiResponse.success(list, "Group recurring expenses retrieved successfully"));
    }

    @DeleteMapping("/{recurringId}")
    @Operation(summary = "Deactivate recurring expense", description = "Stops future automated runs of a recurring expense (creator or group admin only)")
    public ResponseEntity<ApiResponse<Void>> deactivateRecurringExpense(
            @PathVariable("groupId") UUID groupId,
            @PathVariable("recurringId") UUID recurringId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        recurringExpenseService.deactivateRecurringExpense(groupId, recurringId, principal.id());
        return ResponseEntity.ok(ApiResponse.success(null, "Recurring expense deactivated successfully"));
    }
}
