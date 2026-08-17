package com.settl.backend.recurring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settl.backend.auth.JwtService;
import com.settl.backend.expense.ExpenseCategory;
import com.settl.backend.expense.ExpenseRepository;
import com.settl.backend.expense.SplitType;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.recurring.dto.CreateRecurringExpenseRequest;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecurringExpenseControllerTest {

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
    private RecurringExpenseRepository recurringExpenseRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private JavaMailSender javaMailSender;

    private User alice;
    private Group testGroup;
    private String aliceToken;

    @BeforeEach
    void setUp() {
        recurringExpenseRepository.deleteAll();
        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        alice = new User("alice@example.com", "hash", "Alice Smith");
        alice.setEmailVerified(true);
        alice = userRepository.save(alice);

        testGroup = new Group("Shared Flat", "USD", alice);
        testGroup = groupRepository.save(testGroup);

        groupMemberRepository.save(new GroupMember(testGroup, alice, true));
        aliceToken = jwtService.generateAccessToken(alice);
    }

    @Test
    void createAndListRecurringExpenses() throws Exception {
        CreateRecurringExpenseRequest req = new CreateRecurringExpenseRequest(
                "Monthly Rent",
                new BigDecimal("1200.00"),
                "USD",
                ExpenseCategory.HOUSING_AND_UTILITIES,
                SplitType.EQUAL,
                RecurringFrequency.MONTHLY,
                Instant.now().plus(30, ChronoUnit.DAYS)
        );

        mockMvc.perform(post("/api/groups/" + testGroup.getId() + "/recurring-expenses")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.templateDescription").value("Monthly Rent"))
                .andExpect(jsonPath("$.data.amount").value(1200.00));

        mockMvc.perform(get("/api/groups/" + testGroup.getId() + "/recurring-expenses")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
