package com.example.splitbill.split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SplitCalculatorTest {

    @Test
    void splitsEvenlyWhenDivisible() {
        Map<Long, Long> shares = SplitCalculator.splitEqual(9000, List.of(1L, 2L, 3L));
        assertThat(shares).containsExactly(
                Map.entry(1L, 3000L), Map.entry(2L, 3000L), Map.entry(3L, 3000L));
    }

    @Test
    void distributesRemainderDeterministicallyToLowestUserIds() {
        // 100.00 split 3 ways: 33.34 + 33.33 + 33.33, extra cent to lowest id.
        Map<Long, Long> shares = SplitCalculator.splitEqual(10000, List.of(3L, 1L, 2L));
        assertThat(shares).containsExactly(
                Map.entry(1L, 3334L), Map.entry(2L, 3333L), Map.entry(3L, 3333L));
        assertThat(shares.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(10000);
    }

    @Test
    void resultIsIndependentOfInputOrder() {
        Map<Long, Long> first = SplitCalculator.splitEqual(10001, List.of(1L, 2L, 3L));
        Map<Long, Long> shuffled = SplitCalculator.splitEqual(10001, List.of(3L, 2L, 1L));
        assertThat(first).isEqualTo(shuffled);
        assertThat(first).containsEntry(1L, 3334L).containsEntry(2L, 3334L).containsEntry(3L, 3333L);
    }

    @Test
    void remainderNeverExceedsOneCentPerPerson() {
        Map<Long, Long> shares = SplitCalculator.splitEqual(100, List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L));
        long min = shares.values().stream().mapToLong(Long::longValue).min().orElseThrow();
        long max = shares.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        assertThat(max - min).isLessThanOrEqualTo(1);
        assertThat(shares.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(100);
    }

    @Test
    void rejectsEmptyAndDuplicateRecipients() {
        assertThatThrownBy(() -> SplitCalculator.splitEqual(100, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SplitCalculator.splitEqual(100, List.of(1L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SplitCalculator.splitEqual(-1, List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
