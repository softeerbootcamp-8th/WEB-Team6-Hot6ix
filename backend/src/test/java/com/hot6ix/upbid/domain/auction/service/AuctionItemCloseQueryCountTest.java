package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.auction.scheduler.AuctionCloseMetrics;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 물품 마감 한 건이 던지는 쿼리 수를 센다. <b>시간이 아니라 개수를 재는 것이 요점이다.</b>
 * 부하 측정으로 나오는 시간 값은 기기 상태에 따라 크게 흔들리는데, 개수는 흔들리지 않아서
 * 개선을 다투지 않고 말할 수 있다.
 *
 * <p>마감은 물품 행에 쓰기 락을 걸고 시작한다. 락은 줄을 세우는 장치라 한 요청이 잡고 있는
 * 시간이 뒤에 선 요청 수만큼 곱해지므로, 락 안에서 하는 일을 줄이는 것이 곧 입찰 지연을
 * 줄이는 것이 된다.
 *
 * <p>고치기 전에는 다섯 개가 전부 락 안에 있었다.
 *
 * <pre>
 * 1. findByIdForUpdate      락을 잡는다
 * 2. auction_rooms 조회      이벤트에 넣을 방 ID. 지연 로딩이라 락 안에서 나갔다
 * 3. products 조회           이벤트에 넣을 상품명. 마찬가지
 * 4. users 조회              낙찰자 닉네임
 * 5. auction_items UPDATE   커밋
 * </pre>
 *
 * <p>지금은 방 ID와 상품명을 락 앞에서 프로젝션 한 번으로 읽는다. 그래서 전체는 네 개가 되고
 * <b>락 안은 셋으로 준다</b>(락은 {@code findByIdForUpdate}에서 시작해 커밋까지다).
 *
 * <p><b>낙찰자 조회는 일부러 남겼다.</b> 그것만 성격이 다르다. 방과 상품은 경매 중에 안 바뀌지만
 * 낙찰자는 입찰이 접수될 때마다 바뀌므로, 락 앞에서 미리 읽으면 그사이 들어온 입찰이 반영되지
 * 않은 낙찰자를 발표하게 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, AuctionItemCloseService.class,
        AuctionItemCloseQueryCountTest.MetricsTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuctionItemCloseQueryCountTest extends AbstractMySqlContainerTest {

    /** {@code @DataJpaTest}는 {@code MeterRegistry}를 안 올려 준다. 목이면 마감이 통째로 안 돈다. */
    @TestConfiguration
    static class MetricsTestConfig {

        @Bean
        AuctionCloseMetrics auctionCloseMetrics() {
            return new AuctionCloseMetrics(new SimpleMeterRegistry());
        }
    }

    @Autowired
    private AuctionItemCloseService auctionItemCloseService;

    @Autowired
    private AuctionItemRepository auctionItemRepository;

    @Autowired
    private AuctionRoomRepository auctionRoomRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    /** 이벤트 리스너가 도는 것까지 세면 마감 자체의 쿼리 수가 아니게 된다. */
    @MockitoBean
    private DomainEventPublisher domainEventPublisher;

    @Test
    @DisplayName("낙찰로 마감할 때 쿼리는 네 번 나가고 그중 셋만 락 안이다")
    void soldCloseIssuesFourQueries() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile);
        User bidder = newUser("낙찰자");
        AuctionItem auctionItem = newInProgressItem(auctionRoom, sellerProfile, bidder);

        Statistics statistics = startCounting();

        auctionItemCloseService.close(auctionItem.getAuctionItemId());

        // 아래 검증들이 던지는 쿼리가 섞이기 전에 먼저 걷어 둔다.
        long queryCount = statistics.getPrepareStatementCount();

        assertThat(auctionItemRepository.findStatus(auctionItem.getAuctionItemId()))
                .as("먼저 실제로 낙찰됐는지 확인한다. 유찰이면 낙찰자 조회가 없어 개수가 달라진다")
                .contains(AuctionItemStatus.SOLD);

        assertThat(queryCount)
                .as("락 앞 프로젝션 1 + 락 조회 1 + 낙찰자 조회 1 + UPDATE 1")
                .isEqualTo(4);

        assertThat(loadCountOf(statistics, AuctionRoom.class))
                .as("방은 프로젝션으로 읽으므로 엔티티로 로딩되면 안 된다. 그게 락 안에서 나가던 조회다")
                .isZero();
        assertThat(loadCountOf(statistics, Product.class))
                .as("상품도 마찬가지다")
                .isZero();
        assertThat(loadCountOf(statistics, User.class))
                .as("낙찰자는 락 안에서 읽는 것이 맞다. 입찰마다 바뀌는 값이라 미리 읽으면 안 된다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("유찰로 마감할 때는 낙찰자 조회가 없어 세 번만 나간다")
    void passedCloseIssuesThreeQueries() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile);
        AuctionItem auctionItem = newInProgressItem(auctionRoom, sellerProfile, null);

        Statistics statistics = startCounting();

        auctionItemCloseService.close(auctionItem.getAuctionItemId());

        long queryCount = statistics.getPrepareStatementCount();

        assertThat(auctionItemRepository.findStatus(auctionItem.getAuctionItemId()))
                .contains(AuctionItemStatus.FAILED);

        assertThat(queryCount)
                .as("입찰이 없으면 낙찰자를 읽을 일이 없다")
                .isEqualTo(3);
    }

    /**
     * 통계를 켜고 지금까지 쌓인 것을 지운다. 셋업에서 나간 저장 쿼리가 섞이면 안 되기 때문이다.
     */
    private Statistics startCounting() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        return statistics;
    }

    private long loadCountOf(Statistics statistics, Class<?> entityType) {
        return statistics.getEntityStatistics(entityType.getName()).getLoadCount();
    }

    private AuctionItem newInProgressItem(AuctionRoom auctionRoom, SellerProfile sellerProfile, User leader) {
        LocalDateTime startedAt = LocalDateTime.now().minusMinutes(5);
        return auctionItemRepository.saveAndFlush(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(newProduct(sellerProfile))
                .leaderUser(leader)
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.IN_PROGRESS)
                .startedAt(startedAt)
                .endAt(startedAt.plusMinutes(10))
                .build());
    }

    private Product newProduct(SellerProfile sellerProfile) {
        return productRepository.saveAndFlush(Product.builder()
                .sellerProfile(sellerProfile)
                .name("한정판 피규어")
                .description("미개봉 정품")
                .imageUrl("https://cdn.hot6ix.com/item.png")
                .referenceUrl("https://instagram.com/hot6ix")
                .build());
    }

    private AuctionRoom newRoom(SellerProfile sellerProfile) {
        return auctionRoomRepository.saveAndFlush(AuctionRoom.builder()
                .sellerProfile(sellerProfile)
                .name("승민의 경매방")
                .shareCode("qcount-" + System.nanoTime())
                .status(AuctionRoomStatus.OPEN)
                .bidIncrement(1_000L)
                .build());
    }

    private SellerProfile newSellerProfile() {
        return sellerProfileRepository.saveAndFlush(SellerProfile.builder()
                .user(newUser("승민"))
                .storeName("승민 스토어")
                .build());
    }

    private User newUser(String nickname) {
        return userRepository.saveAndFlush(User.builder()
                .email("qcount-" + System.nanoTime() + "@hot6ix.com")
                .password("password")
                .nickname(nickname)
                .phoneNumber("010-0000-0010")
                .build());
    }
}
