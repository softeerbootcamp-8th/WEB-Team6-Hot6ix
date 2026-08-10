package com.hot6ix.upbid.domain.bid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 락을 잡고 있던 시간이 트랜잭션이 끝날 때 기록되는지 본다. 트랜잭션은 프록시가 열고 닫으므로
 * 여기서는 동기화만 직접 켜서 흉내 낸다.
 */
class BidMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final BidMetrics bidMetrics = new BidMetrics(registry);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("첫 입찰 전에도 두 지표가 만들어져 있다")
    void registersTimersUpFront() {

        // 시계열이 측정 도중에 생기면 run.sh 가 구간 증가분을 못 구한다.
        assertThat(registry.find("upbid.bid.lock.wait").timer()).isNotNull();
        assertThat(registry.find("upbid.bid.lock.hold").timer()).isNotNull();
    }

    @Test
    @DisplayName("락 유지 시간은 트랜잭션이 끝날 때 기록된다")
    void recordsHoldOnTransactionCompletion() {

        TransactionSynchronizationManager.initSynchronization();

        bidMetrics.recordLockWait(() -> "잠긴 물품");

        assertThat(holdCount())
                .as("메서드가 끝난 시점에 기록하면 커밋에 걸린 시간이 빠진다")
                .isZero();

        completeTransaction();

        assertThat(holdCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("락을 못 잡으면 유지 시간을 재지 않는다")
    void doesNotRecordHoldWhenLockFails() {

        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> bidMetrics.recordLockWait(() -> {
            throw new IllegalStateException("락 타임아웃");
        })).isInstanceOf(IllegalStateException.class);

        completeTransaction();

        assertThat(holdCount())
                .as("잡지도 못한 락은 유지 시간을 잴 것이 없다")
                .isZero();
        assertThat(registry.get("upbid.bid.lock.wait").timer().count())
                .as("기다린 시간은 실패해도 남는다. 가장 오래 기다린 요청이라 빼면 p95가 낮게 나온다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("트랜잭션 밖에서 불려도 터지지 않는다")
    void survivesOutsideTransaction() {

        assertThat(bidMetrics.recordLockWait(() -> "값")).isEqualTo("값");
        assertThat(holdCount()).isZero();
    }

    private long holdCount() {
        return registry.get("upbid.bid.lock.hold").timer().count();
    }

    /** 커밋 뒤 스프링이 하는 일을 흉내 낸다. */
    private void completeTransaction() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(0));
    }
}
