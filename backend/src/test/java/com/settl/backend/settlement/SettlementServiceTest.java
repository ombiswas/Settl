package com.settl.backend.settlement;

import com.settl.backend.audit.AuditService;
import com.settl.backend.common.ApiException;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.settlement.dto.CreateSettlementRequest;
import com.settl.backend.settlement.dto.SettlementResponse;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private SettlementService settlementService;

    private Group testGroup;
    private User alice;
    private User bob;
    private UUID groupId;
    private UUID aliceId;
    private UUID bobId;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(
                settlementRepository,
                groupRepository,
                groupMemberRepository,
                userRepository,
                auditService
        );

        aliceId = UUID.randomUUID();
        alice = new User("alice@example.com", "hash", "Alice");
        alice.setId(aliceId);

        bobId = UUID.randomUUID();
        bob = new User("bob@example.com", "hash", "Bob");
        bob.setId(bobId);

        groupId = UUID.randomUUID();
        testGroup = new Group("Trip", "USD", alice);
        testGroup.setId(groupId);
    }

    @Test
    void recordSettlementShouldSucceedForValidMembers() {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, bobId)).thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, aliceId)).thenReturn(true);
        when(userRepository.findById(bobId)).thenReturn(Optional.of(bob));
        when(userRepository.findById(aliceId)).thenReturn(Optional.of(alice));

        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> {
            Settlement s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CreateSettlementRequest request = new CreateSettlementRequest(
                aliceId,
                new BigDecimal("50.00"),
                "USD",
                true
        );

        SettlementResponse response = settlementService.recordSettlement(groupId, bobId, request);

        assertThat(response).isNotNull();
        assertThat(response.fromUserId()).isEqualTo(bobId);
        assertThat(response.toUserId()).isEqualTo(aliceId);
        assertThat(response.amount()).isEqualTo(new BigDecimal("50.00"));
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.simplified()).isTrue();
    }

    @Test
    void recordSettlementToSelfShouldThrowBadRequest() {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, aliceId)).thenReturn(true);

        CreateSettlementRequest request = new CreateSettlementRequest(
                aliceId,
                new BigDecimal("50.00"),
                "USD",
                false
        );

        assertThatThrownBy(() -> settlementService.recordSettlement(groupId, aliceId, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("You cannot record a settlement to yourself");
    }

    @Test
    void recordSettlementToNonMemberShouldThrowBadRequest() {
        UUID outsiderId = UUID.randomUUID();
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, aliceId)).thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, outsiderId)).thenReturn(false);

        CreateSettlementRequest request = new CreateSettlementRequest(
                outsiderId,
                new BigDecimal("50.00"),
                "USD",
                false
        );

        assertThatThrownBy(() -> settlementService.recordSettlement(groupId, aliceId, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Recipient must be an active member");
    }
}
