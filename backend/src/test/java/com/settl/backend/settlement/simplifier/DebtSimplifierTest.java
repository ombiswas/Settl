package com.settl.backend.settlement.simplifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DebtSimplifierTest {

    private DebtSimplifier debtSimplifier;
    private final Random random = new Random(42);

    @BeforeEach
    void setUp() {
        debtSimplifier = new DebtSimplifier();
    }

    @Test
    void zeroDebtGroupProducesZeroSettlements() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();

        Map<UUID, BigDecimal> balances = Map.of(
                u1, BigDecimal.ZERO,
                u2, BigDecimal.ZERO,
                u3, new BigDecimal("0.00")
        );

        List<SimplifiedTransaction> result = debtSimplifier.simplify(balances);
        assertThat(result).isEmpty();
    }

    @Test
    void threePersonCycleCollapsesToZeroTransactions() {
        // A paid 30 for B, B paid 30 for C, C paid 30 for A
        // Net balance for everyone is 0.00
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID charlie = UUID.randomUUID();

        Map<UUID, BigDecimal> netBalances = Map.of(
                alice, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN),
                bob, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN),
                charlie, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN)
        );

        List<SimplifiedTransaction> settlements = debtSimplifier.simplify(netBalances);
        assertThat(settlements).isEmpty();
    }

    @Test
    void singleCreditorManyDebtors() {
        // Alice is owed 100. Bob, Charlie, David, Eve owe 25 each
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID charlie = UUID.randomUUID();
        UUID david = UUID.randomUUID();
        UUID eve = UUID.randomUUID();

        Map<UUID, BigDecimal> netBalances = Map.of(
                alice, new BigDecimal("100.00"),
                bob, new BigDecimal("-25.00"),
                charlie, new BigDecimal("-25.00"),
                david, new BigDecimal("-25.00"),
                eve, new BigDecimal("-25.00")
        );

        List<SimplifiedTransaction> settlements = debtSimplifier.simplify(netBalances);

        assertThat(settlements).hasSize(4);
        for (SimplifiedTransaction tx : settlements) {
            assertThat(tx.toUserId()).isEqualTo(alice);
            assertThat(tx.amount()).isEqualTo(new BigDecimal("25.00"));
        }
    }

    @Test
    void singleDebtorManyCreditors() {
        // Alice owes 100. Bob, Charlie, David, Eve are owed 25 each
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID charlie = UUID.randomUUID();
        UUID david = UUID.randomUUID();
        UUID eve = UUID.randomUUID();

        Map<UUID, BigDecimal> netBalances = Map.of(
                alice, new BigDecimal("-100.00"),
                bob, new BigDecimal("25.00"),
                charlie, new BigDecimal("25.00"),
                david, new BigDecimal("25.00"),
                eve, new BigDecimal("25.00")
        );

        List<SimplifiedTransaction> settlements = debtSimplifier.simplify(netBalances);

        assertThat(settlements).hasSize(4);
        for (SimplifiedTransaction tx : settlements) {
            assertThat(tx.fromUserId()).isEqualTo(alice);
            assertThat(tx.amount()).isEqualTo(new BigDecimal("25.00"));
        }
    }

    @RepeatedTest(50)
    void randomizedPropertyTestWithZeroSumBalances() {
        // 1. Generate N random balances that sum to zero (N between 3 and 20)
        int n = 3 + random.nextInt(18);
        List<UUID> users = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            users.add(UUID.randomUUID());
        }

        Map<UUID, BigDecimal> netBalances = new HashMap<>();
        BigDecimal runningSum = BigDecimal.ZERO;

        for (int i = 0; i < n - 1; i++) {
            // Generate random balance between -500.00 and +500.00
            double val = (random.nextDouble() * 1000.0) - 500.0;
            BigDecimal balance = BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_EVEN);
            netBalances.put(users.get(i), balance);
            runningSum = runningSum.add(balance);
        }

        // The last user absorbs the opposite sum to ensure exact zero-sum invariant
        BigDecimal lastBalance = runningSum.negate().setScale(2, RoundingMode.HALF_EVEN);
        netBalances.put(users.get(n - 1), lastBalance);

        // Count number of active (non-zero) participants
        long activeParticipants = netBalances.values().stream()
                .filter(b -> b.abs().compareTo(new BigDecimal("0.005")) > 0)
                .count();

        BigDecimal totalPositiveDebt = netBalances.values().stream()
                .filter(b -> b.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);

        // 2. Run simplification algorithm
        List<SimplifiedTransaction> settlements = debtSimplifier.simplify(netBalances);

        // 3. (a) Assert every resulting settlement is strictly positive
        for (SimplifiedTransaction tx : settlements) {
            assertThat(tx.amount().compareTo(BigDecimal.ZERO))
                    .as("Settlement amount must be strictly positive")
                    .isGreaterThan(0);
            assertThat(tx.fromUserId()).isNotEqualTo(tx.toUserId());
        }

        // 4. (b) Assert total settled amount equals total original positive debt
        BigDecimal totalSettled = settlements.stream()
                .map(SimplifiedTransaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
        assertThat(totalSettled).isEqualTo(totalPositiveDebt);

        // 5. (c) Assert applying all settlements brings every user's balance to exactly zero
        Map<UUID, BigDecimal> postBalances = new HashMap<>(netBalances);
        for (SimplifiedTransaction tx : settlements) {
            // Debtor paid tx.amount -> increases post-balance toward zero
            postBalances.put(tx.fromUserId(), postBalances.get(tx.fromUserId()).add(tx.amount()));
            // Creditor received tx.amount -> decreases post-balance toward zero
            postBalances.put(tx.toUserId(), postBalances.get(tx.toUserId()).subtract(tx.amount()));
        }

        for (Map.Entry<UUID, BigDecimal> entry : postBalances.entrySet()) {
            BigDecimal finalBalance = entry.getValue().setScale(2, RoundingMode.HALF_EVEN);
            assertThat(finalBalance.abs().compareTo(new BigDecimal("0.01")))
                    .as("Post-settlement balance for user %s must be 0.00, was %s", entry.getKey(), finalBalance)
                    .isLessThanOrEqualTo(0);
        }

        // 6. (d) Assert transaction count never exceeds activeParticipants - 1
        if (activeParticipants > 0) {
            assertThat(settlements.size())
                    .as("Transaction count (%d) must not exceed N-1 (%d)", settlements.size(), activeParticipants - 1)
                    .isLessThanOrEqualTo((int) activeParticipants - 1);
        } else {
            assertThat(settlements).isEmpty();
        }
    }
}
