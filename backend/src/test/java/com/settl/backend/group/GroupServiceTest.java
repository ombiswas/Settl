package com.settl.backend.group;

import com.settl.backend.common.ApiException;
import com.settl.backend.expense.ExpenseRepository;
import com.settl.backend.expense.ExpenseShareRepository;
import com.settl.backend.group.dto.AddMemberRequest;
import com.settl.backend.group.dto.AddMemberResponse;
import com.settl.backend.group.dto.CreateGroupRequest;
import com.settl.backend.group.dto.GroupResponse;
import com.settl.backend.settlement.SettlementRepository;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseShareRepository expenseShareRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private com.settl.backend.audit.AuditService auditService;

    @InjectMocks
    private GroupService groupService;

    private User user1;
    private User user2;
    private Group group;

    @BeforeEach
    void setUp() {
        user1 = new User("alice@example.com", "hash", "Alice");
        user1.setId(UUID.randomUUID());

        user2 = new User("bob@example.com", "hash", "Bob");
        user2.setId(UUID.randomUUID());

        group = new Group("Trip to Paris", "EUR", user1);
        group.setId(UUID.randomUUID());
    }

    @Test
    void createGroupShouldMakeCreatorAdmin() {
        CreateGroupRequest request = new CreateGroupRequest("Apartment", "USD");
        when(userRepository.findById(user1.getId())).thenReturn(Optional.of(user1));
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
            Group g = invocation.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });

        GroupResponse response = groupService.createGroup(request, user1.getId());

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Apartment");
        assertThat(response.defaultCurrency()).isEqualTo("USD");
        assertThat(response.members()).hasSize(1);
        assertThat(response.members().get(0).isAdmin()).isTrue();

        ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().isAdmin()).isTrue();
    }

    @Test
    void createGroupWithInvalidCurrencyShouldThrowException() {
        CreateGroupRequest request = new CreateGroupRequest("Apartment", "INVALID");

        assertThatThrownBy(() -> groupService.createGroup(request, user1.getId()))
                .isInstanceOf(ApiException.class)
                .matches(ex -> ((ApiException) ex).getErrorCode().equals("INVALID_CURRENCY"));

        verify(groupRepository, never()).save(any());
    }

    @Test
    void nonMemberViewingGroupShouldThrowForbidden() {
        when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId(group.getId(), user2.getId())).thenReturn(false);

        assertThatThrownBy(() -> groupService.getGroupDetails(group.getId(), user2.getId()))
                .isInstanceOf(ApiException.class)
                .matches(ex -> ((ApiException) ex).getErrorCode().equals("NOT_A_GROUP_MEMBER"));
    }

    @Test
    void nonAdminAddingMemberShouldThrowForbidden() {
        AddMemberRequest request = new AddMemberRequest("charlie@example.com", false);
        GroupMember nonAdminMember = new GroupMember(group, user2, false);

        when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(group.getId(), user2.getId())).thenReturn(Optional.of(nonAdminMember));

        assertThatThrownBy(() -> groupService.addMember(group.getId(), request, user2.getId()))
                .isInstanceOf(ApiException.class)
                .matches(ex -> ((ApiException) ex).getErrorCode().equals("ONLY_ADMIN_CAN_ADD_MEMBERS"));

        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void adminAddingExistingUserShouldSucceed() {
        AddMemberRequest request = new AddMemberRequest("bob@example.com", false);
        GroupMember adminMember = new GroupMember(group, user1, true);

        when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(group.getId(), user1.getId())).thenReturn(Optional.of(adminMember));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user2));
        when(groupMemberRepository.existsByGroupIdAndUserId(group.getId(), user2.getId())).thenReturn(false);

        AddMemberResponse response = groupService.addMember(group.getId(), request, user1.getId());

        assertThat(response.isExistingUser()).isTrue();
        assertThat(response.userId()).isEqualTo(user2.getId());
        verify(groupMemberRepository).save(any(GroupMember.class));
    }

    @Test
    void adminAddingUnregisteredUserShouldDispatchInvitation() {
        AddMemberRequest request = new AddMemberRequest("newuser@example.com", false);
        GroupMember adminMember = new GroupMember(group, user1, true);

        when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(group.getId(), user1.getId())).thenReturn(Optional.of(adminMember));
        when(userRepository.findByEmail("newuser@example.com")).thenReturn(Optional.empty());

        AddMemberResponse response = groupService.addMember(group.getId(), request, user1.getId());

        assertThat(response.isExistingUser()).isFalse();
        assertThat(response.email()).isEqualTo("newuser@example.com");
        assertThat(response.message()).contains("Invitation created");
        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void removeMemberWithNonZeroBalanceShouldThrowUnsettledBalance() {
        GroupMember adminMember = new GroupMember(group, user1, true);
        GroupMember targetMember = new GroupMember(group, user2, false);

        when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(group.getId(), user1.getId())).thenReturn(Optional.of(adminMember));
        when(groupMemberRepository.findByGroupIdAndUserId(group.getId(), user2.getId())).thenReturn(Optional.of(targetMember));

        // User2 owes 50.00 EUR
        when(expenseRepository.sumPaidByUserIdInGroup(group.getId(), user2.getId())).thenReturn(BigDecimal.ZERO);
        when(expenseShareRepository.sumOwedByUserIdInGroup(group.getId(), user2.getId())).thenReturn(new BigDecimal("50.00"));
        when(settlementRepository.sumSettlementsPaidByUserIdInGroup(group.getId(), user2.getId())).thenReturn(BigDecimal.ZERO);
        when(settlementRepository.sumSettlementsReceivedByUserIdInGroup(group.getId(), user2.getId())).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> groupService.removeMember(group.getId(), user2.getId(), user1.getId()))
                .isInstanceOf(ApiException.class)
                .matches(ex -> ((ApiException) ex).getErrorCode().equals("UNSETTLED_BALANCE"));

        verify(groupMemberRepository, never()).deleteByGroupIdAndUserId(any(), any());
    }

    @Test
    void removeMemberWithZeroBalanceShouldSucceed() {
        GroupMember adminMember = new GroupMember(group, user1, true);
        GroupMember targetMember = new GroupMember(group, user2, false);

        when(groupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(group.getId(), user1.getId())).thenReturn(Optional.of(adminMember));
        when(groupMemberRepository.findByGroupIdAndUserId(group.getId(), user2.getId())).thenReturn(Optional.of(targetMember));

        when(expenseRepository.sumPaidByUserIdInGroup(group.getId(), user2.getId())).thenReturn(new BigDecimal("100.00"));
        when(expenseShareRepository.sumOwedByUserIdInGroup(group.getId(), user2.getId())).thenReturn(new BigDecimal("100.00"));
        when(settlementRepository.sumSettlementsPaidByUserIdInGroup(group.getId(), user2.getId())).thenReturn(BigDecimal.ZERO);
        when(settlementRepository.sumSettlementsReceivedByUserIdInGroup(group.getId(), user2.getId())).thenReturn(BigDecimal.ZERO);

        groupService.removeMember(group.getId(), user2.getId(), user1.getId());

        verify(groupMemberRepository).deleteByGroupIdAndUserId(eq(group.getId()), eq(user2.getId()));
    }
}
