package com.settl.backend.settlement;

import com.settl.backend.common.ApiException;
import com.settl.backend.expense.Expense;
import com.settl.backend.expense.ExpenseCategory;
import com.settl.backend.expense.ExpenseRepository;
import com.settl.backend.expense.ExpenseShare;
import com.settl.backend.expense.ExpenseShareRepository;
import com.settl.backend.expense.SplitType;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.group.GroupService;
import com.settl.backend.settlement.dto.GroupBalanceResponse;
import com.settl.backend.settlement.dto.UserBalanceDto;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class BalanceCharacterizationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseShareRepository expenseShareRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private GroupBalanceCacheEvictor groupBalanceCacheEvictor;

    @MockBean
    private JavaMailSender javaMailSender;

    private User alice;
    private User bob;
    private User charlie;
    private User dave;
    private User eve;
    private Group testGroup;

    @BeforeEach
    void setUp() {
        settlementRepository.deleteAll();
        expenseShareRepository.deleteAll();
        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        alice = userRepository.save(new User("alice@example.com", "hash", "Alice"));
        bob = userRepository.save(new User("bob@example.com", "hash", "Bob"));
        charlie = userRepository.save(new User("charlie@example.com", "hash", "Charlie"));
        dave = userRepository.save(new User("dave@example.com", "hash", "Dave"));
        eve = userRepository.save(new User("eve@example.com", "hash", "Eve"));

        testGroup = groupRepository.save(new Group("Weekend Trip", "USD", alice));

        groupMemberRepository.save(new GroupMember(testGroup, alice, true));
        groupMemberRepository.save(new GroupMember(testGroup, bob, false));
        groupMemberRepository.save(new GroupMember(testGroup, charlie, false));
        groupMemberRepository.save(new GroupMember(testGroup, dave, false));
        groupMemberRepository.save(new GroupMember(testGroup, eve, false));
    }

    @Test
    void testCharacterizationOfGroupBalancesAndGroupDeletion() {
        // 1. Expense 1 (EQUAL split): Alice pays 100.00 split among Alice, Bob, Charlie, Dave (25.00 each)
        Expense expense1 = new Expense(
                testGroup,
                alice,
                "Groceries",
                new BigDecimal("100.00"),
                "USD",
                ExpenseCategory.FOOD_AND_DINING,
                SplitType.EQUAL,
                null
        );
        expense1.addShare(new ExpenseShare(expense1, alice, new BigDecimal("25.00")));
        expense1.addShare(new ExpenseShare(expense1, bob, new BigDecimal("25.00")));
        expense1.addShare(new ExpenseShare(expense1, charlie, new BigDecimal("25.00")));
        expense1.addShare(new ExpenseShare(expense1, dave, new BigDecimal("25.00")));
        expenseRepository.save(expense1);

        // 2. Expense 2 (PERCENTAGE split): Bob pays 200.00: Alice 50% (100.00), Bob 25% (50.00), Charlie 25% (50.00), Dave 0% (0.00)
        Expense expense2 = new Expense(
                testGroup,
                bob,
                "Rental Car",
                new BigDecimal("200.00"),
                "USD",
                ExpenseCategory.TRANSPORTATION,
                SplitType.PERCENTAGE,
                null
        );
        expense2.addShare(new ExpenseShare(expense2, alice, new BigDecimal("100.00")));
        expense2.addShare(new ExpenseShare(expense2, bob, new BigDecimal("50.00")));
        expense2.addShare(new ExpenseShare(expense2, charlie, new BigDecimal("50.00")));
        expense2.addShare(new ExpenseShare(expense2, dave, new BigDecimal("0.00")));
        expenseRepository.save(expense2);

        // 3. Settlement 1: Charlie pays Bob 30.00
        settlementRepository.save(new Settlement(testGroup, charlie, bob, new BigDecimal("30.00"), "USD", true));

        // 4. Settlement 2: Dave pays Alice 15.00
        settlementRepository.save(new Settlement(testGroup, dave, alice, new BigDecimal("15.00"), "USD", true));

        // 5. Eve has NO activity (0 paid, 0 owed, 0 settlements)

        // --- Verify getGroupBalances outputs ---
        GroupBalanceResponse balanceResponse = balanceService.getGroupBalances(testGroup.getId(), alice.getId());

        assertThat(balanceResponse.totalGroupSpend()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(balanceResponse.balances()).hasSize(5);

        Map<UUID, UserBalanceDto> balancesByUser = balanceResponse.balances().stream()
                .collect(Collectors.toMap(UserBalanceDto::userId, b -> b));

        // Alice: Paid 100.00, Owed 125.00, SettledPaid 0.00, SettledReceived 15.00 -> Net = -40.00 (OWES)
        UserBalanceDto aliceBal = balancesByUser.get(alice.getId());
        assertThat(aliceBal.totalPaid()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(aliceBal.totalShare()).isEqualByComparingTo(new BigDecimal("125.00"));
        assertThat(aliceBal.netBalance()).isEqualByComparingTo(new BigDecimal("-40.00"));
        assertThat(aliceBal.status()).isEqualTo("OWES");

        // Bob: Paid 200.00, Owed 75.00, SettledPaid 0.00, SettledReceived 30.00 -> Net = +95.00 (IS_OWED)
        UserBalanceDto bobBal = balancesByUser.get(bob.getId());
        assertThat(bobBal.totalPaid()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(bobBal.totalShare()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(bobBal.netBalance()).isEqualByComparingTo(new BigDecimal("95.00"));
        assertThat(bobBal.status()).isEqualTo("IS_OWED");

        // Charlie: Paid 0.00, Owed 75.00, SettledPaid 30.00, SettledReceived 0.00 -> Net = -45.00 (OWES)
        UserBalanceDto charlieBal = balancesByUser.get(charlie.getId());
        assertThat(charlieBal.totalPaid()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(charlieBal.totalShare()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(charlieBal.netBalance()).isEqualByComparingTo(new BigDecimal("-45.00"));
        assertThat(charlieBal.status()).isEqualTo("OWES");

        // Dave: Paid 0.00, Owed 25.00, SettledPaid 15.00, SettledReceived 0.00 -> Net = -10.00 (OWES)
        UserBalanceDto daveBal = balancesByUser.get(dave.getId());
        assertThat(daveBal.totalPaid()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(daveBal.totalShare()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(daveBal.netBalance()).isEqualByComparingTo(new BigDecimal("-10.00"));
        assertThat(daveBal.status()).isEqualTo("OWES");

        // Eve: No activity -> Net = 0.00 (SETTLED)
        UserBalanceDto eveBal = balancesByUser.get(eve.getId());
        assertThat(eveBal.totalPaid()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(eveBal.totalShare()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(eveBal.netBalance()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(eveBal.status()).isEqualTo("SETTLED");

        // --- Verify deleteGroup balance check ---
        assertThatThrownBy(() -> groupService.deleteGroup(testGroup.getId(), alice.getId()))
                .isInstanceOf(ApiException.class)
                .matches(ex -> ((ApiException) ex).getErrorCode().equals("UNSETTLED_GROUP_BALANCES"));

        // Fully settle all remaining debts
        settlementRepository.save(new Settlement(testGroup, alice, bob, new BigDecimal("40.00"), "USD", true));
        settlementRepository.save(new Settlement(testGroup, charlie, bob, new BigDecimal("45.00"), "USD", true));
        settlementRepository.save(new Settlement(testGroup, dave, bob, new BigDecimal("10.00"), "USD", true));
        groupBalanceCacheEvictor.evictGroupBalances(testGroup.getId());

        // Verify all balances are now settled (0.00)
        GroupBalanceResponse settledResponse = balanceService.getGroupBalances(testGroup.getId(), alice.getId());
        for (UserBalanceDto b : settledResponse.balances()) {
            assertThat(b.netBalance().setScale(2, RoundingMode.HALF_EVEN)).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(b.status()).isEqualTo("SETTLED");
        }

        // Group deletion should now succeed
        groupService.deleteGroup(testGroup.getId(), alice.getId());
        assertThat(groupRepository.findById(testGroup.getId())).isEmpty();
    }
}
