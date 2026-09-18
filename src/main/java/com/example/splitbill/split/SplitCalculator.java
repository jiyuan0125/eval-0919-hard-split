package com.example.splitbill.split;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic equal split in integer cents.
 *
 * Rule: base = total / n (integer division), remainder r = total % n cents.
 * Recipients are sorted by user id ascending; the first r recipients each get
 * one extra cent. No floating point is ever involved, so the result depends
 * only on (totalCents, userIds), never on evaluation order or platform.
 */
public final class SplitCalculator {

    private SplitCalculator() {
    }

    public static Map<Long, Long> splitEqual(long totalCents, List<Long> userIds) {
        if (userIds.isEmpty()) {
            throw new IllegalArgumentException("at least one recipient is required");
        }
        if (totalCents < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        List<Long> sorted = userIds.stream().distinct().sorted().toList();
        if (sorted.size() != userIds.size()) {
            throw new IllegalArgumentException("duplicate recipients are not allowed");
        }
        long n = sorted.size();
        long base = totalCents / n;
        long remainder = totalCents % n;
        Map<Long, Long> shares = new LinkedHashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            shares.put(sorted.get(i), base + (i < remainder ? 1 : 0));
        }
        return shares;
    }
}
