package com.settl.backend.settlement;

import com.settl.backend.auth.CustomUserPrincipal;
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
import com.settl.backend.settlement.dto.GroupBalanceResponse;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.datasource.password=${TEST_DB_PASSWORD:2603}"
})
class BalanceBenchmarkTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private MockMvc mockMvc;

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
    private EntityManagerFactory entityManagerFactory;

    @MockBean
    private JavaMailSender javaMailSender;

    private Group testGroup;
    private User testAdmin;
    private List<User> testMembers;

    @BeforeEach
    void setUp() {
        settlementRepository.deleteAll();
        expenseShareRepository.deleteAll();
        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        // Create 10 members
        testMembers = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            User u = new User("bench_user" + i + "@example.com", "hash", "Bench User " + i);
            u.setEmailVerified(true);
            testMembers.add(userRepository.save(u));
        }
        testAdmin = testMembers.get(0);
        testGroup = groupRepository.save(new Group("Benchmark Trip", "USD", testAdmin));

        for (int i = 0; i < testMembers.size(); i++) {
            groupMemberRepository.save(new GroupMember(testGroup, testMembers.get(i), i == 0));
        }

        // Create 50 expenses split across 10 members
        for (int e = 1; e <= 50; e++) {
            User payer = testMembers.get((e - 1) % testMembers.size());
            BigDecimal amount = BigDecimal.valueOf(20 + (e * 3));
            Expense expense = new Expense(
                    testGroup,
                    payer,
                    "Expense " + e,
                    amount,
                    "USD",
                    ExpenseCategory.FOOD_AND_DINING,
                    SplitType.EQUAL,
                    null
            );
            BigDecimal share = amount.divide(BigDecimal.valueOf(testMembers.size()), 2, RoundingMode.HALF_EVEN);
            for (User member : testMembers) {
                expense.addShare(new ExpenseShare(expense, member, share));
            }
            expenseRepository.save(expense);
        }

        // Create 15 settlements
        for (int s = 1; s <= 15; s++) {
            User payer = testMembers.get((s * 2) % testMembers.size());
            User payee = testMembers.get((s * 3) % testMembers.size());
            if (!payer.getId().equals(payee.getId())) {
                settlementRepository.save(new Settlement(
                        testGroup, payer, payee, BigDecimal.valueOf(15 + s), "USD", true
                ));
            }
        }
    }

    @Test
    @DisplayName("Benchmark balance query count and execution latency")
    void benchmarkBalanceEndpoint() throws Exception {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);

        // 1. Measure query count
        statistics.clear();
        GroupBalanceResponse response = balanceService.getGroupBalances(testGroup.getId(), testAdmin.getId());
        long queryCount = statistics.getPrepareStatementCount();
        assertThat(response.balances()).hasSize(10);

        // 2. Warm up
        for (int i = 0; i < 20; i++) {
            balanceService.getGroupBalances(testGroup.getId(), testAdmin.getId());
        }

        // 3. Measure service execution time (100 iterations)
        int iterations = 100;
        long[] serviceTimes = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            balanceService.getGroupBalances(testGroup.getId(), testAdmin.getId());
            serviceTimes[i] = System.nanoTime() - start;
        }

        Arrays.sort(serviceTimes);
        double avgServiceMs = Arrays.stream(serviceTimes).average().orElse(0) / 1_000_000.0;
        double minServiceMs = serviceTimes[0] / 1_000_000.0;
        double medianServiceMs = serviceTimes[iterations / 2] / 1_000_000.0;
        double p95ServiceMs = serviceTimes[(int) (iterations * 0.95)] / 1_000_000.0;

        // 4. Measure HTTP MockMvc endpoint time (50 iterations)
        CustomUserPrincipal principal = new CustomUserPrincipal(
                testAdmin.getId(), testAdmin.getEmail(), testAdmin.getPasswordHash()
        );

        // Warm up MockMvc
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/groups/" + testGroup.getId() + "/balances").with(user(principal)))
                    .andExpect(status().isOk());
        }

        int httpIterations = 50;
        long[] httpTimes = new long[httpIterations];
        for (int i = 0; i < httpIterations; i++) {
            long start = System.nanoTime();
            mockMvc.perform(get("/api/groups/" + testGroup.getId() + "/balances").with(user(principal)))
                    .andExpect(status().isOk());
            httpTimes[i] = System.nanoTime() - start;
        }

        Arrays.sort(httpTimes);
        double avgHttpMs = Arrays.stream(httpTimes).average().orElse(0) / 1_000_000.0;
        double minHttpMs = httpTimes[0] / 1_000_000.0;
        double medianHttpMs = httpTimes[httpIterations / 2] / 1_000_000.0;
        double p95HttpMs = httpTimes[(int) (httpIterations * 0.95)] / 1_000_000.0;

        System.out.println("=================================================================");
        System.out.println(">>> BALANCE ENDPOINT BENCHMARK RESULTS (BEFORE CACHING) <<<");
        System.out.println("Group size: 10 members, 50 expenses (with 500 shares), 15 settlements");
        System.out.println("SQL Queries prepared per request: " + queryCount);
        System.out.printf("Service Layer Latency (100 runs): avg=%.2f ms, min=%.2f ms, median=%.2f ms, p95=%.2f ms%n",
                avgServiceMs, minServiceMs, medianServiceMs, p95ServiceMs);
        System.out.printf("HTTP Endpoint Latency (50 runs):  avg=%.2f ms, min=%.2f ms, median=%.2f ms, p95=%.2f ms%n",
                avgHttpMs, minHttpMs, medianHttpMs, p95HttpMs);
        System.out.println("=================================================================");
    }
}
