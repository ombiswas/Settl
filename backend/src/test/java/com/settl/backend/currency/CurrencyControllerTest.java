package com.settl.backend.currency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;

import java.util.TimeZone;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CurrencyControllerTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JavaMailSender javaMailSender;

    @Test
    void getSupportedCurrenciesShouldReturnCurrenciesWithRates() throws Exception {
        mockMvc.perform(get("/api/currencies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.code == 'USD')]").exists())
                .andExpect(jsonPath("$.data[?(@.code == 'EUR')]").exists());
    }

    @Test
    void convertCurrencyShouldReturnConvertedAmountAndRate() throws Exception {
        mockMvc.perform(get("/api/currencies/convert")
                        .param("amount", "100.00")
                        .param("from", "EUR")
                        .param("to", "USD")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fromCurrency").value("EUR"))
                .andExpect(jsonPath("$.data.toCurrency").value("USD"))
                .andExpect(jsonPath("$.data.originalAmount").value(100.00))
                .andExpect(jsonPath("$.data.convertedAmount").isNumber())
                .andExpect(jsonPath("$.data.exchangeRate").isNumber());
    }

    @Test
    void convertCurrencySameCurrencyShouldReturnExactOriginalAmount() throws Exception {
        mockMvc.perform(get("/api/currencies/convert")
                        .param("amount", "250.50")
                        .param("from", "INR")
                        .param("to", "INR")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fromCurrency").value("INR"))
                .andExpect(jsonPath("$.data.toCurrency").value("INR"))
                .andExpect(jsonPath("$.data.convertedAmount").value(250.50))
                .andExpect(jsonPath("$.data.exchangeRate").value(1.000000));
    }
}
