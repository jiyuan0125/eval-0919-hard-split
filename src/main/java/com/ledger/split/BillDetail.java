package com.ledger.split;

import java.util.List;

/**
 * Aggregate view assembled from immutable rows: bill + computed shares + event
 * stream (with confirmations) + settlements.
 */
public record BillDetail(SplitBill bill,
                         SplitStatus status,
                         List<SplitParticipant> participants,
                         List<SplitEvent> events,
                         List<Settlement> settlements,
                         List<Long> confirmedUserIds) {
}
