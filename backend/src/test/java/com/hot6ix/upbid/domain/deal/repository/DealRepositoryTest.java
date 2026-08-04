package com.hot6ix.upbid.domain.deal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus;
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
class DealRepositoryTest extends AbstractMySqlContainerTest {

    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 7, 29, 21, 0);

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User seller;
    private SellerProfile sellerProfile;
    private AuctionRoom auctionRoom;

    @BeforeEach
    void setUp() {
        seller = newUser("seller@hot6ix.com", "승민");
        sellerProfile = entityManager.persist(SellerProfile.builder()
                .user(seller)
                .storeName("승민상점")
                .build());
        auctionRoom = entityManager.persist(AuctionRoom.builder()
                .sellerProfile(sellerProfile)
                .bidIncrement(1_000L)
                .name("승민상점 경매방")
                .build());
    }

    private User newUser(String email, String nickname) {
        return entityManager.persist(User.builder()
                .email(email)
                .password("password")
                .nickname(nickname)
                .phoneNumber("010-1234-5678")
                .build());
    }

    private AuctionItem newItem(String productName, AuctionItemStatus status, LocalDateTime endAt) {
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
                .status(status)
                .endAt(endAt)
                .build());
    }

    private DealCandidate newCandidate(AuctionItem item, User bidder, int rank, long amount) {
        return entityManager.persist(DealCandidate.builder()
                .auctionItem(item)
                .bidder(bidder)
                .candidateRank(rank)
                .bidAmount(amount)
                .build());
    }

    private List<DealSummaryProjection> findDeals(User user) {
        entityManager.flush();
        entityManager.clear();
        return dealRepository.findDeals(user.getUserId());
    }

    @Test
    @DisplayName("판매 건은 내 경매방의 마감된 물품만 나온다")
    void findDealsReturnsClosedItemsOfMyRoom() {

        newItem("낙찰물품", AuctionItemStatus.SOLD, END_AT);
        newItem("유찰물품", AuctionItemStatus.FAILED, END_AT);
        newItem("진행물품", AuctionItemStatus.IN_PROGRESS, END_AT);
        newItem("대기물품", AuctionItemStatus.READY, END_AT);

        assertThat(findDeals(seller))
                .extracting(DealSummaryProjection::getProductName,
                        DealSummaryProjection::getSellerRow)
                .containsExactlyInAnyOrder(tuple("낙찰물품", 1), tuple("유찰물품", 1));
    }

    @Test
    @DisplayName("구매 건은 내가 후보로 오른 물품이고 내 입찰가가 금액이 된다")
    void findDealsReturnsMyCandidacies() {

        AuctionItem item = newItem("포토카드", AuctionItemStatus.SOLD, END_AT);
        User buyer = newUser("buyer@hot6ix.com", "원기");
        newCandidate(item, buyer, 2, 13_000L);
        newCandidate(item, newUser("other@hot6ix.com", "남"), 1, 15_000L);

        List<DealSummaryProjection> deals = findDeals(buyer);

        assertThat(deals).hasSize(1);
        DealSummaryProjection deal = deals.getFirst();
        assertThat(deal.getSellerRow()).isZero();
        assertThat(deal.getAmount()).isEqualTo(13_000L);
        assertThat(deal.getProductId()).as("내가 산 물건은 내 상품이 아니다").isNull();
        // 구매자는 이 ID로 판매자 프로필을 조회해 연락처를 얻는다.
        assertThat(deal.getSellerProfileId()).isEqualTo(sellerProfile.getSellerProfileId());
    }

    @Test
    @DisplayName("판매자가 자기 물품을 보면 거래 상대는 순서를 기다리는 최저 순위 후보다")
    void findDealsPicksWaitingPartnerForSeller() {

        AuctionItem item = newItem("포토카드", AuctionItemStatus.SOLD, END_AT);
        newCandidate(item, newUser("first@hot6ix.com", "일등"), 1, 15_000L)
                .fail(LocalDateTime.of(2026, 7, 30, 10, 0));
        newCandidate(item, newUser("second@hot6ix.com", "이등"), 2, 13_000L);
        newCandidate(item, newUser("third@hot6ix.com", "삼등"), 3, 12_000L);

        assertThat(findDeals(seller).getFirst().getPartnerNickname())
                .as("실패한 1등이 아니라 지금 차례인 2등이어야 한다")
                .isEqualTo("이등");
    }

    @Test
    @DisplayName("성사된 후보가 있으면 그 사람이 거래 상대이고 상태가 COMPLETED다")
    void findDealsPicksCompletedPartner() {

        AuctionItem item = newItem("포토카드", AuctionItemStatus.SOLD, END_AT);
        newCandidate(item, newUser("first@hot6ix.com", "일등"), 1, 15_000L)
                .complete(LocalDateTime.of(2026, 7, 30, 10, 0));
        newCandidate(item, newUser("second@hot6ix.com", "이등"), 2, 13_000L);

        DealSummaryProjection deal = findDeals(seller).getFirst();

        assertThat(deal.getPartnerNickname()).isEqualTo("일등");
        assertThat(deal.getDealCompleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("유찰 물품은 후보가 없어 거래 상대도 없다")
    void findDealsLeavesPartnerNullWhenUnsold() {

        newItem("유찰물품", AuctionItemStatus.FAILED, END_AT);

        DealSummaryProjection deal = findDeals(seller).getFirst();

        assertThat(deal.getPartnerNickname()).isNull();
        assertThat(deal.getItemStatus()).isEqualTo(AuctionItemStatus.FAILED.name());
        assertThat(deal.getDealCompleted()).isZero();
    }

    /** 판매 쪽과 구매 쪽은 원천이 다른 UNION이라, 섞였을 때 정렬이 유지되는지가 회귀 지점이다. */
    @Test
    @DisplayName("판 것과 산 것이 한 목록에 최근 마감 순으로 모인다")
    void findDealsMergesBothSidesNewestFirst() {

        newItem("내가판물품", AuctionItemStatus.SOLD, LocalDateTime.of(2026, 7, 28, 21, 0));

        AuctionItem bought = otherSellersItem("내가산물품", LocalDateTime.of(2026, 7, 30, 21, 0));
        newCandidate(bought, seller, 1, 15_000L);

        assertThat(findDeals(seller))
                .extracting(DealSummaryProjection::getProductName,
                        DealSummaryProjection::getSellerRow)
                .containsExactly(
                        tuple("내가산물품", 0),
                        tuple("내가판물품", 1));
    }

    /** 남의 경매방 물품. 내가 판 것과 산 것이 섞이는 상황을 만들려면 판매자가 달라야 한다. */
    private AuctionItem otherSellersItem(String productName, LocalDateTime endAt) {

        SellerProfile otherProfile = entityManager.persist(SellerProfile.builder()
                .user(newUser("other-seller@hot6ix.com", "다른판매자"))
                .storeName("다른상점")
                .build());
        AuctionRoom otherRoom = entityManager.persist(AuctionRoom.builder()
                .sellerProfile(otherProfile)
                .bidIncrement(1_000L)
                .name("다른상점 경매방")
                .build());
        Product product = entityManager.persist(Product.builder()
                .sellerProfile(otherProfile)
                .name(productName)
                .description("미개봉 정품")
                .build());

        return entityManager.persist(AuctionItem.builder()
                .auctionRoom(otherRoom)
                .product(product)
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.SOLD)
                .endAt(endAt)
                .build());
    }

    @Test
    @DisplayName("경매방을 지워도 그 방에서 있었던 거래는 내역에 남는다")
    void findDealsKeepsDealsOfDeletedRoom() {

        newItem("낙찰물품", AuctionItemStatus.SOLD, END_AT);
        auctionRoom.softDelete(LocalDateTime.of(2026, 7, 30, 10, 0));

        assertThat(findDeals(seller))
                .extracting(DealSummaryProjection::getProductName)
                .containsExactly("낙찰물품");
    }

    /** 상대가 나갔다고 내 기록이 없어지면 안 된다. 구매자 쪽이 판매자에 매달려 있던 부분이다. */
    @Test
    @DisplayName("판매자가 탈퇴하고 프로필을 지워도 구매 내역은 남는다")
    void findDealsKeepsPurchaseWhenSellerWithdraws() {

        AuctionItem item = newItem("포토카드", AuctionItemStatus.SOLD, END_AT);
        User buyer = newUser("buyer@hot6ix.com", "원기");
        newCandidate(item, buyer, 1, 15_000L);

        LocalDateTime withdrawnAt = LocalDateTime.of(2026, 7, 30, 10, 0);
        seller.softDelete(withdrawnAt);
        sellerProfile.softDelete(withdrawnAt);

        assertThat(findDeals(buyer))
                .extracting(DealSummaryProjection::getProductName,
                        DealSummaryProjection::getPartnerNickname)
                .containsExactly(tuple("포토카드", "승민"));
    }

    /** 후보 목록에서 탈퇴자를 건너뛰므로, 거래 상대 표시도 같은 사람을 가리켜야 한다. */
    @Test
    @DisplayName("거래 상대가 탈퇴하면 다음 순위가 상대로 올라온다")
    void findDealsSkipsWithdrawnPartner() {

        AuctionItem item = newItem("포토카드", AuctionItemStatus.SOLD, END_AT);
        User withdrawn = newUser("gone@hot6ix.com", "탈퇴함");
        newCandidate(item, withdrawn, 1, 15_000L);
        newCandidate(item, newUser("second@hot6ix.com", "이등"), 2, 13_000L);
        withdrawn.softDelete(LocalDateTime.of(2026, 7, 30, 10, 0));

        assertThat(findDeals(seller).getFirst().getPartnerNickname()).isEqualTo("이등");
    }

    @Test
    @DisplayName("거래가 없으면 빈 목록이다")
    void findDealsReturnsEmpty() {
        assertThat(findDeals(newUser("nobody@hot6ix.com", "구경꾼"))).isEmpty();
    }

    /** 상한을 넘는 거래는 오래된 것부터 조용히 잘린다. 상한 자체가 동작하는지를 작은 값으로 본다. */
    @Test
    @DisplayName("거래 내역은 상한을 넘으면 최근 것까지만 돌려준다")
    void findDealsStopsAtLimit() {

        newItem("오래된물품", AuctionItemStatus.SOLD, LocalDateTime.of(2026, 7, 27, 21, 0));
        newItem("최근물품", AuctionItemStatus.SOLD, LocalDateTime.of(2026, 7, 30, 21, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(dealRepository.findDeals(seller.getUserId(),
                AuctionItemStatus.SOLD.name(), AuctionItemStatus.FAILED.name(),
                DealCandidateStatus.COMPLETED.name(), DealCandidateStatus.WAITING.name(),
                DealCandidateStatus.FAILED.name(), 1))
                .extracting(DealSummaryProjection::getProductName)
                .containsExactly("최근물품");
    }
}
