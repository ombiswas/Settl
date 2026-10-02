package com.settl.backend.user;

import com.settl.backend.audit.AuditAction;
import com.settl.backend.audit.AuditService;
import com.settl.backend.auth.AuthService;
import com.settl.backend.auth.RefreshTokenRepository;
import com.settl.backend.common.ApiException;
import com.settl.backend.expense.ExpenseRepository;
import com.settl.backend.expense.ExpenseShareRepository;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.recurring.RecurringExpenseRepository;
import com.settl.backend.settlement.SettlementRepository;
import com.settl.backend.user.dto.DeleteAccountRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseShareRepository expenseShareRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private RecurringExpenseRepository recurringExpenseRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User("alice@example.com", "encodedPassword", "Alice");
        user.setId(userId);
    }

    private void mockZeroBalance(UUID groupId, UUID targetUserId) {
        when(expenseRepository.sumPaidByUserIdInGroup(groupId, targetUserId)).thenReturn(BigDecimal.ZERO);
        when(expenseShareRepository.sumOwedByUserIdInGroup(groupId, targetUserId)).thenReturn(BigDecimal.ZERO);
        when(settlementRepository.sumSettlementsPaidByUserIdInGroup(groupId, targetUserId)).thenReturn(BigDecimal.ZERO);
        when(settlementRepository.sumSettlementsReceivedByUserIdInGroup(groupId, targetUserId)).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void deleteAccount_ThrowsInvalidCredentials_WhenPasswordDoesNotMatch() {
        DeleteAccountRequest request = new DeleteAccountRequest("wrongPass", "DELETE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "encodedPassword")).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteAccount(userId, request))
                .isInstanceOf(ApiException.class)
                .matches(ex -> ((ApiException) ex).getErrorCode().equals("INVALID_CREDENTIALS"));

        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteAccount_ThrowsInvalidConfirmation_WhenConfirmationDoesNotMatch() {
        DeleteAccountRequest request = new DeleteAccountRequest("correctPass", "WRONG_KEYWORD");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPass", "encodedPassword")).thenReturn(true);

        assertThatThrownBy(() -> userService.deleteAccount(userId, request))
                .isInstanceOf(ApiException.class)
                .matches(ex -> ((ApiException) ex).getErrorCode().equals("INVALID_CONFIRMATION"));

        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteAccount_ThrowsUnsettledBalance_WhenUserOwesOrIsOwedMoney() {
        DeleteAccountRequest request = new DeleteAccountRequest("correctPass", "DELETE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPass", "encodedPassword")).thenReturn(true);

        Group group = new Group("Trip to Paris", "EUR", user);
        group.setId(UUID.randomUUID());
        GroupMember gm = new GroupMember(group, user, false);

        when(groupMemberRepository.findAllByUserIdWithGroup(userId)).thenReturn(List.of(gm));
        when(expenseRepository.sumPaidByUserIdInGroup(group.getId(), userId)).thenReturn(BigDecimal.ZERO);
        when(expenseShareRepository.sumOwedByUserIdInGroup(group.getId(), userId)).thenReturn(new BigDecimal("25.50"));
        when(settlementRepository.sumSettlementsPaidByUserIdInGroup(group.getId(), userId)).thenReturn(BigDecimal.ZERO);
        when(settlementRepository.sumSettlementsReceivedByUserIdInGroup(group.getId(), userId)).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> userService.deleteAccount(userId, request))
                .isInstanceOf(ApiException.class)
                .matches(ex -> ((ApiException) ex).getErrorCode().equals("UNSETTLED_GROUP_BALANCES"))
                .hasMessageContaining("Trip to Paris");

        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteAccount_ThrowsSoleAdminRestriction_WhenUserIsSoleAdminOfMultiMemberGroup() {
        DeleteAccountRequest request = new DeleteAccountRequest("correctPass", "DELETE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPass", "encodedPassword")).thenReturn(true);

        Group group = new Group("Apartment", "USD", user);
        group.setId(UUID.randomUUID());
        GroupMember gm = new GroupMember(group, user, true); // Admin

        when(groupMemberRepository.findAllByUserIdWithGroup(userId)).thenReturn(List.of(gm));
        mockZeroBalance(group.getId(), userId);
        when(groupMemberRepository.countMembersInGroup(group.getId())).thenReturn(3L);
        when(groupMemberRepository.countAdminsInGroup(group.getId())).thenReturn(1L);

        assertThatThrownBy(() -> userService.deleteAccount(userId, request))
                .isInstanceOf(ApiException.class)
                .matches(ex -> ((ApiException) ex).getErrorCode().equals("SOLE_ADMIN_RESTRICTION"))
                .hasMessageContaining("Apartment");

        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteAccount_Successful_HardDeleteWhenNoSharedHistory() {
        DeleteAccountRequest request = new DeleteAccountRequest("correctPass", "DELETE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPass", "encodedPassword")).thenReturn(true);

        Group group = new Group("Dinner", "USD", user);
        group.setId(UUID.randomUUID());
        GroupMember gm = new GroupMember(group, user, false);

        when(groupMemberRepository.findAllByUserIdWithGroup(userId)).thenReturn(List.of(gm));
        mockZeroBalance(group.getId(), userId);
        when(groupMemberRepository.countMembersInGroup(group.getId())).thenReturn(2L);
        when(groupMemberRepository.countAdminsInGroup(group.getId())).thenReturn(1L); // other user is admin

        when(expenseRepository.countGroupExpensesPaidByUserId(userId)).thenReturn(0L);
        when(expenseShareRepository.countGroupExpenseSharesByUserId(userId)).thenReturn(0L);
        when(settlementRepository.countSettlementsByUserId(userId)).thenReturn(0L);

        ResponseCookie cookie = userService.deleteAccount(userId, request);

        assertThat(cookie.getName()).isEqualTo(AuthService.REFRESH_COOKIE_NAME);
        assertThat(cookie.getMaxAge().getSeconds()).isZero();
        verify(groupMemberRepository).deleteByGroupIdAndUserId(group.getId(), userId);
        verify(expenseRepository).deletePersonalExpensesByUserId(userId);
        verify(recurringExpenseRepository).deleteByPaidById(userId);
        verify(refreshTokenRepository).deleteAllByUser(user);
        verify(groupMemberRepository).deleteAllByUserId(userId);
        verify(userRepository).deleteUserById(userId);
    }

    @Test
    void deleteAccount_Successful_AnonymizeWhenSharedHistoryExists() {
        DeleteAccountRequest request = new DeleteAccountRequest("correctPass", "alice@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPass", "encodedPassword")).thenReturn(true);

        when(groupMemberRepository.findAllByUserIdWithGroup(userId)).thenReturn(List.of());
        when(expenseRepository.countGroupExpensesPaidByUserId(userId)).thenReturn(5L); // Has shared expenses!

        ResponseCookie cookie = userService.deleteAccount(userId, request);

        assertThat(cookie.getName()).isEqualTo(AuthService.REFRESH_COOKIE_NAME);
        assertThat(cookie.getMaxAge().getSeconds()).isZero();
        verify(userRepository, never()).deleteUserById(any());
        verify(userRepository).save(user);

        assertThat(user.getDisplayName()).isEqualTo("Former Member");
        assertThat(user.getEmail()).contains("@deleted.settl.local");
        assertThat(user.getPasswordHash()).startsWith("DELETED_");
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void deleteAccount_DeletesSoloGroup_WhenUserIsOnlyMember() {
        DeleteAccountRequest request = new DeleteAccountRequest("correctPass", "DELETE");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correctPass", "encodedPassword")).thenReturn(true);

        Group soloGroup = new Group("Solo Project", "USD", user);
        soloGroup.setId(UUID.randomUUID());
        GroupMember gm = new GroupMember(soloGroup, user, true);

        when(groupMemberRepository.findAllByUserIdWithGroup(userId)).thenReturn(List.of(gm));
        mockZeroBalance(soloGroup.getId(), userId);
        when(groupMemberRepository.countMembersInGroup(soloGroup.getId())).thenReturn(1L);

        when(expenseRepository.countGroupExpensesPaidByUserId(userId)).thenReturn(0L);
        when(expenseShareRepository.countGroupExpenseSharesByUserId(userId)).thenReturn(0L);
        when(settlementRepository.countSettlementsByUserId(userId)).thenReturn(0L);

        userService.deleteAccount(userId, request);

        verify(groupMemberRepository).deleteAllByGroupId(soloGroup.getId());
        verify(groupRepository).deleteGroupById(soloGroup.getId());
        verify(groupMemberRepository).deleteAllByUserId(userId);
        verify(userRepository).deleteUserById(userId);
    }
}
