package com.settl.backend.settlement;

import com.settl.backend.auth.CustomUserPrincipal;
import com.settl.backend.common.ApiResponse;
import com.settl.backend.settlement.dto.CreateSettlementRequest;
import com.settl.backend.settlement.dto.SettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/settlements")
@Tag(name = "Settlements", description = "Record actual settlements, debt repayments, and view settlement ledger")
@SecurityRequirement(name = "BearerAuth")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping
    @Operation(summary = "Record a settlement", description = "Records an actual debt repayment between two group members, immediately reducing outstanding balances")
    public ResponseEntity<ApiResponse<SettlementResponse>> recordSettlement(
            @PathVariable("groupId") UUID groupId,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CreateSettlementRequest request
    ) {
        SettlementResponse response = settlementService.recordSettlement(groupId, principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Settlement recorded successfully"));
    }

    @GetMapping
    @Operation(summary = "List group settlements", description = "Retrieves all recorded settlements and debt payments in this group (member-only)")
    public ResponseEntity<ApiResponse<List<SettlementResponse>>> getGroupSettlements(
            @PathVariable("groupId") UUID groupId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        List<SettlementResponse> settlements = settlementService.getGroupSettlements(groupId, principal.id());
        return ResponseEntity.ok(ApiResponse.success(settlements, "Group settlements retrieved successfully"));
    }
}
