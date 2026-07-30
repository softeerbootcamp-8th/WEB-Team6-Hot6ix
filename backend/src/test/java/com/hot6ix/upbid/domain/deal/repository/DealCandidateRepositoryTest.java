package com.hot6ix.upbid.domain.deal.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.hibernate.Hibernate;
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
class DealCandidateRepositoryTest extends AbstractMySqlContainerTest {

    @Autowired
    private DealCandidateRepository dealCandidateRepository;

    @Autowired
    private TestEntityManager entityManager;

    private SellerProfile sellerProfile;
    private AuctionItem auctionItem;

    /** 후보는 마감된 물품에만 생기므로 물품 상태는 SOLD로 둔다. */
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

    private DealCandidate newCandidate(AuctionItem item, String bidderEmail, int candidateRank, long bidAmount) {
        return entityManager.persist(DealCandidate.builder()
                .auctionItem(item)
                .bidder(newUser(bidderEmail, "입찰자" + candidateRank))
                .candidateRank(candidateRank)
                .bidAmount(bidAmount)
                .build());
    }

    private List<DealCandidate> findAll() {
        entityManager.flush();
        entityManager.clear();
        return dealCandidateRepository.findAllByAuctionItemId(auctionItem.getAuctionItemId());
    }

    @Test
    @DisplayName("후보는 저장 순서와 무관하게 순위 오름차순으로 조회된다")
    void findAllByAuctionItemIdOrdersByRankAsc() {

        newCandidate(auctionItem, "third@hot6ix.com", 3, 12_000L);
        newCandidate(auctionItem, "first@hot6ix.com", 1, 15_000L);
        newCandidate(auctionItem, "second@hot6ix.com", 2, 13_000L);

        List<DealCandidate> candidates = findAll();

        assertThat(candidates)
                .extracting(DealCandidate::getCandidateRank)
                .containsExactly(1, 2, 3);
        assertThat(candidates)
                .extracting(DealCandidate::getBidAmount)
                .containsExactly(15_000L, 13_000L, 12_000L);
    }

    /**
     * 값만 비교하면 LAZY 프록시가 뒤늦게 초기화돼도 통과해 fetch join이 빠진 것을 못 잡는다.
     */
    @Test
    @DisplayName("조회한 후보의 입찰자는 fetch join으로 함께 로딩된다")
    void findAllByAuctionItemIdFetchesBidder() {

        User bidder = newUser("bidder@hot6ix.com", "원기");
        entityManager.persist(DealCandidate.builder()
                .auctionItem(auctionItem)
                .bidder(bidder)
                .candidateRank(1)
                .bidAmount(15_000L)
                .build());

        List<DealCandidate> candidates = findAll();

        assertThat(candidates).hasSize(1);
        assertThat(Hibernate.isInitialized(candidates.getFirst().getBidder())).isTrue();
        assertThat(candidates.getFirst().getBidder().getUserId()).isEqualTo(bidder.getUserId());
    }

    @Test
    @DisplayName("후보는 WAITING 상태로 저장된다")
    void newCandidateStartsWaiting() {

        newCandidate(auctionItem, "bidder@hot6ix.com", 1, 15_000L);

        assertThat(findAll().getFirst().getStatus()).isEqualTo(DealCandidateStatus.WAITING);
    }

    @Test
    @DisplayName("다른 물품의 후보는 조회되지 않는다")
    void findAllByAuctionItemIdExcludesOtherItems() {

        newCandidate(auctionItem, "mine@hot6ix.com", 1, 15_000L);
        newCandidate(newAuctionItem("다른물품"), "others@hot6ix.com", 1, 99_000L);

        List<DealCandidate> candidates = findAll();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().getBidAmount()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("후보가 없으면 빈 목록을 돌려준다")
    void findAllByAuctionItemIdReturnsEmptyWhenNoCandidate() {
        assertThat(findAll()).isEmpty();
    }

    @Test
    @DisplayName("후보가 있으면 존재 검사가 참이다")
    void existsByAuctionItemIdReturnsTrueWhenCandidateExists() {

        newCandidate(auctionItem, "bidder@hot6ix.com", 1, 15_000L);
        entityManager.flush();

        assertThat(dealCandidateRepository
                .existsByAuctionItem_AuctionItemId(auctionItem.getAuctionItemId())).isTrue();
    }

    @Test
    @DisplayName("다른 물품에만 후보가 있으면 존재 검사가 거짓이다")
    void existsByAuctionItemIdReturnsFalseWhenOnlyOtherItemHasCandidate() {

        newCandidate(newAuctionItem("다른물품"), "others@hot6ix.com", 1, 99_000L);
        entityManager.flush();

        assertThat(dealCandidateRepository
                .existsByAuctionItem_AuctionItemId(auctionItem.getAuctionItemId())).isFalse();
    }
}
