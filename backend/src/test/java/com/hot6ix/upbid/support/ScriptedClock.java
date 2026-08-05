package com.hot6ix.upbid.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * 읽을 때마다 미리 정해둔 시각을 순서대로 돌려주는 테스트용 시계.
 *
 * <p>{@link java.time.Clock#fixed}로는 <b>한 요청 안에서 시간이 흐르는 상황</b>을 만들 수 없다.
 * 입찰은 도착 시각과 락을 잡은 뒤의 시각을 따로 읽는데, 고정 시계는 둘을 항상 같은 값으로
 * 만들어 그 설계가 지켜지는지 검증할 수 없다.
 *
 * <p>이 시계는 "요청이 도착하고, 락을 300밀리초 기다린 뒤 판정했다"를 그대로 재현한다.
 * 준비한 값을 다 쓰면 마지막 값을 계속 돌려주므로, 읽는 횟수를 정확히 맞추지 않아도 된다.
 */
public final class ScriptedClock extends Clock {

    private final ZoneId zone;
    private final Deque<Instant> instants;

    private ScriptedClock(ZoneId zone, Deque<Instant> instants) {
        this.zone = zone;
        this.instants = instants;
    }

    /** 읽는 순서대로 돌려줄 시각들. 하나만 주면 고정 시계와 같다. */
    public static ScriptedClock of(Instant... instants) {
        return new ScriptedClock(ZoneId.systemDefault(), new ArrayDeque<>(Arrays.asList(instants)));
    }

    @Override
    public Instant instant() {
        return instants.size() > 1 ? instants.poll() : instants.peek();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new ScriptedClock(zone, new ArrayDeque<>(instants));
    }
}
