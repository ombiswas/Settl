package com.settl.backend.settlement;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class BalanceQueryCountTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private BalanceService balanceService;

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

    private Group groupWith4Members;
    private User admin4;

    private Group groupWith10Members;
    private User admin10;

    @BeforeEach
    void setUp() {
        settlementRepository.deleteAll();
        expenseShareRepository.deleteAll();
        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        // Setup Group A: 4 members
        List<User> members4 = createUsers("group4", 4);
        admin4 = members4.get(0);
        groupWith4Members = groupRepository.save(new Group("Group 4", "USD", admin4));
        for (int i = 0; i < members4.size(); i++) {
            groupMemberRepository.save(new GroupMember(groupWith4Members, members4.get(i), i == 0));
        }
        createExpensesAndSettlements(groupWith4Members, members4);

        // Setup Group B: 10 members
        List<User> members10 = createUsers("group10", 10);
        admin10 = members10.get(0);
        groupWith10Members = groupRepository.save(new Group("Group 10", "USD", admin10));
        for (int i = 0; i < members10.size(); i++) {
            groupMemberRepository.save(new GroupMember(groupWith10Members, members10.get(i), i == 0));
        }
        createExpensesAndSettlements(groupWith10Members, members10);
    }

    private List<User> createUsers(String prefix, int count) {
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            User u = new User(prefix + "_user" + i + "@example.com", "hash", prefix + " User " + i);
            u.setEmailVerified(true);
            users.add(userRepository.save(u));
        }
        return users;
    }

    private void createExpensesAndSettlements(Group group, List<User> members) {
        Expense expense = new Expense(
                group,
                members.get(0),
                "Team Lunch",
                new BigDecimal("100.00"),
                "USD",
                ExpenseCategory.FOOD_AND_DINING,
                SplitType.EQUAL,
                null
        );
        BigDecimal shareAmount = new BigDecimal("100.00").divide(new BigDecimal(members.size()), 2, java.math.RoundingMode.HALF_EVEN);
        for (User member : members) {
            expense.addShare(new ExpenseShare(expense, member, shareAmount));
        }
        expenseRepository.save(expense);

        if (members.size() >= 2) {
            settlementRepository.save(new Settlement(group, members.get(1), members.get(0), new BigDecimal("10.00"), "USD", true));
        }
    }

    @Test
    void queryCountDoesNotScaleWithMemberCount() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);

        // Measure query count for 4-member group
        statistics.clear();
        GroupBalanceResponse response4 = balanceService.getGroupBalances(groupWith4Members.getId(), admin4.getId());
        long queryCount4 = statistics.getPrepareStatementCount();
        assertThat(response4.balances()).hasSize(4);

        // Measure query count for 10-member group
        statistics.clear();
        GroupBalanceResponse response10 = balanceService.getGroupBalances(groupWith10Members.getId(), admin10.getId());
        long queryCount10 = statistics.getPrepareStatementCount();
        assertThat(response10.balances()).hasSize(10);

        // The query count must be constant O(1) and NOT grow with member count
        assertThat(queryCount10)
                .as("Query count for 10 members should equal query count for 4 members (no N+1)")
                .isEqualTo(queryCount4);
    }
}
