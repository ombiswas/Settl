package com.settl.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settl.backend.auth.dto.LoginRequest;
import com.settl.backend.auth.dto.RegisterRequest;
import com.settl.backend.auth.dto.ResendVerificationRequest;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void cleanDb() {
        refreshTokenRepository.deleteAll();
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
                .andExpect(jsonPath("$.data.emailVerified").value(false));

        User saved = userRepository.findByEmail("jane@example.com").orElseThrow();
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(passwordEncoder.matches("SecurePass123!", saved.getPasswordHash())).isTrue();
    }

    @Test
    void loginWithVerifiedUserShouldReturnTokenAndSetCookie() throws Exception {
        User user = new User("verified@example.com", passwordEncoder.encode("Secret123!"), "Verified User");
        user.setEmailVerified(true);
        userRepository.save(user);

        LoginRequest request = new LoginRequest("verified@example.com", "Secret123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value("verified@example.com"));

        assertThat(refreshTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void loginWithUnverifiedUserShouldReturn403EmailNotVerified() throws Exception {
        User user = new User("unverified@example.com", passwordEncoder.encode("Secret123!"), "Unverified User");
        user.setEmailVerified(false);
        userRepository.save(user);

        LoginRequest request = new LoginRequest("unverified@example.com", "Secret123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void loginWithWrongPasswordShouldReturn401() throws Exception {
        User user = new User("user@example.com", passwordEncoder.encode("CorrectPassword!"), "User");
        user.setEmailVerified(true);
        userRepository.save(user);

        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshRotationShouldIssueNewAccessTokenAndRotateCookie() throws Exception {
        User user = new User("rotate@example.com", passwordEncoder.encode("Secret123!"), "Rotate User");
        user.setEmailVerified(true);
        user = userRepository.save(user);

        LoginRequest loginReq = new LoginRequest("rotate@example.com", "Secret123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        String cookieValue = setCookie.split(";")[0].replace("refresh_token=", "");

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", cookieValue)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    void getCurrentUserWithValidJwtShouldReturnProfile() throws Exception {
        User user = new User("profile@example.com", passwordEncoder.encode("Pass12345!"), "Profile User");
        user.setEmailVerified(true);
        user = userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("profile@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("Profile User"));
    }

    @Test
    void getCurrentUserWithoutJwtShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void logoutShouldClearCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refresh_token", "some-token")))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.success").value(true));
    }
}
