package com.ledger.goal;

import com.ledger.common.Money;

public record SelfProgressView(Long userId, String month, long targetCents, long spentCents) {

    public String target() {
        return Money.centsToYuan(targetCents);
    }

    public String spent() {
        return Money.centsToYuan(spentCents);
    }

    public String remaining() {
        return Money.centsToYuan(Math.max(0, targetCents - spentCents));
    }

    /** Self can see the precise percentage (2 decimals); friends never get this. */
    public String percentPrecise() {
        if (targetCents == 0) {
            return "0";
        }
        long basisPoints = Math.min(10000, spentCents * 10000 / targetCents);
        return Money.centsToYuan(basisPoints);
    }
}
