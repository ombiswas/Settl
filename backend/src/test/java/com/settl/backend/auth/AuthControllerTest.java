package com.settl.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settl.backend.auth.dto.RegisterRequest;
import com.settl.backend.auth.dto.ResendVerificationRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

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
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    @Test
    void registerEndpointShouldCreateUserAndReturn201() throws Exception {
        RegisterRequest request = new RegisterRequest("jane@example.com", "SecurePass123!", "Jane Doe");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("jane@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("Jane Doe"))
                .andExpect(jsonPath("$.data.emailVerified").value(false))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        User saved = userRepository.findByEmail("jane@example.com").orElseThrow();
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(passwordEncoder.matches("SecurePass123!", saved.getPasswordHash())).isTrue();
        assertThat(saved.getVerificationToken()).isNotNull();
    }

    @Test
    void registerWithInvalidEmailShouldReturn400() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "Short", "");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.displayName").exists());
    }

    @Test
    void verifyEndpointWithValidTokenShouldActivateUser() throws Exception {
        String rawToken = "my-secret-test-token-123";
        String tokenHash = AuthService.hashToken(rawToken);

        User user = new User("mark@example.com", passwordEncoder.encode("Pass12345!"), "Mark");
        user.setEmailVerified(false);
        user.setVerificationToken(tokenHash);
        user.setVerificationTokenExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        userRepository.save(user);

        mockMvc.perform(get("/api/auth/verify")
                        .param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verified").value(true));

        User updated = userRepository.findByEmail("mark@example.com").orElseThrow();
        assertThat(updated.isEmailVerified()).isTrue();
        assertThat(updated.getVerificationToken()).isNull();
    }

    @Test
    void resendVerificationShouldAlwaysReturn200() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest("random@example.com");

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
