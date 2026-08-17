package com.settl.backend.expense;

import com.settl.backend.common.ApiResponse;
import com.settl.backend.expense.dto.CategoryInfoDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "Standardized expense categories for personal and group expenses")
public class CategoryController {

    private final PersonalExpenseService personalExpenseService;

    public CategoryController(PersonalExpenseService personalExpenseService) {
        this.personalExpenseService = personalExpenseService;
    }

    @GetMapping
    @Operation(summary = "List all expense categories", description = "Retrieves all standard system expense categories and display names")
    public ResponseEntity<ApiResponse<List<CategoryInfoDto>>> getCategories() {
        List<CategoryInfoDto> categories = personalExpenseService.getAllCategories();
        return ResponseEntity.ok(ApiResponse.success(categories, "Expense categories retrieved successfully"));
    }
}
