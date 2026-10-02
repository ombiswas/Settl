package com.settl.backend.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settl.backend.auth.JwtService;
import com.settl.backend.auth.dto.LoginRequest;
import com.settl.backend.auth.dto.RegisterRequest;
import com.settl.backend.auth.dto.ResendVerificationRequest;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.group.dto.AddMemberRequest;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.TimeZone;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void cleanUp() {
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void registerRateLimitShouldEnforceLimitOf3AndReturn429WithHeaders() throws Exception {
        String testIp = "192.168.100.50";

        // First 3 requests should pass through to business logic
        for (int i = 1; i <= 3; i++) {
            RegisterRequest req = new RegisterRequest("user" + i + "@example.com", "Password123!", "User " + i);
            mockMvc.perform(post("/api/auth/register")
                            .header("X-Forwarded-For", testIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("X-RateLimit-Limit", "3"))
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("X-RateLimit-Reset"));
        }

        // 4th request must be rejected with 429 Too Many Requests
        RegisterRequest blockedReq = new RegisterRequest("user4@example.com", "Password123!", "User 4");
        mockMvc.perform(post("/api/auth/register")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockedReq)))
                .andExpect(status().is(429))
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("X-RateLimit-Reset"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void loginRateLimitShouldEnforceLimitOf5AndRejectSubsequentCallsWith429() throws Exception {
        String testIp = "192.168.100.99";
        LoginRequest loginReq = new LoginRequest("someone@example.com", "WrongPassword123!");

        // 5 allowed attempts
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", testIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("X-RateLimit-Limit", "5"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(5 - i)));
        }

        // 6th through 10th attempts must immediately fail with 429 Too Many Requests
        for (int i = 6; i <= 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", testIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().is(429))
                    .andExpect(header().string("X-RateLimit-Remaining", "0"))
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
        }
    }

    @Test
    void loginRateLimitShouldEnforceLimitByEmailAcrossDifferentIps() throws Exception {
        String email = "bruteforce_target@example.com";
        LoginRequest req = new LoginRequest(email, "WrongPassword!");

        // 5 requests from 5 different IPs targeting the same email
        for (int i = 1; i <= 5; i++) {
            String ip = "10.0.1." + i;
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(5 - i)));
        }

        // 6th request from IP 10.0.1.6 targeting the same email must be rejected with 429
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.0.1.6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is(429))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));

        // However, a different email from IP 10.0.1.6 should be allowed through (returning 401 Unauthorized, not 429)
        LoginRequest otherReq = new LoginRequest("different_user@example.com", "WrongPassword!");
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.0.1.6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-RateLimit-Remaining", "3"));
    }

    @Test
    void resendVerificationRateLimitShouldEnforceLimitOf3() throws Exception {
        String testIp = "192.168.100.77";
        ResendVerificationRequest req = new ResendVerificationRequest("somebody@example.com");

        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/auth/resend-verification")
                            .header("X-Forwarded-For", testIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "3"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(3 - i)));
        }

        mockMvc.perform(post("/api/auth/resend-verification")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is(429))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void groupInviteRateLimitShouldEnforceLimitOf20ForAuthenticatedUser() throws Exception {
        User admin = new User("admin_inviter@example.com", passwordEncoder.encode("Password123!"), "Admin Inviter");
        admin.setEmailVerified(true);
        admin = userRepository.save(admin);

        Group group = new Group("Invite Group", "USD", admin);
        group = groupRepository.save(group);
        groupMemberRepository.save(new GroupMember(group, admin, true));

        String token = jwtService.generateAccessToken(admin);

        for (int i = 1; i <= 20; i++) {
            AddMemberRequest req = new AddMemberRequest("invitee" + i + "@example.com", false);
            mockMvc.perform(post("/api/groups/" + group.getId() + "/members")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "20"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(20 - i)));
        }

        AddMemberRequest blockedReq = new AddMemberRequest("invitee21@example.com", false);
        mockMvc.perform(post("/api/groups/" + group.getId() + "/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockedReq)))
                .andExpect(status().is(429))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }
}
