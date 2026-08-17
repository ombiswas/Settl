package com.settl.backend.settlement;

import com.settl.backend.auth.JwtService;
import com.settl.backend.expense.Expense;
import com.settl.backend.expense.ExpenseCategory;
import com.settl.backend.expense.ExpenseRepository;
import com.settl.backend.expense.ExpenseShare;
import com.settl.backend.expense.SplitType;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.TimeZone;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BalanceControllerTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

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
    private SettlementRepository settlementRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private JavaMailSender javaMailSender;

    private User alice;
    private User bob;
    private Group testGroup;
    private String aliceToken;

    @BeforeEach
    void setUp() {
        settlementRepository.deleteAll();
        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        alice = new User("alice@example.com", "hash", "Alice Smith");
        alice.setEmailVerified(true);
        alice = userRepository.save(alice);

        bob = new User("bob@example.com", "hash", "Bob Jones");
        bob.setEmailVerified(true);
        bob = userRepository.save(bob);

        testGroup = new Group("Weekend Getaway", "USD", alice);
        testGroup = groupRepository.save(testGroup);

        groupMemberRepository.save(new GroupMember(testGroup, alice, true));
        groupMemberRepository.save(new GroupMember(testGroup, bob, false));

        aliceToken = jwtService.generateAccessToken(alice);

        // Alice pays 100 for hotel; Alice owes 50, Bob owes 50
        Expense exp = new Expense(testGroup, alice, "Hotel", new BigDecimal("100.00"), "USD", ExpenseCategory.TRAVEL, SplitType.EQUAL, null);
        exp.addShare(new ExpenseShare(exp, alice, new BigDecimal("50.00")));
        exp.addShare(new ExpenseShare(exp, bob, new BigDecimal("50.00")));
        expenseRepository.save(exp);
    }

    @Test
    void getGroupBalancesShouldReturnNetBalancesAndStatuses() throws Exception {
        mockMvc.perform(get("/api/groups/" + testGroup.getId() + "/balances")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.groupId").value(testGroup.getId().toString()))
                .andExpect(jsonPath("$.data.totalGroupSpend").value(100.00))
                .andExpect(jsonPath("$.data.balances").isArray())
                .andExpect(jsonPath("$.data.balances.length()").value(2))
                .andExpect(jsonPath("$.data.balances[?(@.userId == '" + alice.getId() + "')].netBalance").value(50.00))
                .andExpect(jsonPath("$.data.balances[?(@.userId == '" + alice.getId() + "')].status").value("IS_OWED"))
                .andExpect(jsonPath("$.data.balances[?(@.userId == '" + bob.getId() + "')].netBalance").value(-50.00))
                .andExpect(jsonPath("$.data.balances[?(@.userId == '" + bob.getId() + "')].status").value("OWES"));
    }

    @Test
    void getSuggestedSettlementsShouldReturnSimplifiedPaymentInstructions() throws Exception {
        mockMvc.perform(get("/api/groups/" + testGroup.getId() + "/settlements/suggested")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionCount").value(1))
                .andExpect(jsonPath("$.data.totalSettledAmount").value(50.00))
                .andExpect(jsonPath("$.data.suggestedTransactions[0].fromUserId").value(bob.getId().toString()))
                .andExpect(jsonPath("$.data.suggestedTransactions[0].toUserId").value(alice.getId().toString()))
                .andExpect(jsonPath("$.data.suggestedTransactions[0].amount").value(50.00));
    }
}
