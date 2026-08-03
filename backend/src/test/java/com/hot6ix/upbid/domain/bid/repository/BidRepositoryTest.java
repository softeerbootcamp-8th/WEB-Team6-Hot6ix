package com.hot6ix.upbid.domain.bid.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
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
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureTestEntityManager
@Import(JpaConfig.class)
class BidRepositoryTest extends AbstractMySqlContainerTest {

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
                .bidIncrement(1_000L)
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

    private void saveBid(AuctionItem item, User bidder, Long amount) {
        bidRepository.saveAndFlush(Bid.builder()
                .auctionItem(item).bidder(bidder).amount(amount).build());
    }

    @Test
    @DisplayName("같은 물품에 같은 금액으로 두 번 저장하면 unique 제약에 걸린다")
    void rejectsSameAmountOnSameItem() {

        User first = newUser("first@hot6ix.com", "먼저");
        User second = newUser("second@hot6ix.com", "나중");

        bidRepository.saveAndFlush(Bid.builder()
                .auctionItem(auctionItem).bidder(first).amount(12_000L).build());

        assertThatThrownBy(() -> bidRepository.saveAndFlush(Bid.builder()
                .auctionItem(auctionItem).bidder(second).amount(12_000L).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("물품이 다르면 같은 금액이어도 저장된다")
    void allowsSameAmountOnDifferentItems() {

        User bidder = newUser("bidder@hot6ix.com", "입찰자");

        bidRepository.saveAndFlush(Bid.builder()
                .auctionItem(auctionItem).bidder(bidder).amount(12_000L).build());
        Bid saved = bidRepository.saveAndFlush(Bid.builder()
                .auctionItem(newAuctionItem("다른물품")).bidder(bidder).amount(12_000L).build());

        assertThat(saved.getBidId()).isNotNull();
    }

    @Test
    @DisplayName("한 사람이 여러 번 입찰해도 그 사람의 최고가로 한 줄만 나온다")
    void findTopBiddersKeepsHighestPerBidder() {

        User bidder = newUser("bidder@hot6ix.com", "입찰자");
        saveBid(auctionItem, bidder, 11_000L);
        saveBid(auctionItem, bidder, 13_000L);

        List<TopBidderProjection> rows =
                bidRepository.findTopBidders(List.of(auctionItem.getAuctionItemId()), 3);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRankNo()).isEqualTo(1);
        assertThat(rows.get(0).getNickname()).isEqualTo("입찰자");
        assertThat(rows.get(0).getAmount()).isEqualTo(13_000L);
    }

    @Test
    @DisplayName("금액이 높은 순으로 limit 만큼만 나온다")
    void findTopBiddersLimitsToTop3() {

        saveBid(auctionItem, newUser("a@hot6ix.com", "A"), 11_000L);
        saveBid(auctionItem, newUser("b@hot6ix.com", "B"), 14_000L);
        saveBid(auctionItem, newUser("c@hot6ix.com", "C"), 12_000L);
        saveBid(auctionItem, newUser("d@hot6ix.com", "D"), 13_000L);

        List<TopBidderProjection> rows =
                bidRepository.findTopBidders(List.of(auctionItem.getAuctionItemId()), 3);

        assertThat(rows).extracting(TopBidderProjection::getNickname)
                .containsExactly("B", "D", "C");
        assertThat(rows).extracting(TopBidderProjection::getRankNo)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("탈퇴한 회원은 순위에서 빠지고 순위 번호에 구멍이 생기지 않는다")
    void findTopBiddersExcludesDeletedUser() {

        User left = newUser("left@hot6ix.com", "탈퇴자");
        left.softDelete(LocalDateTime.of(2026, 7, 30, 12, 0));

        saveBid(auctionItem, left, 15_000L);
        saveBid(auctionItem, newUser("stay@hot6ix.com", "잔류자"), 12_000L);
        entityManager.flush();

        List<TopBidderProjection> rows =
                bidRepository.findTopBidders(List.of(auctionItem.getAuctionItemId()), 3);

        assertThat(rows).extracting(TopBidderProjection::getNickname)
                .containsExactly("잔류자");
        assertThat(rows.get(0).getRankNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("물품 여러 개를 한 번에 조회하면 물품마다 순위가 따로 매겨진다")
    void findTopBiddersPartitionsByItem() {

        AuctionItem other = newAuctionItem("키링");
        User bidder = newUser("bidder@hot6ix.com", "입찰자");

        saveBid(auctionItem, bidder, 11_000L);
        saveBid(other, bidder, 20_000L);

        List<TopBidderProjection> rows = bidRepository.findTopBidders(
                List.of(auctionItem.getAuctionItemId(), other.getAuctionItemId()), 3);

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> assertThat(row.getRankNo()).isEqualTo(1));
        assertThat(rows).extracting(TopBidderProjection::getAmount)
                .containsExactlyInAnyOrder(11_000L, 20_000L);
    }

    @Test
    @DisplayName("물품 ID 목록이 비어 있으면 빈 목록을 준다")
    void findTopBiddersReturnsEmptyForEmptyIds() {

        assertThat(bidRepository.findTopBidders(List.of(), 3)).isEmpty();
    }
}
