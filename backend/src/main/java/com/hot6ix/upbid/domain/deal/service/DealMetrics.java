package com.hot6ix.upbid.domain.deal.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 낙찰 계측만 아는 객체. 분리한 이유는 {@code BidMetrics}와 같다.
 *
 * <p><b>낙찰 한 건을 전체와 후보 삽입으로 나눈다.</b> {@code BidMetrics}가 {@code place()}를
 * 네 구간으로 쪼갠 것과 같은 이유다 — 값이 커졌을 때 어디를 고쳐야 하는지 말할 수 있어야 한다.
 *
 * <pre>
 *   award() 진입 ({@code REQUIRES_NEW} 라 커넥션을 새로 잡는다)
 *      │
 *      │  커넥션 획득 + findByIdForUpdate 락 대기
 *      │    ({@code hikaricp.connections.acquire} 로 앞부분을 따로 볼 수 있다)
 *      │
 *      │  existsCandidate
 *      │      ┌─────────────────────────────────────────
 *      │      │ {@link #candidateInsert}  insertCandidatesFromBids 만
 *      │      └─────────────────────────────────────────
 *      │  findCurrentWinner → 낙찰 권한 이벤트 발행
 *      │  COMMIT
 *      ▼
 *   {@link #award} 는 위 전체를 잰다
 * </pre>
 */
@Component
public class DealMetrics {

    /**
     * 낙찰 후보 스냅샷을 만드는 데 걸린 시간.
     *
     * <p>이게 마감 소요 시간에 들어간다. {@code DealCandidateAwardListener}가 커밋 뒤에
     * <b>같은 스레드에서</b> 부르기 때문에, 여기 걸린 시간만큼 스케줄러 스레드가 더 묶인다.
     * 후보를 입찰자 수만큼 만들어서 입찰이 몰린 물품일수록 길어질 것으로 보고 재기 시작했다(#198).
     *
     * <p>첫 낙찰 전에도 지표가 보이도록 생성자에서 미리 만든다.
     */
    private final Timer award;

    /**
     * {@code insertCandidatesFromBids} 한 번에 걸린 시간. {@link #award}에서 이 값을 빼면
     * 나머지(커넥션 획득, 락 대기, 이벤트 발행, 커밋)가 얼마인지 나온다.
     *
     * <p><b>이 타이머가 없어서 실제로 틀린 결론을 낼 뻔했다.</b> 후보 삽입 쿼리의 조인 순서를
     * 바꿔 단독 측정에서 200ms→88ms로 줄인 뒤 부하 테스트로 확인하려 했는데,
     * {@link #award} 하나만 있으니 개선 전후가 구분되지 않았다. 나중에 EXPLAIN 으로 확인해
     * 보니 부하 테스트가 만든 데이터 모양(물품당 입찰자 60명)에서는 이 쿼리가 0.3ms 라
     * {@link #award}의 100~500ms 안에서 0.1% 도 안 되는 조각이었다. 트랜잭션 전체를 재는
     * 지표로는 그 안의 쿼리 하나가 좋아졌는지 나빠졌는지 원리적으로 알 수 없다.
     *
     * <p>{@link #award}와 같은 이유로 생성자에서 미리 만든다.
     */
    private final Timer candidateInsert;

    public DealMetrics(MeterRegistry registry) {
        this.award = registry.timer("upbid.deal.award");
        this.candidateInsert = registry.timer("upbid.deal.candidate.insert");
    }

    /** 예외가 나도 기록하고 예외는 그대로 올린다. 실패한 실행이 가장 오래 걸린 경우일 수 있다. */
    public void recordAward(Runnable award) {
        this.award.record(award);
    }

    /** 예외가 나도 기록하는 것은 {@link #recordAward}와 같은 이유다. */
    public <T> T recordCandidateInsert(Supplier<T> insert) {
        return candidateInsert.record(insert);
    }
}
