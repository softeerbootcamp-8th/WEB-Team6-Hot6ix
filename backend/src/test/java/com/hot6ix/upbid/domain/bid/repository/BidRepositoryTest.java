package com.hot6ix.upbid.domain.bid.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.bid.dto.BidderRankProjection;
import com.hot6ix.upbid.domain.bid.entity.Bid;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureTestEntityManager
@Import(JpaConfig.class)
class BidRepositoryTest extends AbstractMySqlContainerTest {

    private static final int TOP_BIDDER_LIMIT = 5;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private TestEntityManager entityManager;

    private SellerProfile sellerProfile;
    private AuctionItem auctionItem;

    /**
     * 입찰 하나를 넣으려면 User → SellerProfile → Product·AuctionRoom → AuctionItem이
     * 먼저 있어야 한다. Repository가 없는 것들은 TestEntityManager로 직접 저장한다.
     */
    @BeforeEach
    void setUp() {
        User seller = newUser("seller@hot6ix.com", "승민");

        sellerProfile = entityManager.persist(SellerProfile.builder()
                .user(seller)
                .storeName("승민상점")
                .build());

        auctionItem = newAuctionItem("포토카드");
    }

    private User newUser(String email, String nickname) {
        return entityManager.persist(User.builder()
                .email(email)
                .password("password")
                .nickname(nickname)
                .phoneNumber("010-1234-5678")
                .build());
    }

    private AuctionItem newAuctionItem(String productName) {
        AuctionRoom auctionRoom = entityManager.persist(AuctionRoom.builder()
                .sellerProfile(sellerProfile)
                .name("승민상점 경매방")
                .build());

        Product product = entityManager.persist(Product.builder()
                .sellerProfile(sellerProfile)
                .name(productName)
                .description("미개봉 정품")
                .build());

        return entityManager.persist(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(product)
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.SOLD)
                .endAt(LocalDateTime.of(2026, 7, 29, 21, 0))
                .build());
    }

    private Bid newBid(AuctionItem item, User bidder, long amount) {
        return entityManager.persist(Bid.builder()
                .auctionItem(item)
                .bidder(bidder)
                .amount(amount)
                .build());
    }

    /**
     * {@code acceptedAt}은 {@code @CreatedDate}가 채우므로 테스트에서 직접 정할 수 없다.
     * 같은 금액일 때의 정렬을 검증하려면 시각을 못 박아야 해서 native UPDATE로 덮어쓴다.
     */
    private void forceAcceptedAt(Bid bid, LocalDateTime acceptedAt) {
        entityManager.flush();
        entityManager.getEntityManager()
                .createNativeQuery("update bids set accepted_at = :acceptedAt where bid_id = :bidId")
                .setParameter("acceptedAt", acceptedAt)
                .setParameter("bidId", bid.getBidId())
                .executeUpdate();
    }

    private List<BidderRankProjection> findTopBidders() {
        entityManager.flush();
        return bidRepository.findTopBidders(auctionItem.getAuctionItemId(), TOP_BIDDER_LIMIT);
    }

    @Test
    @DisplayName("같은 입찰자가 여러 번 입찰해도 최고가 한 건만 순위에 오른다")
    void findTopBiddersKeepsOnlyHighestBidPerBidder() {

        User bidder = newUser("bidder@hot6ix.com", "원기");
        newBid(auctionItem, bidder, 11_000L);
        newBid(auctionItem, bidder, 13_000L);
        newBid(auctionItem, bidder, 12_000L);

        List<BidderRankProjection> ranks = findTopBidders();

        assertThat(ranks).hasSize(1);
        assertThat(ranks.getFirst().getBidderUserId()).isEqualTo(bidder.getUserId());
        assertThat(ranks.getFirst().getAmount()).isEqualTo(13_000L);
    }

    @Test
    @DisplayName("순위는 최고 입찰가가 큰 순서로 매겨진다")
    void findTopBiddersOrdersByAmountDesc() {

        User first = newUser("first@hot6ix.com", "일등");
        User second = newUser("second@hot6ix.com", "이등");
        User third = newUser("third@hot6ix.com", "삼등");

        newBid(auctionItem, second, 12_000L);
        newBid(auctionItem, third, 11_000L);
        newBid(auctionItem, first, 15_000L);

        List<BidderRankProjection> ranks = findTopBidders();

        assertThat(ranks)
                .extracting(BidderRankProjection::getBidderUserId)
                .containsExactly(first.getUserId(), second.getUserId(), third.getUserId());
    }

    @Test
    @DisplayName("입찰자가 상한보다 많으면 상위 5명까지만 조회된다")
    void findTopBiddersAppliesLimit() {

        for (int i = 1; i <= 7; i++) {
            newBid(auctionItem, newUser("bidder" + i + "@hot6ix.com", "입찰자" + i), 10_000L + i * 1_000L);
        }

        List<BidderRankProjection> ranks = findTopBidders();

        assertThat(ranks).hasSize(TOP_BIDDER_LIMIT);
        assertThat(ranks)
                .extracting(BidderRankProjection::getAmount)
                .containsExactly(17_000L, 16_000L, 15_000L, 14_000L, 13_000L);
    }

    @Test
    @DisplayName("최고 입찰가가 같으면 먼저 입찰한 사람이 상위다")
    void findTopBiddersBreaksTieByEarlierBid() {

        User earlier = newUser("earlier@hot6ix.com", "먼저");
        User later = newUser("later@hot6ix.com", "나중");

        forceAcceptedAt(newBid(auctionItem, later, 12_000L), LocalDateTime.of(2026, 7, 29, 20, 30));
        forceAcceptedAt(newBid(auctionItem, earlier, 12_000L), LocalDateTime.of(2026, 7, 29, 20, 10));

        List<BidderRankProjection> ranks = findTopBidders();

        assertThat(ranks)
                .extracting(BidderRankProjection::getBidderUserId)
                .containsExactly(earlier.getUserId(), later.getUserId());
    }

    @Test
    @DisplayName("다른 물품의 입찰은 순위에 포함되지 않는다")
    void findTopBiddersExcludesOtherItems() {

        User mine = newUser("mine@hot6ix.com", "내물품");
        User others = newUser("others@hot6ix.com", "남의물품");

        newBid(auctionItem, mine, 11_000L);
        newBid(newAuctionItem("다른물품"), others, 99_000L);

        List<BidderRankProjection> ranks = findTopBidders();

        assertThat(ranks).hasSize(1);
        assertThat(ranks.getFirst().getBidderUserId()).isEqualTo(mine.getUserId());
    }

    @Test
    @DisplayName("입찰이 없으면 빈 목록을 돌려준다")
    void findTopBiddersReturnsEmptyWhenNoBid() {
        assertThat(findTopBidders()).isEmpty();
    }
}
