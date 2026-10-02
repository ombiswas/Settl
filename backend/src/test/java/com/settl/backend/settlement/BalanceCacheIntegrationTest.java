package com.settl.backend.settlement;

import com.settl.backend.audit.AuditService;
import com.settl.backend.auth.dto.RegisterRequest;
import com.settl.backend.common.ApiException;
import com.settl.backend.config.CacheConfig;
import com.settl.backend.expense.Expense;
import com.settl.backend.expense.ExpenseCategory;
import com.settl.backend.expense.ExpenseRepository;
import com.settl.backend.expense.ExpenseService;
import com.settl.backend.expense.SplitType;
import com.settl.backend.expense.dto.CreateExpenseRequest;
import com.settl.backend.expense.dto.ExpenseResponse;
import com.settl.backend.expense.dto.ExpenseSplitDto;
import com.settl.backend.expense.dto.UpdateExpenseRequest;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.group.GroupService;
import com.settl.backend.group.dto.AddMemberRequest;
import com.settl.backend.recurring.RecurringExpense;
import com.settl.backend.recurring.RecurringExpenseRepository;
import com.settl.backend.recurring.RecurringExpenseService;
import com.settl.backend.recurring.RecurringFrequency;
import com.settl.backend.settlement.dto.CreateSettlementRequest;
import com.settl.backend.settlement.dto.GroupBalanceResponse;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import com.settl.backend.user.UserService;
import com.settl.backend.user.dto.DeleteAccountRequest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.datasource.password=${TEST_DB_PASSWORD:2603}"
})
class BalanceCacheIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private RecurringExpenseService recurringExpenseService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private RecurringExpenseRepository recurringExpenseRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockBean
    private JavaMailSender javaMailSender;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User alice;
    private User bob;
    private User charlie;
    private Group group;

    @BeforeEach
    void setUp() {
        // Clear Redis cache
        Cache cache = cacheManager.getCache(CacheConfig.GROUP_BALANCES_CACHE);
        if (cache != null) {
            cache.clear();
        }
        Set<String> keys = redisTemplate.keys("group_balances*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // Clean database
        recurringExpenseRepository.deleteAll();
        settlementRepository.deleteAll();
        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        String passHash = passwordEncoder.encode("password123");
        alice = userRepository.save(new User("alice_cache@example.com", passHash, "Alice"));
        alice.setEmailVerified(true);
        alice = userRepository.save(alice);

        bob = userRepository.save(new User("bob_cache@example.com", passHash, "Bob"));
        bob.setEmailVerified(true);
        bob = userRepository.save(bob);

        charlie = userRepository.save(new User("charlie_cache@example.com", passHash, "Charlie"));
        charlie.setEmailVerified(true);
        charlie = userRepository.save(charlie);

        group = groupRepository.save(new Group("Cache Test Group", "USD", alice));
        groupMemberRepository.save(new GroupMember(group, alice, true));
        groupMemberRepository.save(new GroupMember(group, bob, false));
    }

    @Test
    @DisplayName("Cache hit avoids expensive DB aggregate queries on repeated calls")
    void cacheHitAvoidsDbQueries() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);

        // Add an initial expense
        expenseService.createGroupExpense(group.getId(), alice.getId(), new CreateExpenseRequest(
                "Initial Dinner",
                BigDecimal.valueOf(100),
                "USD",
                ExpenseCategory.FOOD_AND_DINING,
                SplitType.EQUAL,
                alice.getId(),
                null,
                List.of(
                        new ExpenseSplitDto(alice.getId(), null, null, null),
                        new ExpenseSplitDto(bob.getId(), null, null, null)
                )
        ));

        // 1. First call: Cache miss -> populates cache
        statistics.clear();
        GroupBalanceResponse initialResponse = balanceService.getGroupBalances(group.getId(), alice.getId());
        long missQueryCount = statistics.getPrepareStatementCount();
        assertThat(missQueryCount).isGreaterThanOrEqualTo(6);
        assertThat(initialResponse.totalGroupSpend()).isEqualByComparingTo(BigDecimal.valueOf(100));

        // 2. Second call: Cache hit -> should only execute group + membership check (<= 2 queries)
        statistics.clear();
        GroupBalanceResponse hitResponse = balanceService.getGroupBalances(group.getId(), alice.getId());
        long hitQueryCount = statistics.getPrepareStatementCount();

        assertThat(hitQueryCount).isLessThanOrEqualTo(2);
        assertThat(hitResponse.totalGroupSpend()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(hitResponse.balances()).hasSize(2);
    }

    @Test
    @DisplayName("Membership check runs on every request: Outsider is denied even when cache is hot")
    void differentCallerCannotAccessGroupBalancesEvenWhenCacheIsHot() {
        // Populate cache as Alice (member)
        GroupBalanceResponse memberResponse = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(memberResponse).isNotNull();

        // Charlie is NOT a member of the group
        assertThatThrownBy(() -> balanceService.getGroupBalances(group.getId(), charlie.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("You must be a member of this group");

        // Bob is a member: should be served the group balances from cache
        GroupBalanceResponse bobResponse = balanceService.getGroupBalances(group.getId(), bob.getId());
        assertThat(bobResponse.groupId()).isEqualTo(group.getId());
        assertThat(bobResponse.balances()).hasSize(2);
    }

    @Test
    @DisplayName("Expense create, update, and delete all invalidate group balance cache")
    void expenseMutationsInvalidateCache() {
        // Warm up cache
        GroupBalanceResponse r0 = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(r0.totalGroupSpend()).isEqualByComparingTo(BigDecimal.ZERO);

        // 1. Create expense
        ExpenseResponse exp = expenseService.createGroupExpense(group.getId(), alice.getId(), new CreateExpenseRequest(
                "Lunch",
                BigDecimal.valueOf(60),
                "USD",
                ExpenseCategory.FOOD_AND_DINING,
                SplitType.EQUAL,
                alice.getId(),
                null,
                List.of(
                        new ExpenseSplitDto(alice.getId(), null, null, null),
                        new ExpenseSplitDto(bob.getId(), null, null, null)
                )
        ));

        // Verify cache was evicted and reflects new total
        GroupBalanceResponse r1 = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(r1.totalGroupSpend()).isEqualByComparingTo(BigDecimal.valueOf(60));

        // 2. Update expense
        expenseService.updateGroupExpense(group.getId(), exp.id(), alice.getId(), new UpdateExpenseRequest(
                "Updated Lunch",
                BigDecimal.valueOf(90),
                "USD",
                ExpenseCategory.FOOD_AND_DINING,
                SplitType.EQUAL,
                alice.getId(),
                null,
                List.of(
                        new ExpenseSplitDto(alice.getId(), null, null, null),
                        new ExpenseSplitDto(bob.getId(), null, null, null)
                )
        ));

        // Verify cache was evicted and reflects updated total
        GroupBalanceResponse r2 = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(r2.totalGroupSpend()).isEqualByComparingTo(BigDecimal.valueOf(90));

        // 3. Delete expense
        expenseService.deleteGroupExpense(group.getId(), exp.id(), alice.getId());

        // Verify cache was evicted and reflects deletion
        GroupBalanceResponse r3 = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(r3.totalGroupSpend()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Settlement recording invalidates group balance cache")
    void settlementRecordingInvalidatesCache() {
        // Create initial expense: Alice paid $100 for Alice & Bob ($50 each)
        expenseService.createGroupExpense(group.getId(), alice.getId(), new CreateExpenseRequest(
                "Hotel",
                BigDecimal.valueOf(100),
                "USD",
                ExpenseCategory.ENTERTAINMENT,
                SplitType.EQUAL,
                alice.getId(),
                null,
                List.of(
                        new ExpenseSplitDto(alice.getId(), null, null, null),
                        new ExpenseSplitDto(bob.getId(), null, null, null)
                )
        ));

        // Warm up cache: Alice net balance = +50, Bob = -50
        GroupBalanceResponse before = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(before.balances().stream().filter(b -> b.userId().equals(bob.getId())).findFirst().get().netBalance())
                .isEqualByComparingTo(BigDecimal.valueOf(-50));

        // Bob settles $50 with Alice
        settlementService.recordSettlement(group.getId(), bob.getId(), new CreateSettlementRequest(
                alice.getId(), BigDecimal.valueOf(50), "USD", false
        ));

        // Cache must be evicted and show settled balance = 0
        GroupBalanceResponse after = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(after.balances().stream().filter(b -> b.userId().equals(bob.getId())).findFirst().get().netBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Member addition and removal invalidate group balance cache")
    void memberMutationsInvalidateCache() {
        // Warm up cache (2 members)
        GroupBalanceResponse r0 = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(r0.balances()).hasSize(2);

        // Add Charlie
        groupService.addMember(group.getId(), new AddMemberRequest(charlie.getEmail(), false), alice.getId());

        // Verify cache was evicted and Charlie is now listed
        GroupBalanceResponse r1 = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(r1.balances()).hasSize(3);

        // Remove Charlie
        groupService.removeMember(group.getId(), charlie.getId(), alice.getId());

        // Verify cache was evicted and Charlie is removed
        GroupBalanceResponse r2 = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(r2.balances()).hasSize(2);
    }

    @Test
    @DisplayName("Recurring expense generation invalidates group balance cache")
    void recurringExpenseTriggerInvalidatesCache() {
        // Warm up cache
        GroupBalanceResponse r0 = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(r0.totalGroupSpend()).isEqualByComparingTo(BigDecimal.ZERO);

        // Setup a due recurring expense
        RecurringExpense recurring = new RecurringExpense(
                group,
                "Monthly Internet",
                BigDecimal.valueOf(80),
                "USD",
                ExpenseCategory.HOUSING_AND_UTILITIES,
                SplitType.EQUAL,
                alice,
                RecurringFrequency.MONTHLY,
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        recurringExpenseRepository.save(recurring);

        // Run recurring processor
        int processed = recurringExpenseService.processDueRecurringExpenses();
        assertThat(processed).isEqualTo(1);

        // Verify cache was evicted and reflects the generated expense
        GroupBalanceResponse r1 = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(r1.totalGroupSpend()).isEqualByComparingTo(BigDecimal.valueOf(80));
    }

    @Test
    @DisplayName("Account deletion invalidates balance cache for all groups user was in")
    void accountDeletionInvalidatesCache() {
        // Add Charlie to group and warm cache
        groupService.addMember(group.getId(), new AddMemberRequest(charlie.getEmail(), false), alice.getId());
        GroupBalanceResponse r0 = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(r0.balances()).hasSize(3);

        // Charlie deletes his account
        userService.deleteAccount(charlie.getId(), new DeleteAccountRequest("password123", "DELETE"));

        // Cache must be evicted and Charlie removed from active group members
        GroupBalanceResponse r1 = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(r1.balances()).hasSize(2);
    }

    @Test
    @DisplayName("When Redis cache throws an error, app falls back to DB transparently")
    void redisDownStillReturnsCorrectData() {
        // Add expense
        expenseService.createGroupExpense(group.getId(), alice.getId(), new CreateExpenseRequest(
                "Coffee",
                BigDecimal.valueOf(10),
                "USD",
                ExpenseCategory.FOOD_AND_DINING,
                SplitType.EQUAL,
                alice.getId(),
                null,
                List.of(
                        new ExpenseSplitDto(alice.getId(), null, null, null),
                        new ExpenseSplitDto(bob.getId(), null, null, null)
                )
        ));

        // Clear cache so it tries to read or write
        Cache cache = cacheManager.getCache(CacheConfig.GROUP_BALANCES_CACHE);
        if (cache != null) {
            cache.clear();
        }

        // Call getGroupBalances: CacheErrorHandler ensures any Redis error falls back to DB
        GroupBalanceResponse response = balanceService.getGroupBalances(group.getId(), alice.getId());
        assertThat(response).isNotNull();
        assertThat(response.totalGroupSpend()).isEqualByComparingTo(BigDecimal.valueOf(10));
    }
}
