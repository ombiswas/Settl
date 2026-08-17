package com.settl.backend.expense;

import com.settl.backend.auth.CustomUserPrincipal;
import com.settl.backend.common.ApiResponse;
import com.settl.backend.expense.dto.CreatePersonalExpenseRequest;
import com.settl.backend.expense.dto.PersonalExpenseAnalyticsResponse;
import com.settl.backend.expense.dto.PersonalExpenseResponse;
import com.settl.backend.expense.dto.UpdatePersonalExpenseRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses/personal")
@Tag(name = "Personal Expenses", description = "Individual categorized expense tracking, budget management, and spending analytics")
@SecurityRequirement(name = "BearerAuth")
public class PersonalExpenseController {

    private final PersonalExpenseService personalExpenseService;

    public PersonalExpenseController(PersonalExpenseService personalExpenseService) {
        this.personalExpenseService = personalExpenseService;
    }

    @PostMapping
    @Operation(summary = "Create personal expense", description = "Records an individual personal expense categorized for spending tracking")
    public ResponseEntity<ApiResponse<PersonalExpenseResponse>> createPersonalExpense(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CreatePersonalExpenseRequest request
    ) {
        PersonalExpenseResponse response = personalExpenseService.createPersonalExpense(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Personal expense created successfully"));
    }

    @GetMapping
    @Operation(summary = "List personal expenses", description = "Retrieves the authenticated user's personal expenses with category and date range filters")
    public ResponseEntity<ApiResponse<List<PersonalExpenseResponse>>> getPersonalExpenses(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(value = "category", required = false) ExpenseCategory category,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<PersonalExpenseResponse> expenses = personalExpenseService.getPersonalExpenses(principal.id(), category, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(expenses, "Personal expenses retrieved successfully"));
    }

    @GetMapping("/analytics")
    @Operation(summary = "Get spending analytics", description = "Calculates category spending breakdown, monthly summaries, and total spent")
    public ResponseEntity<ApiResponse<PersonalExpenseAnalyticsResponse>> getPersonalAnalytics(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        PersonalExpenseAnalyticsResponse response = personalExpenseService.getPersonalAnalytics(principal.id(), startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(response, "Personal spending analytics generated successfully"));
    }

    @GetMapping("/{expenseId}")
    @Operation(summary = "Get personal expense details", description = "Retrieves details of a specific personal expense")
    public ResponseEntity<ApiResponse<PersonalExpenseResponse>> getPersonalExpenseById(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("expenseId") UUID expenseId
    ) {
        PersonalExpenseResponse response = personalExpenseService.getPersonalExpenseById(principal.id(), expenseId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{expenseId}")
    @Operation(summary = "Update personal expense", description = "Modifies an existing personal expense")
    public ResponseEntity<ApiResponse<PersonalExpenseResponse>> updatePersonalExpense(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("expenseId") UUID expenseId,
            @Valid @RequestBody UpdatePersonalExpenseRequest request
    ) {
        PersonalExpenseResponse response = personalExpenseService.updatePersonalExpense(principal.id(), expenseId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Personal expense updated successfully"));
    }

    @DeleteMapping("/{expenseId}")
    @Operation(summary = "Delete personal expense", description = "Deletes a personal expense")
    public ResponseEntity<ApiResponse<Void>> deletePersonalExpense(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable("expenseId") UUID expenseId
    ) {
        personalExpenseService.deletePersonalExpense(principal.id(), expenseId);
        return ResponseEntity.ok(ApiResponse.success(null, "Personal expense deleted successfully"));
    }
}
