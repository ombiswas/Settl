package com.settl.backend.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settl.backend.auth.AuthService;
import com.settl.backend.auth.JwtService;
import com.settl.backend.auth.dto.RegisterRequest;
import com.settl.backend.group.dto.AddMemberRequest;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InvitationControllerTest {

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
    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private JavaMailSender javaMailSender;

    private User owner;
    private Group group;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        groupInvitationRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        owner = new User("owner_" + UUID.randomUUID() + "@example.com", passwordEncoder.encode("Password123!"), "Owner User");
        owner.setEmailVerified(true);
        owner = userRepository.save(owner);
        ownerToken = jwtService.generateAccessToken(owner);

        group = new Group("Trip to Bali", "USD", owner);
        group = groupRepository.save(group);
        groupMemberRepository.save(new GroupMember(group, owner, true));
    }

    @Test
    void invitingUnregisteredEmailCreatesPendingInvitation() throws Exception {
        String targetEmail = "unregistered_" + UUID.randomUUID() + "@example.com";
        AddMemberRequest request = new AddMemberRequest(targetEmail, false);

        mockMvc.perform(post("/api/groups/" + group.getId() + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(targetEmail.toLowerCase()))
                .andExpect(jsonPath("$.data.isExistingUser").value(false));

        var invitations = groupInvitationRepository.findByGroupIdAndStatusWithInviter(group.getId(), GroupInvitationStatus.PENDING);
        assertThat(invitations).hasSize(1);
        assertThat(invitations.get(0).getEmail()).isEqualTo(targetEmail.toLowerCase());

        // Check GET /api/groups/{id}/invitations
        mockMvc.perform(get("/api/groups/" + group.getId() + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].email").value(targetEmail.toLowerCase()));
    }

    @Test
    void previewInvitationTokenPublicly() throws Exception {
        String rawToken = "sample_raw_token_12345678901234567890";
        String tokenHash = AuthService.hashToken(rawToken);
        String inviteEmail = "friend_" + UUID.randomUUID() + "@example.com";

        GroupInvitation invitation = new GroupInvitation(
                group,
                inviteEmail,
                owner,
                tokenHash,
                false,
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
        groupInvitationRepository.save(invitation);

        // Public request without Authorization header
        mockMvc.perform(get("/api/invitations/preview?token=" + rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.groupName").value("Trip to Bali"))
                .andExpect(jsonPath("$.data.inviterName").value("Owner User"))
                .andExpect(jsonPath("$.data.email").value(inviteEmail.toLowerCase()))
                .andExpect(jsonPath("$.data.isExpired").value(false));
    }

    @Test
    void registeringWithInviteTokenAutoVerifiesAndJoinsGroup() throws Exception {
        String rawToken = "token_for_register_" + UUID.randomUUID();
        String tokenHash = AuthService.hashToken(rawToken);
        String targetEmail = "invitee_" + UUID.randomUUID() + "@example.com";

        GroupInvitation invitation = new GroupInvitation(
                group,
                targetEmail,
                owner,
                tokenHash,
                true, // Invited as admin
                Instant.now().plus(7, ChronoUnit.DAYS)
        );
        groupInvitationRepository.save(invitation);

        RegisterRequest registerRequest = new RegisterRequest(
                targetEmail,
                "Password123!",
                "Invited Member",
                rawToken
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.emailVerified").value(true)); // Pre-verified!

        User registeredUser = userRepository.findByEmail(targetEmail.toLowerCase()).orElseThrow();
        assertThat(registeredUser.isEmailVerified()).isTrue();

        // Check that user is now in group_members with admin flag
        var memberOpt = groupMemberRepository.findByGroupIdAndUserId(group.getId(), registeredUser.getId());
        assertThat(memberOpt).isPresent();
        assertThat(memberOpt.get().isAdmin()).isTrue();

        // Check invitation status is ACCEPTED
        GroupInvitation updatedInv = groupInvitationRepository.findById(invitation.getId()).orElseThrow();
        assertThat(updatedInv.getStatus()).isEqualTo(GroupInvitationStatus.ACCEPTED);
    }
}
