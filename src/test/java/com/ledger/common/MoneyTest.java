package com.ledger.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void parsesYuanToCents() {
        assertThat(Money.yuanToCents("100")).isEqualTo(10000);
        assertThat(Money.yuanToCents("33.3")).isEqualTo(3330);
        assertThat(Money.yuanToCents("0.01")).isEqualTo(1);
    }

    @Test
    void rejectsTooPreciseOrInvalidAmounts() {
        assertThatThrownBy(() -> Money.yuanToCents("12.345"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.yuanToCents("abc")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.yuanToCents("-1")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.yuanToCents("")).isInstanceOf(ApiException.class);
    }

    @Test
    void centsRoundTrip() {
        assertThat(Money.centsToYuan(3333)).isEqualTo("33.33");
        assertThat(Money.centsToYuan(1)).isEqualTo("0.01");
        assertThat(Money.centsToYuan(10000)).isEqualTo("100.00");
        assertThat(Money.centsToYuan(-5)).isEqualTo("-0.05");
    }

    @Test
    void hundredOverThreeIsDeterministicAndExact() {
        List<Money.Weighted<Long>> weights = List.of(
                new Money.Weighted<>(1L, 1L),
                new Money.Weighted<>(2L, 1L),
                new Money.Weighted<>(3L, 1L));

        List<Money.Share<Long>> first = Money.allocate(10000, weights);
        List<Money.Share<Long>> second = Money.allocate(10000, weights);

        assertThat(first).extracting(Money.Share::amountCents)
                .containsExactly(3334L, 3333L, 3333L);
        assertThat(first).usingRecursiveComparison().isEqualTo(second);
        assertThat(first).extracting(Money.Share::amountCents).asList()
                .allSatisfy(share -> assertThat((long) share).isPositive());
        long sum = first.stream().mapToLong(Money.Share::amountCents).sum();
        assertThat(sum).isEqualTo(10000L);
    }

    @Test
    void oneCentRemainderGoesToLowestUserIdOnTie() {
        List<Money.Weighted<Long>> weights = List.of(
                new Money.Weighted<>(10L, 1L),
                new Money.Weighted<>(20L, 1L),
                new Money.Weighted<>(30L, 1L));
        // 100 / 3 -> all equal fractions 33.33..., tie broken by ascending user id
        assertThat(Money.allocate(100, weights)).extracting(Money.Share::amountCents)
                .containsExactly(34L, 33L, 33L);
    }

    @Test
    void evenSplitsStayEven() {
        List<Money.Weighted<Long>> weights = List.of(
                new Money.Weighted<>(1L, 1L),
                new Money.Weighted<>(2L, 1L));
        assertThat(Money.allocate(10000, weights)).extracting(Money.Share::amountCents)
                .containsExactly(5000L, 5000L);
    }

    @Test
    void rejectsEmptyOrNonPositiveWeights() {
        assertThatThrownBy(() -> Money.allocate(100, List.of()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.allocate(100,
                List.of(new Money.Weighted<>(1L, 0L)))).isInstanceOf(ApiException.class);
    }
}
