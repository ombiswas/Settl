package com.settl.backend.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settl.backend.auth.JwtService;
import com.settl.backend.group.dto.AddMemberRequest;
import com.settl.backend.group.dto.CreateGroupRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.TimeZone;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GroupControllerTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private JavaMailSender javaMailSender;

    private User adminUser;
    private User regularUser;
    private User outsiderUser;
    private String adminToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = new User("admin@example.com", passwordEncoder.encode("Secret123!"), "Admin User");
        adminUser.setEmailVerified(true);
        adminUser = userRepository.save(adminUser);

        regularUser = new User("member@example.com", passwordEncoder.encode("Secret123!"), "Regular Member");
        regularUser.setEmailVerified(true);
        regularUser = userRepository.save(regularUser);

        outsiderUser = new User("outsider@example.com", passwordEncoder.encode("Secret123!"), "Outsider");
        outsiderUser.setEmailVerified(true);
        outsiderUser = userRepository.save(outsiderUser);

        adminToken = jwtService.generateAccessToken(adminUser);
        outsiderToken = jwtService.generateAccessToken(outsiderUser);
    }

    @Test
    void createGroupShouldReturn201AndAddCallerAsAdmin() throws Exception {
        CreateGroupRequest request = new CreateGroupRequest("Roadtrip", "USD");

        mockMvc.perform(post("/api/groups")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Roadtrip"))
                .andExpect(jsonPath("$.data.defaultCurrency").value("USD"))
                .andExpect(jsonPath("$.data.memberCount").value(1))
                .andExpect(jsonPath("$.data.members[0].email").value("admin@example.com"))
                .andExpect(jsonPath("$.data.members[0].isAdmin").value(true));
    }

    @Test
    void nonMemberCannotViewGroupDetails() throws Exception {
        Group group = new Group("Private Flat", "EUR", adminUser);
        group = groupRepository.save(group);
        groupMemberRepository.save(new GroupMember(group, adminUser, true));

        mockMvc.perform(get("/api/groups/" + group.getId())
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("NOT_A_GROUP_MEMBER"));
    }

    @Test
    void memberCanViewGroupDetails() throws Exception {
        Group group = new Group("Shared Flat", "GBP", adminUser);
        group = groupRepository.save(group);
        groupMemberRepository.save(new GroupMember(group, adminUser, true));

        mockMvc.perform(get("/api/groups/" + group.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Shared Flat"))
                .andExpect(jsonPath("$.data.defaultCurrency").value("GBP"));
    }

    @Test
    void adminCanAddExistingUserToGroup() throws Exception {
        Group group = new Group("Dinner Group", "INR", adminUser);
        group = groupRepository.save(group);
        groupMemberRepository.save(new GroupMember(group, adminUser, true));

        AddMemberRequest request = new AddMemberRequest("member@example.com", false);

        mockMvc.perform(post("/api/groups/" + group.getId() + "/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isExistingUser").value(true))
                .andExpect(jsonPath("$.data.email").value("member@example.com"));
    }

    @Test
    void removeMemberWithZeroBalanceShouldSucceed() throws Exception {
        Group group = new Group("Ski Trip", "USD", adminUser);
        group = groupRepository.save(group);
        groupMemberRepository.save(new GroupMember(group, adminUser, true));
        groupMemberRepository.save(new GroupMember(group, regularUser, false));

        mockMvc.perform(delete("/api/groups/" + group.getId() + "/members/" + regularUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
