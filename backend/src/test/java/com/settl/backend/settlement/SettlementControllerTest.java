package com.settl.backend.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settl.backend.audit.AuditLogRepository;
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
import com.settl.backend.settlement.dto.CreateSettlementRequest;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.TimeZone;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SettlementControllerTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private JavaMailSender javaMailSender;

    private User alice;
    private User bob;
    private Group testGroup;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
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

        testGroup = new Group("Roadtrip Group", "USD", alice);
        testGroup = groupRepository.save(testGroup);

        groupMemberRepository.save(new GroupMember(testGroup, alice, true));
        groupMemberRepository.save(new GroupMember(testGroup, bob, false));

        aliceToken = jwtService.generateAccessToken(alice);
        bobToken = jwtService.generateAccessToken(bob);

        // Initial expense: Alice pays 100.00 (split equal -> Bob owes 50.00)
        Expense exp = new Expense(testGroup, alice, "Gasoline", new BigDecimal("100.00"), "USD", ExpenseCategory.TRANSPORTATION, SplitType.EQUAL, null);
        exp.addShare(new ExpenseShare(exp, alice, new BigDecimal("50.00")));
        exp.addShare(new ExpenseShare(exp, bob, new BigDecimal("50.00")));
        expenseRepository.save(exp);
    }

    @Test
    void recordSettlementShouldReduceBalancesToZeroAndRecordAuditTrail() throws Exception {
        // 1. Verify initial balance: Bob owes 50.00
        mockMvc.perform(get("/api/groups/" + testGroup.getId() + "/balances")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balances[?(@.userId == '" + bob.getId() + "')].netBalance").value(-50.00))
                .andExpect(jsonPath("$.data.balances[?(@.userId == '" + bob.getId() + "')].status").value("OWES"));

        // 2. Bob pays Alice 50.00
        CreateSettlementRequest settlementReq = new CreateSettlementRequest(
                alice.getId(),
                new BigDecimal("50.00"),
                "USD",
                true
        );

        mockMvc.perform(post("/api/groups/" + testGroup.getId() + "/settlements")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settlementReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(50.00))
                .andExpect(jsonPath("$.data.fromUserId").value(bob.getId().toString()))
                .andExpect(jsonPath("$.data.toUserId").value(alice.getId().toString()));

        // 3. Verify updated balances: both Alice and Bob are now 0.00 and SETTLED
        mockMvc.perform(get("/api/groups/" + testGroup.getId() + "/balances")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balances[?(@.userId == '" + bob.getId() + "')].netBalance").value(0.00))
                .andExpect(jsonPath("$.data.balances[?(@.userId == '" + bob.getId() + "')].status").value("SETTLED"))
                .andExpect(jsonPath("$.data.balances[?(@.userId == '" + alice.getId() + "')].netBalance").value(0.00))
                .andExpect(jsonPath("$.data.balances[?(@.userId == '" + alice.getId() + "')].status").value("SETTLED"));

        // 4. Verify activity feed includes SETTLEMENT_RECORDED
        mockMvc.perform(get("/api/groups/" + testGroup.getId() + "/activity")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[?(@.action == 'SETTLEMENT_RECORDED')]").exists());
    }
}
