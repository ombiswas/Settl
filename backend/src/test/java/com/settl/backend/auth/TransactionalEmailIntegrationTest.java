package com.settl.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settl.backend.auth.dto.RegisterRequest;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TransactionalEmailIntegrationTest.RollbackTestHelper.class)
@TestPropertySource(properties = {
        "spring.mail.username=mailer@settl.test",
        "app.mail.from=noreply@settl.test"
})
class TransactionalEmailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RollbackTestHelper rollbackTestHelper;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        reset(javaMailSender);
        Session session = Session.getInstance(new Properties());
        when(javaMailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(session));
    }

    @Test
    void emailIsDispatchedAfterTransactionCommit() throws Exception {
        RegisterRequest request = new RegisterRequest("commit.user@example.com", "SecurePass123!", "Commit User");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("commit.user@example.com"));

        // Verify user is committed in database
        Optional<User> saved = userRepository.findByEmail("commit.user@example.com");
        assertThat(saved).isPresent();

        // Verify email dispatched asynchronously on mailTaskExecutor after commit
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(javaMailSender, atLeastOnce()).send(any(MimeMessage.class));
        });
    }

    @Test
    void noEmailIsSentOnTransactionRollback() {
        assertThatThrownBy(() -> rollbackTestHelper.executeWithRollback("rollback.user@example.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Forced rollback for testing");

        // Verify no email was dispatched since the transaction rolled back
        verify(javaMailSender, after(600).never()).send(any(MimeMessage.class));
    }

    @Test
    void failingMailSenderDoesNotBreakApiResponse() throws Exception {
        doThrow(new MailSendException("SMTP connection refused during test"))
                .when(javaMailSender).send(any(MimeMessage.class));

        RegisterRequest request = new RegisterRequest("failing.mail@example.com", "SecurePass123!", "Failing Mail User");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("failing.mail@example.com"));

        // User should still be created and persisted in the database
        Optional<User> saved = userRepository.findByEmail("failing.mail@example.com");
        assertThat(saved).isPresent();

        // Verify send attempt was executed asynchronously and failed safely
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(javaMailSender, atLeastOnce()).send(any(MimeMessage.class));
        });
    }

    @TestComponent
    public static class RollbackTestHelper {

        @Autowired
        private EmailService emailService;

        @Transactional
        public void executeWithRollback(String toEmail) {
            emailService.sendVerificationEmail(toEmail, "Rollback User", "https://settl.test/verify?token=fake");
            throw new RuntimeException("Forced rollback for testing");
        }
    }
}
