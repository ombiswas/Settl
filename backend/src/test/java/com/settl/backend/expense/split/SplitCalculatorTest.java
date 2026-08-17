package com.settl.backend.expense.split;

import com.settl.backend.common.ApiException;
import com.settl.backend.expense.SplitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitCalculatorTest {

    private SplitCalculator splitCalculator;

    private UUID user1;
    private UUID user2;
    private UUID user3;

    @BeforeEach
    void setUp() {
        splitCalculator = new SplitCalculator(List.of(
                new EqualSplitStrategy(),
                new ExactSplitStrategy(),
                new PercentageSplitStrategy(),
                new SharesSplitStrategy(),
                new PersonalSplitStrategy()
        ));

        user1 = UUID.randomUUID();
        user2 = UUID.randomUUID();
        user3 = UUID.randomUUID();
    }

    @Test
    void equalSplitWithOneCentRemainderAssignsToPayerDeterministically() {
        // 10.00 split 3 ways: 3.33 * 3 = 9.99, remainder 0.01 assigned to payer (user1)
        BigDecimal totalAmount = new BigDecimal("10.00");
        List<SplitParam> params = List.of(
                SplitParam.equal(user1),
                SplitParam.equal(user2),
                SplitParam.equal(user3)
        );

        Map<UUID, BigDecimal> shares = splitCalculator.calculate(SplitType.EQUAL, totalAmount, params, user1);

        assertThat(shares.get(user1)).isEqualTo(new BigDecimal("3.34"));
        assertThat(shares.get(user2)).isEqualTo(new BigDecimal("3.33"));
        assertThat(shares.get(user3)).isEqualTo(new BigDecimal("3.33"));

        BigDecimal sum = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualTo(totalAmount);
    }

    @Test
    void exactSplitValidatesExactTotalSum() {
        BigDecimal totalAmount = new BigDecimal("100.00");
        List<SplitParam> params = List.of(
                SplitParam.exact(user1, new BigDecimal("40.00")),
                SplitParam.exact(user2, new BigDecimal("35.00")),
                SplitParam.exact(user3, new BigDecimal("25.00"))
        );

        Map<UUID, BigDecimal> shares = splitCalculator.calculate(SplitType.EXACT, totalAmount, params, user1);

        assertThat(shares.get(user1)).isEqualTo(new BigDecimal("40.00"));
        assertThat(shares.get(user2)).isEqualTo(new BigDecimal("35.00"));
        assertThat(shares.get(user3)).isEqualTo(new BigDecimal("25.00"));
    }

    @Test
    void exactSplitThrowsWhenSumDoesNotMatch() {
        BigDecimal totalAmount = new BigDecimal("100.00");
        List<SplitParam> params = List.of(
                SplitParam.exact(user1, new BigDecimal("40.00")),
                SplitParam.exact(user2, new BigDecimal("35.00")),
                SplitParam.exact(user3, new BigDecimal("20.00")) // Sums to 95.00 instead of 100.00
        );

        assertThatThrownBy(() -> splitCalculator.calculate(SplitType.EXACT, totalAmount, params, user1))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not match total expense amount");
    }

    @Test
    void percentageSplitCalculatesCorrectAmountsAndValidatesSum() {
        BigDecimal totalAmount = new BigDecimal("200.00");
        List<SplitParam> params = List.of(
                SplitParam.percentage(user1, new BigDecimal("50.00")),
                SplitParam.percentage(user2, new BigDecimal("30.00")),
                SplitParam.percentage(user3, new BigDecimal("20.00"))
        );

        Map<UUID, BigDecimal> shares = splitCalculator.calculate(SplitType.PERCENTAGE, totalAmount, params, user1);

        assertThat(shares.get(user1)).isEqualTo(new BigDecimal("100.00"));
        assertThat(shares.get(user2)).isEqualTo(new BigDecimal("60.00"));
        assertThat(shares.get(user3)).isEqualTo(new BigDecimal("40.00"));
    }

    @Test
    void percentageSplitThrowsWhenSumIsNot100() {
        BigDecimal totalAmount = new BigDecimal("100.00");
        List<SplitParam> params = List.of(
                SplitParam.percentage(user1, new BigDecimal("50.00")),
                SplitParam.percentage(user2, new BigDecimal("40.00")) // Sums to 90.00
        );

        assertThatThrownBy(() -> splitCalculator.calculate(SplitType.PERCENTAGE, totalAmount, params, user1))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("must sum to exactly 100.00%");
    }

    @Test
    void sharesSplitCalculatesWeightedRatios() {
        BigDecimal totalAmount = new BigDecimal("90.00");
        List<SplitParam> params = List.of(
                SplitParam.shares(user1, 2), // 2 / 3 -> 60.00
                SplitParam.shares(user2, 1)  // 1 / 3 -> 30.00
        );

        Map<UUID, BigDecimal> shares = splitCalculator.calculate(SplitType.SHARES, totalAmount, params, user1);

        assertThat(shares.get(user1)).isEqualTo(new BigDecimal("60.00"));
        assertThat(shares.get(user2)).isEqualTo(new BigDecimal("30.00"));
    }

    @Test
    void personalSplitAllocatesFullAmountToPayer() {
        BigDecimal totalAmount = new BigDecimal("75.50");
        Map<UUID, BigDecimal> shares = splitCalculator.calculate(SplitType.PERSONAL, totalAmount, List.of(), user1);

        assertThat(shares).hasSize(1);
        assertThat(shares.get(user1)).isEqualTo(new BigDecimal("75.50"));
    }
}
