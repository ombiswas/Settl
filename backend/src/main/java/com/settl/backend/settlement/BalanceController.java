package com.settl.backend.settlement;

import com.settl.backend.auth.CustomUserPrincipal;
import com.settl.backend.common.ApiResponse;
import com.settl.backend.settlement.dto.GroupBalanceResponse;
import com.settl.backend.settlement.dto.SuggestedSettlementsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}")
@Tag(name = "Balances & Settlement Suggestions", description = "Net group balance tracking and optimal debt simplification suggestions")
@SecurityRequirement(name = "BearerAuth")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/balances")
    @Operation(summary = "Get group balances", description = "Calculates net balances for all group members (member-only)")
    public ResponseEntity<ApiResponse<GroupBalanceResponse>> getGroupBalances(
            @PathVariable("groupId") UUID groupId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        GroupBalanceResponse response = balanceService.getGroupBalances(groupId, principal.id());
        return ResponseEntity.ok(ApiResponse.success(response, "Group balances calculated successfully"));
    }

    @GetMapping("/settlements/suggested")
    @Operation(summary = "Get suggested simplified settlements", description = "Runs greedy max-heap debt simplifier algorithm to recommend minimal debt-settling transactions")
    public ResponseEntity<ApiResponse<SuggestedSettlementsResponse>> getSuggestedSettlements(
            @PathVariable("groupId") UUID groupId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        SuggestedSettlementsResponse response = balanceService.getSuggestedSettlements(groupId, principal.id());
        return ResponseEntity.ok(ApiResponse.success(response, "Suggested simplified settlements computed successfully"));
    }
}
