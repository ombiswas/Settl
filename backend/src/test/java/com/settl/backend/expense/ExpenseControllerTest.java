package com.settl.backend.expense;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settl.backend.auth.JwtService;
import com.settl.backend.expense.dto.CreateExpenseRequest;
import com.settl.backend.expense.dto.CreatePersonalExpenseRequest;
import com.settl.backend.expense.dto.ExpenseSplitDto;
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
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.TimeZone;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExpenseControllerTest {

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
    private JwtService jwtService;

    @MockBean
    private JavaMailSender javaMailSender;

    private User adminUser;
    private User memberUser;
    private Group testGroup;
    private String adminToken;
    private String memberToken;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = new User("admin@example.com", "hash", "Admin User");
        adminUser.setEmailVerified(true);
        adminUser = userRepository.save(adminUser);

        memberUser = new User("member@example.com", "hash", "Member User");
        memberUser.setEmailVerified(true);
        memberUser = userRepository.save(memberUser);

        testGroup = new Group("Roadtrip Group", "USD", adminUser);
        testGroup = groupRepository.save(testGroup);

        groupMemberRepository.save(new GroupMember(testGroup, adminUser, true));
        groupMemberRepository.save(new GroupMember(testGroup, memberUser, false));

        adminToken = jwtService.generateAccessToken(adminUser);
        memberToken = jwtService.generateAccessToken(memberUser);
    }

    @Test
    void createGroupExpenseWithExactSplitShouldSucceed() throws Exception {
        CreateExpenseRequest request = new CreateExpenseRequest(
                "Gas Station",
                new BigDecimal("60.00"),
                "USD",
                ExpenseCategory.TRANSPORTATION,
                SplitType.EXACT,
                adminUser.getId(),
                null,
                List.of(
                        new ExpenseSplitDto(adminUser.getId(), new BigDecimal("35.00"), null, null),
                        new ExpenseSplitDto(memberUser.getId(), new BigDecimal("25.00"), null, null)
                )
        );

        mockMvc.perform(post("/api/groups/" + testGroup.getId() + "/expenses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.description").value("Gas Station"))
                .andExpect(jsonPath("$.data.amount").value(60.00))
                .andExpect(jsonPath("$.data.shares").isArray())
                .andExpect(jsonPath("$.data.shares.length()").value(2));
    }

    @Test
    void listGroupExpensesShouldReturnAllExpenses() throws Exception {
        CreateExpenseRequest request = new CreateExpenseRequest(
                "Groceries",
                new BigDecimal("100.00"),
                "USD",
                ExpenseCategory.FOOD_AND_DINING,
                SplitType.EQUAL,
                adminUser.getId(),
                null,
                null
        );

        mockMvc.perform(post("/api/groups/" + testGroup.getId() + "/expenses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/groups/" + testGroup.getId() + "/expenses")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void personalExpenseLifecycleAndAnalytics() throws Exception {
        CreatePersonalExpenseRequest req1 = new CreatePersonalExpenseRequest(
                "Morning Coffee",
                new BigDecimal("5.00"),
                "USD",
                ExpenseCategory.FOOD_AND_DINING,
                null
        );

        CreatePersonalExpenseRequest req2 = new CreatePersonalExpenseRequest(
                "Bus Fare",
                new BigDecimal("15.00"),
                "USD",
                ExpenseCategory.TRANSPORTATION,
                null
        );

        // 1. Create personal expenses
        mockMvc.perform(post("/api/expenses/personal")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.description").value("Morning Coffee"));

        mockMvc.perform(post("/api/expenses/personal")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        // 2. Query personal expenses
        mockMvc.perform(get("/api/expenses/personal")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 3. Category filter
        mockMvc.perform(get("/api/expenses/personal")
                        .param("category", "FOOD_AND_DINING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].description").value("Morning Coffee"));

        // 4. Spending analytics
        mockMvc.perform(get("/api/expenses/personal/analytics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSpent").value(20.00))
                .andExpect(jsonPath("$.data.totalExpenseCount").value(2))
                .andExpect(jsonPath("$.data.categoryBreakdown").isArray());
    }

    @Test
    void getCategoriesShouldReturnList() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.code == 'FOOD_AND_DINING')]").exists());
    }
}
