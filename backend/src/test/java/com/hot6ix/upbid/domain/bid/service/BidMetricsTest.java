package com.hot6ix.upbid.domain.bid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 락을 잡고 있던 시간이 트랜잭션이 끝날 때 기록되는지, 그리고 접수와 거절이 갈려서 들어가는지
 * 본다. 트랜잭션은 프록시가 열고 닫으므로 여기서는 동기화만 직접 켜서 흉내 낸다.
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
    @DisplayName("첫 입찰 전에도 다섯 지표가 만들어져 있다")
    void registersTimersUpFront() {

        // 시계열이 측정 도중에 생기면 run.sh 가 구간 증가분을 못 구한다.
        assertThat(registry.find("upbid.bid.before.lock").timer()).isNotNull();
        assertThat(registry.find("upbid.bid.lock.wait").timer()).isNotNull();
        assertThat(holdTimer("accepted")).isNotNull();
        assertThat(holdTimer("rejected")).isNotNull();
        assertThat(registry.find("upbid.bid.commit").timer()).isNotNull();
    }

    @Test
    @DisplayName("커밋으로 끝나면 접수 경로에 기록되고 커밋 시간도 남는다")
    void recordsAcceptedHoldOnCommit() {

        TransactionSynchronizationManager.initSynchronization();

        bidMetrics.recordLockWait(() -> "잠긴 물품");

        assertThat(holdCount("accepted"))
                .as("메서드가 끝난 시점에 기록하면 커밋에 걸린 시간이 빠진다")
                .isZero();

        commitTransaction();

        assertThat(holdCount("accepted")).isEqualTo(1);
        assertThat(holdCount("rejected")).isZero();
        assertThat(registry.get("upbid.bid.commit").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("롤백으로 끝나면 거절 경로에 기록되고 커밋 시간은 안 남는다")
    void recordsRejectedHoldOnRollback() {

        TransactionSynchronizationManager.initSynchronization();

        bidMetrics.recordLockWait(() -> "잠긴 물품");

        rollbackTransaction();

        assertThat(holdCount("rejected"))
                .as("입찰 거절은 ApplicationException 이고 그것이 RuntimeException 이라 롤백된다")
                .isEqualTo(1);
        assertThat(holdCount("accepted")).isZero();
        assertThat(registry.get("upbid.bid.commit").timer().count())
                .as("롤백 경로에서는 beforeCommit 이 안 불린다")
                .isZero();
    }

    @Test
    @DisplayName("락을 못 잡으면 유지 시간을 재지 않는다")
    void doesNotRecordHoldWhenLockFails() {

        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> bidMetrics.recordLockWait(() -> {
            throw new IllegalStateException("락 타임아웃");
        })).isInstanceOf(IllegalStateException.class);

        rollbackTransaction();

        assertThat(holdCount("accepted") + holdCount("rejected"))
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
        assertThat(holdCount("accepted") + holdCount("rejected")).isZero();
    }

    @Test
    @DisplayName("락 앞 구간은 부른 자리에서 바로 기록된다")
    void recordsBeforeLockImmediately() {

        bidMetrics.recordBeforeLock(System.nanoTime());

        assertThat(registry.get("upbid.bid.before.lock").timer().count()).isEqualTo(1);
    }

    private io.micrometer.core.instrument.Timer holdTimer(String result) {
        return registry.find("upbid.bid.lock.hold").tag("result", result).timer();
    }

    private long holdCount(String result) {
        return holdTimer(result).count();
    }

    /** 커밋으로 끝났을 때 스프링이 하는 일을 흉내 낸다. */
    private void commitTransaction() {
        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> {
            synchronization.beforeCommit(false);
            synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        });
    }

    /** 롤백으로 끝났을 때. beforeCommit 은 불리지 않는다. */
    private void rollbackTransaction() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization ->
                        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }
}
