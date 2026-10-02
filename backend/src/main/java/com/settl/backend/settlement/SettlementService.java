package com.settl.backend.settlement;

import com.settl.backend.audit.AuditAction;
import com.settl.backend.audit.AuditService;
import com.settl.backend.common.ApiException;
import com.settl.backend.common.CurrencyValidator;
import com.settl.backend.common.PageResponse;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.settlement.dto.CreateSettlementRequest;
import com.settl.backend.settlement.dto.SettlementResponse;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final GroupBalanceCacheEvictor groupBalanceCacheEvictor;

    public SettlementService(
            SettlementRepository settlementRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            AuditService auditService,
            GroupBalanceCacheEvictor groupBalanceCacheEvictor
    ) {
        this.settlementRepository = settlementRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.groupBalanceCacheEvictor = groupBalanceCacheEvictor;
    }

    @Transactional
    public SettlementResponse recordSettlement(UUID groupId, UUID callerId, CreateSettlementRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Settlement amount must be greater than zero", "INVALID_AMOUNT");
        }
        if (request.amount().stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("Settlement amount cannot have more than 2 decimal places", "INVALID_AMOUNT");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, callerId)) {
            throw ApiException.forbidden("You must be a member of this group to record a settlement", "NOT_A_GROUP_MEMBER");
        }

        if (callerId.equals(request.toUserId())) {
            throw ApiException.badRequest("You cannot record a settlement to yourself", "CANNOT_SETTLE_WITH_SELF");
        }

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, request.toUserId())) {
            throw ApiException.badRequest("Recipient must be an active member of this group", "RECIPIENT_NOT_IN_GROUP");
        }

        User fromUser = userRepository.findById(callerId)
                .orElseThrow(() -> ApiException.notFound("Payer user not found", "USER_NOT_FOUND"));
        User toUser = userRepository.findById(request.toUserId())
                .orElseThrow(() -> ApiException.notFound("Recipient user not found", "USER_NOT_FOUND"));

        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency().trim().toUpperCase()
                : group.getDefaultCurrency();
        CurrencyValidator.validate(currency);

        boolean simplified = request.isSimplified() != null && request.isSimplified();

        Settlement settlement = new Settlement(
                group,
                fromUser,
                toUser,
                request.amount().setScale(2, RoundingMode.HALF_EVEN),
                currency,
                simplified
        );

        Settlement saved = settlementRepository.save(settlement);

        // Audit Logging
        Map<String, Object> details = new HashMap<>();
        details.put("amount", saved.getAmount().toString());
        details.put("currency", saved.getCurrency());
        details.put("fromUserId", fromUser.getId().toString());
        details.put("fromUserName", fromUser.getDisplayName());
        details.put("toUserId", toUser.getId().toString());
        details.put("toUserName", toUser.getDisplayName());
        details.put("simplified", saved.isSimplified());
        auditService.logActivity(group, fromUser, AuditAction.SETTLEMENT_RECORDED, details);

        groupBalanceCacheEvictor.evictGroupBalances(groupId);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<SettlementResponse> getGroupSettlements(UUID groupId, UUID callerId, int page, int size) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, callerId)) {
            throw ApiException.forbidden("You must be a member of this group to view settlements", "NOT_A_GROUP_MEMBER");
        }

        int clampedPage = Math.max(0, page);
        int clampedSize = Math.clamp(size, 1, 100);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize);

        Page<Settlement> settlementPage = settlementRepository.findByGroupIdOrderBySettledAtDesc(groupId, pageable);
        List<SettlementResponse> dtos = settlementPage.getContent().stream()
                .map(this::mapToResponse)
                .toList();

        return new PageResponse<>(
                dtos,
                settlementPage.getNumber(),
                settlementPage.getSize(),
                settlementPage.getTotalElements(),
                settlementPage.getTotalPages()
        );
    }

    private SettlementResponse mapToResponse(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getGroup().getId(),
                settlement.getFromUser().getId(),
                settlement.getFromUser().getDisplayName(),
                settlement.getFromUser().getEmail(),
                settlement.getToUser().getId(),
                settlement.getToUser().getDisplayName(),
                settlement.getToUser().getEmail(),
                settlement.getAmount(),
                settlement.getCurrency(),
                settlement.isSimplified(),
                settlement.getSettledAt()
        );
    }
}
