package com.hot6ix.upbid.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.deal.repository.DealCandidateRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code award}가 실제로 READ COMMITTED 커넥션에서 도는지 확인한다.
 *
 * <p>애노테이션만 보고 넘어가면 Spring이 격리 수준을 반영하지 못하는 구성에서도 통과한다.
 * 그래서 트랜잭션 안에서 커넥션의 격리 수준을 직접 읽는다. 저장소는 목으로 대체해 DB 데이터
 * 없이도 {@code award}가 트랜잭션을 열고 조기 반환하도록 만든다.
 *
 * <p>REPEATABLE READ에서는 {@code INSERT ... SELECT}가 {@code bids}·{@code users}에 공유 락을
 * 걸어 입찰자 수에 비례해 잠긴다. 이 설정이 풀리면 그 문제가 조용히 되살아난다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, DealCandidateService.class,
        DealCandidateServiceIsolationTest.MetricsConfig.class})
class DealCandidateServiceIsolationTest extends AbstractMySqlContainerTest {

    private static final Long ROOM_ID = 1L;
    private static final Long ITEM_ID = 2L;

    /**
     * {@code DealCandidateService}가 {@code DealMetrics}를 받으므로 이 슬라이스에도 넣어 준다.
     * {@code @DataJpaTest}는 {@code MeterRegistry}를 자동 구성하지 않아 함께 준다.
     *
     * <p>목으로 두지 않는 이유는 계측이 supplier를 감싸기 때문이다. 목이면 후보 삽입이 아예
     * 안 불려서, 계측을 붙였다는 이유만으로 낙찰 로직이 조용히 건너뛰어진다.
     */
    @TestConfiguration
    static class MetricsConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        DealMetrics dealMetrics(MeterRegistry registry) {
            return new DealMetrics(registry);
        }
    }

    @Autowired
    private DealCandidateService dealCandidateService;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private AuctionItemRepository auctionItemRepository;

    @MockitoBean
    private DealCandidateRepository dealCandidateRepository;

    @MockitoBean
    private DomainEventPublisher domainEventPublisher;

    private int currentIsolation() throws SQLException {
        return DataSourceUtils.getConnection(dataSource).getTransactionIsolation();
    }

    @Test
    @DisplayName("award는 READ COMMITTED 트랜잭션에서 실행되고, 바깥 트랜잭션은 그대로다")
    void awardRunsInReadCommitted() throws SQLException {

        assertThat(currentIsolation())
                .as("테스트를 감싼 트랜잭션은 DB 기본값(REPEATABLE READ)이어야 한다")
                .isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);

        AtomicInteger awardIsolation = new AtomicInteger();
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID))
                .thenReturn(Optional.of(AuctionItem.builder()
                        .startingPrice(10_000L)
                        .bidIncrement(1_000L)
                        .status(AuctionItemStatus.SOLD)
                        .build()));
        // 멱등 검사에서 조기 반환시키고, 그 시점의 격리 수준을 읽는다.
        when(dealCandidateRepository.existsCandidate(anyLong())).thenAnswer(invocation -> {
            awardIsolation.set(currentIsolation());
            return true;
        });

        dealCandidateService.award(ItemEnded.of(ROOM_ID, ITEM_ID, "포토카드", 15_000L, "원기",
                LocalDateTime.of(2026, 7, 29, 21, 0)));

        assertThat(awardIsolation.get())
                .as("award 트랜잭션은 READ COMMITTED여야 한다")
                .isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
    }
}
