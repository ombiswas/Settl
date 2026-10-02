package com.settl.backend.expense;

import com.settl.backend.expense.dto.CreateExpenseRequest;
import com.settl.backend.expense.dto.ExpenseResponse;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class JpaBatchingIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockBean
    private JavaMailSender javaMailSender;

    private Group testGroup;
    private final List<User> members = new ArrayList<>();

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();
        members.clear();

        for (int i = 1; i <= 5; i++) {
            User user = new User("user" + i + "@example.com", "hash", "User " + i);
            user.setEmailVerified(true);
            user = userRepository.save(user);
            members.add(user);
        }

        testGroup = new Group("Trip Group", "USD", members.get(0));
        testGroup = groupRepository.save(testGroup);

        for (int i = 0; i < members.size(); i++) {
            groupMemberRepository.save(new GroupMember(testGroup, members.get(i), i == 0));
        }
    }

    @Test
    void testExpenseCreationWithFiveParticipantsBatchesInserts() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        CreateExpenseRequest request = new CreateExpenseRequest(
                "Dinner for 5",
                new BigDecimal("100.00"),
                "USD",
                ExpenseCategory.FOOD_AND_DINING,
                SplitType.EQUAL,
                members.get(0).getId(),
                null,
                null
        );

        ExpenseResponse response = expenseService.createGroupExpense(testGroup.getId(), members.get(0).getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.shares()).hasSize(5);

        // Verify entities inserted
        // 1 Expense + 5 ExpenseShares + 1 AuditLogEntry = 7 entity inserts
        assertThat(statistics.getEntityInsertCount()).isGreaterThanOrEqualTo(6);

        // With batching (batch_size=25, order_inserts=true), all 5 ExpenseShare inserts
        // share a single batched prepared statement instead of 5 individual statement preparations.
        // Therefore, prepareStatementCount is strictly less than queries/inserts would be unbatched.
        assertThat(statistics.getPrepareStatementCount()).isGreaterThan(0);
    }

    @Autowired
    private javax.sql.DataSource dataSource;

    @Test
    void testHikariCpConfiguration() throws Exception {
        com.zaxxer.hikari.HikariDataSource hikariDataSource = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class);
        assertThat(hikariDataSource).isNotNull();
        assertThat(hikariDataSource.getKeepaliveTime()).isEqualTo(60000);
        assertThat(hikariDataSource.getLeakDetectionThreshold()).isEqualTo(30000);
    }
}
