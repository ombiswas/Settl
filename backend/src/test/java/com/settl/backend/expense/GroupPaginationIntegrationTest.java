package com.settl.backend.expense;

import com.settl.backend.common.PageResponse;
import com.settl.backend.expense.dto.ExpenseResponse;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.settlement.Settlement;
import com.settl.backend.settlement.SettlementRepository;
import com.settl.backend.settlement.SettlementService;
import com.settl.backend.settlement.dto.SettlementResponse;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class GroupPaginationIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseShareRepository expenseShareRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private JavaMailSender javaMailSender;

    private Group testGroup;
    private User alice;
    private User bob;
    private User charlie;

    @BeforeEach
    void setUp() {
        settlementRepository.deleteAll();
        expenseShareRepository.deleteAll();
        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        alice = userRepository.save(new User("alice@test.com", "hash", "Alice"));
        bob = userRepository.save(new User("bob@test.com", "hash", "Bob"));
        charlie = userRepository.save(new User("charlie@test.com", "hash", "Charlie"));

        testGroup = groupRepository.save(new Group("Trip Group", "USD", alice));
        groupMemberRepository.save(new GroupMember(testGroup, alice, true));
        groupMemberRepository.save(new GroupMember(testGroup, bob, false));
        groupMemberRepository.save(new GroupMember(testGroup, charlie, false));
    }

    private List<Expense> createNumberedExpenses(int count) {
        List<Expense> list = new ArrayList<>();
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        for (int i = 1; i <= count; i++) {
            Expense exp = new Expense(
                    testGroup,
                    alice,
                    "Expense " + i,
                    new BigDecimal("30.00"),
                    "USD",
                    ExpenseCategory.FOOD_AND_DINING,
                    SplitType.EQUAL,
                    null
            );
            // Stagger creation times so Expense count is newest first
            setField(exp, "createdAt", base.plusSeconds(i * 60));
            Expense saved = expenseRepository.save(exp);

            expenseShareRepository.save(new ExpenseShare(saved, alice, new BigDecimal("10.00")));
            expenseShareRepository.save(new ExpenseShare(saved, bob, new BigDecimal("10.00")));
            expenseShareRepository.save(new ExpenseShare(saved, charlie, new BigDecimal("10.00")));

            list.add(saved);
        }
        return list;
    }

    private List<Settlement> createNumberedSettlements(int count) {
        List<Settlement> list = new ArrayList<>();
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        for (int i = 1; i <= count; i++) {
            Settlement s = new Settlement(
                    testGroup,
                    bob,
                    alice,
                    new BigDecimal("10.00"),
                    "USD",
                    true
            );
            setField(s, "settledAt", base.plusSeconds(i * 60));
            list.add(settlementRepository.save(s));
        }
        return list;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should return the first page of expenses and settlements with correct metadata")
    void testFirstPage() {
        createNumberedExpenses(5);
        createNumberedSettlements(5);

        PageResponse<ExpenseResponse> expensePage = expenseService.getGroupExpenses(testGroup.getId(), alice.getId(), 0, 2);
        assertThat(expensePage.page()).isEqualTo(0);
        assertThat(expensePage.size()).isEqualTo(2);
        assertThat(expensePage.totalElements()).isEqualTo(5);
        assertThat(expensePage.totalPages()).isEqualTo(3);
        assertThat(expensePage.content()).hasSize(2);
        // Newest first: Expense 5 and Expense 4
        assertThat(expensePage.content().get(0).description()).isEqualTo("Expense 5");
        assertThat(expensePage.content().get(1).description()).isEqualTo("Expense 4");

        PageResponse<SettlementResponse> settlementPage = settlementService.getGroupSettlements(testGroup.getId(), alice.getId(), 0, 2);
        assertThat(settlementPage.page()).isEqualTo(0);
        assertThat(settlementPage.size()).isEqualTo(2);
        assertThat(settlementPage.totalElements()).isEqualTo(5);
        assertThat(settlementPage.totalPages()).isEqualTo(3);
        assertThat(settlementPage.content()).hasSize(2);
    }

    @Test
    @DisplayName("Should return the second page of expenses and settlements")
    void testSecondPage() {
        createNumberedExpenses(5);
        createNumberedSettlements(5);

        PageResponse<ExpenseResponse> expensePage = expenseService.getGroupExpenses(testGroup.getId(), alice.getId(), 1, 2);
        assertThat(expensePage.page()).isEqualTo(1);
        assertThat(expensePage.size()).isEqualTo(2);
        assertThat(expensePage.totalElements()).isEqualTo(5);
        assertThat(expensePage.totalPages()).isEqualTo(3);
        assertThat(expensePage.content()).hasSize(2);
        // Middle items: Expense 3 and Expense 2
        assertThat(expensePage.content().get(0).description()).isEqualTo("Expense 3");
        assertThat(expensePage.content().get(1).description()).isEqualTo("Expense 2");

        PageResponse<SettlementResponse> settlementPage = settlementService.getGroupSettlements(testGroup.getId(), alice.getId(), 1, 2);
        assertThat(settlementPage.page()).isEqualTo(1);
        assertThat(settlementPage.size()).isEqualTo(2);
        assertThat(settlementPage.totalElements()).isEqualTo(5);
        assertThat(settlementPage.totalPages()).isEqualTo(3);
        assertThat(settlementPage.content()).hasSize(2);
    }

    @Test
    @DisplayName("Should return an empty content list for out of range page")
    void testOutOfRangePage() {
        createNumberedExpenses(5);
        createNumberedSettlements(5);

        PageResponse<ExpenseResponse> expensePage = expenseService.getGroupExpenses(testGroup.getId(), alice.getId(), 10, 2);
        assertThat(expensePage.page()).isEqualTo(10);
        assertThat(expensePage.size()).isEqualTo(2);
        assertThat(expensePage.totalElements()).isEqualTo(5);
        assertThat(expensePage.totalPages()).isEqualTo(3);
        assertThat(expensePage.content()).isEmpty();

        PageResponse<SettlementResponse> settlementPage = settlementService.getGroupSettlements(testGroup.getId(), alice.getId(), 10, 2);
        assertThat(settlementPage.page()).isEqualTo(10);
        assertThat(settlementPage.size()).isEqualTo(2);
        assertThat(settlementPage.totalElements()).isEqualTo(5);
        assertThat(settlementPage.totalPages()).isEqualTo(3);
        assertThat(settlementPage.content()).isEmpty();
    }

    @Test
    @DisplayName("Should cap page size at 100 and clamp negative values")
    void testSizeCapAndClamping() {
        createNumberedExpenses(3);

        // Size > 100 clamped to 100
        PageResponse<ExpenseResponse> largePage = expenseService.getGroupExpenses(testGroup.getId(), alice.getId(), 0, 250);
        assertThat(largePage.size()).isEqualTo(100);

        // Negative page clamped to 0, size < 1 clamped to 1
        PageResponse<ExpenseResponse> clampedPage = expenseService.getGroupExpenses(testGroup.getId(), alice.getId(), -5, -10);
        assertThat(clampedPage.page()).isEqualTo(0);
        assertThat(clampedPage.size()).isEqualTo(1);
        assertThat(clampedPage.content()).hasSize(1);

        // Settlements size cap
        PageResponse<SettlementResponse> settlementLarge = settlementService.getGroupSettlements(testGroup.getId(), alice.getId(), -2, 500);
        assertThat(settlementLarge.page()).isEqualTo(0);
        assertThat(settlementLarge.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should maintain ordering stability when multiple records have identical createdAt/settledAt timestamps")
    void testOrderingStabilityWithIdenticalCreatedAt() {
        Instant exactSameTime = Instant.parse("2026-04-01T12:00:00Z");

        // Insert 5 expenses with EXACT same createdAt timestamp
        for (int i = 1; i <= 5; i++) {
            Expense exp = new Expense(
                    testGroup,
                    alice,
                    "Identical Time Expense " + i,
                    new BigDecimal("15.00"),
                    "USD",
                    ExpenseCategory.FOOD_AND_DINING,
                    SplitType.EQUAL,
                    null
            );
            setField(exp, "createdAt", exactSameTime);
            expenseRepository.save(exp);
        }

        // Query across pages with page size 2
        PageResponse<ExpenseResponse> page0 = expenseService.getGroupExpenses(testGroup.getId(), alice.getId(), 0, 2);
        PageResponse<ExpenseResponse> page1 = expenseService.getGroupExpenses(testGroup.getId(), alice.getId(), 1, 2);
        PageResponse<ExpenseResponse> page2 = expenseService.getGroupExpenses(testGroup.getId(), alice.getId(), 2, 2);

        assertThat(page0.content()).hasSize(2);
        assertThat(page1.content()).hasSize(2);
        assertThat(page2.content()).hasSize(1);

        List<UUID> allIds = new ArrayList<>();
        page0.content().forEach(e -> allIds.add(e.id()));
        page1.content().forEach(e -> allIds.add(e.id()));
        page2.content().forEach(e -> allIds.add(e.id()));

        // Check that all 5 IDs are unique (no duplicates, no omissions due to unstable sorting across pages)
        Set<UUID> uniqueIds = Set.copyOf(allIds);
        assertThat(allIds).hasSize(5);
        assertThat(uniqueIds).hasSize(5);

        // Verify repeatability: fetching the same page again yields the exact same stable order
        PageResponse<ExpenseResponse> repeatPage0 = expenseService.getGroupExpenses(testGroup.getId(), alice.getId(), 0, 2);
        assertThat(repeatPage0.content().stream().map(ExpenseResponse::id).toList())
                .as("Subsequent fetch of page 0 returns identical records in identical order")
                .isEqualTo(page0.content().stream().map(ExpenseResponse::id).toList());
    }

    @Test
    @DisplayName("Query count should remain constant (<= 4 queries total, 3 for data loading) and not grow with shares count or page size (No N+1)")
    void testQueryCountProvingNoNPlusOneOnShares() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);

        // Create 10 expenses, each with 3 shares (30 shares total)
        createNumberedExpenses(10);

        // Clear statistics and test query count for page size 5
        statistics.clear();
        PageResponse<ExpenseResponse> page5 = expenseService.getGroupExpenses(testGroup.getId(), alice.getId(), 0, 5);
        long queriesForPage5 = statistics.getPrepareStatementCount();

        assertThat(page5.content()).hasSize(5);
        // Each expense has 3 shares populated
        for (ExpenseResponse er : page5.content()) {
            assertThat(er.shares()).hasSize(3);
            assertThat(er.shares().get(0).userDisplayName()).isNotBlank();
        }

        // Expected queries:
        // 1: membership check (existsByGroupIdAndUserId)
        // 2: expenses with joined paidBy and group
        // 3: count query
        // 4: expense_shares with joined user in :expenseIds
        assertThat(queriesForPage5)
                .as("Total queries for page should be at most 4 (membership check + count + expenses + batch shares)")
                .isLessThanOrEqualTo(4);

        // Clear statistics and test query count for page size 10 (double the elements)
        statistics.clear();
        PageResponse<ExpenseResponse> page10 = expenseService.getGroupExpenses(testGroup.getId(), alice.getId(), 0, 10);
        long queriesForPage10 = statistics.getPrepareStatementCount();

        assertThat(page10.content()).hasSize(10);
        assertThat(queriesForPage10)
                .as("Query count for page size 10 should not grow with page size (no N+1)")
                .isLessThanOrEqualTo(4);
    }
}
