package com.settl.backend.settlement.simplifier;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * =========================================================================================
 *                         DEBT SIMPLIFICATION ALGORITHM (GREEDY MAX-HEAP)
 * =========================================================================================
 *
 * THE PROBLEM:
 * In a group of N people sharing expenses, individual debts naturally form a dense directed
 * graph with up to N(N - 1) / 2 pairwise debts, full of redundant circular payments (e.g.,
 * A owes B $20, B owes C $20, and C owes A $20).
 *
 * THE GREEDY MAX-HEAP STRATEGY:
 * 1. Compute each user's NET BALANCE:
 *    Net Balance = (Total Paid For Others) - (Total Share Owed) + (Settlements Paid - Settlements Received)
 *    - Positive Net Balance (> 0): Creditor (is owed money from the group).
 *    - Negative Net Balance (< 0): Debtor (owes money to the group).
 *    - Zero (0.00): Completely settled.
 *
 * 2. Partition non-zero participants into two Max-Heaps (PriorityQueues):
 *    - Creditors Heap: Sorted descending by amount owed to them.
 *    - Debtors Heap: Sorted descending by absolute debt owed.
 *
 * 3. Greedily match and settle in a loop:
 *    - Pop the largest Creditor C and largest Debtor D.
 *    - Settle payment S = min(C.amount, D.amount) from D -> C.
 *    - Deduct S from both balances:
 *      - If C still has remainder, push C back to Creditors Heap.
 *      - If D still has remainder, push D back to Debtors Heap.
 *      - If a party's remainder hits 0.00, they are completely resolved and dropped.
 *    - Terminate when both heaps are empty.
 *
 * THEORETICAL GUARANTEES & COMPLEXITY:
 * - Upper Bound on Transactions: At most N - 1 transactions are generated (compared to O(N^2)
 *   in the naive case). Every step completely settles at least one person's full debt.
 * - Time Complexity: O(N log N) total runtime. Each heap push/pop takes O(log N) and there
 *   are at most 2N total operations.
 * - Space Complexity: O(N) auxiliary space to store heaps and computed transactions.
 * - Precision: Uses BigDecimal with 2-decimal rounding (HALF_EVEN) to eliminate float drift.
 * =========================================================================================
 */
@Component
public class DebtSimplifier {

    private static final BigDecimal EPSILON = new BigDecimal("0.005");

    public List<SimplifiedTransaction> simplify(Map<UUID, BigDecimal> netBalances) {
        if (netBalances == null || netBalances.isEmpty()) {
            return List.of();
        }

        // Max-heaps sorted descending by amount
        PriorityQueue<UserBalance> creditors = new PriorityQueue<>(
                Comparator.comparing(UserBalance::amount).reversed()
        );
        PriorityQueue<UserBalance> debtors = new PriorityQueue<>(
                Comparator.comparing(UserBalance::amount).reversed()
        );

        netBalances.forEach((userId, balance) -> {
            if (balance != null) {
                BigDecimal normalized = balance.setScale(2, RoundingMode.HALF_EVEN);
                if (normalized.compareTo(EPSILON) > 0) {
                    creditors.add(new UserBalance(userId, normalized));
                } else if (normalized.compareTo(EPSILON.negate()) < 0) {
                    debtors.add(new UserBalance(userId, normalized.abs()));
                }
            }
        });

        List<SimplifiedTransaction> transactions = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            UserBalance creditor = creditors.poll();
            UserBalance debtor = debtors.poll();

            BigDecimal settleAmount = creditor.amount().min(debtor.amount()).setScale(2, RoundingMode.HALF_EVEN);

            if (settleAmount.compareTo(BigDecimal.ZERO) > 0) {
                transactions.add(new SimplifiedTransaction(debtor.userId(), creditor.userId(), settleAmount));
            }

            BigDecimal creditorRemainder = creditor.amount().subtract(settleAmount).setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal debtorRemainder = debtor.amount().subtract(settleAmount).setScale(2, RoundingMode.HALF_EVEN);

            if (creditorRemainder.compareTo(EPSILON) > 0) {
                creditors.add(new UserBalance(creditor.userId(), creditorRemainder));
            }
            if (debtorRemainder.compareTo(EPSILON) > 0) {
                debtors.add(new UserBalance(debtor.userId(), debtorRemainder));
            }
        }

        return transactions;
    }
}
