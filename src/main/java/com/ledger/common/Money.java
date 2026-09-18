package com.ledger.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Money is stored and computed as integer minor units (cents).
 * No floating point is ever used for monetary values.
 */
public final class Money {

    public static final int CENTS_PER_YUAN = 100;

    private Money() {
    }

    /** Parse an API amount (yuan, e.g. "33.33") into cents using HALF_UP. */
    public static long yuanToCents(String yuan) {
        if (yuan == null || yuan.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION, "amount must not be empty");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(yuan);
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.VALIDATION, "invalid amount: " + yuan);
        }
        if (value.signum() < 0) {
            throw new ApiException(ErrorCode.VALIDATION, "amount must not be negative");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new ApiException(ErrorCode.VALIDATION, "amount precision cannot exceed 2 decimal places");
        }
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static String centsToYuan(long cents) {
        boolean negative = cents < 0;
        long abs = Math.absExact(cents);
        String whole = Long.toString(abs / CENTS_PER_YUAN);
        long fraction = abs % CENTS_PER_YUAN;
        String text = whole + "." + (fraction < 10 ? "0" + fraction : Long.toString(fraction));
        return negative ? "-" + text : text;
    }

    /**
     * Deterministically split {@code totalCents} among {@code weights} (equal
     * weights in this app, but weighted math is used so the rule is explicit).
     *
     * <p>Rule: every participant first gets {@code floor(total * weight / totalWeight)}
     * cents via integer division. The remaining cents (always fewer than the
     * participant count) are handed out one by one, each to the participant whose
     * integer division dropped the largest fraction; ties are broken by the
     * participant's ascending key (user id). The shares therefore always sum to
     * exactly {@code totalCents}, and the same inputs always produce the same
     * output - there is no float involved.
     */
    public static <K extends Comparable<K>> List<Share<K>> allocate(
            long totalCents, List<Weighted<K>> weights) {
        if (weights.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION, "cannot split among zero participants");
        }
        long totalWeight = weights.stream().mapToLong(Weighted::weight).sum();
        if (totalWeight <= 0) {
            throw new ApiException(ErrorCode.VALIDATION, "split weights must be positive");
        }

        long[] amounts = new long[weights.size()];
        long[] remainders = new long[weights.size()];
        long allocated = 0;
        for (int i = 0; i < weights.size(); i++) {
            long weighted = totalCents * weights.get(i).weight();
            amounts[i] = weighted / totalWeight;
            remainders[i] = weighted % totalWeight;
            allocated += amounts[i];
        }
        long left = totalCents - allocated;

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingLong((Integer i) -> remainders[i]).reversed()
                .thenComparing(i -> weights.get(i).key()));
        for (int n = 0; n < left; n++) {
            amounts[order.get(n % order.size())]++;
        }

        List<Share<K>> result = new ArrayList<>(weights.size());
        for (int i = 0; i < weights.size(); i++) {
            result.add(new Share<>(weights.get(i).key(), amounts[i]));
        }
        return result;
    }

    public record Weighted<K>(K key, long weight) {
    }

    public record Share<K>(K key, long amountCents) {
    }
}
