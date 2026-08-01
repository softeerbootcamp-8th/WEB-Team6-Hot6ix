package com.hot6ix.upbid.domain.deal.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.bid.entity.Bid;
import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.time.LocalDateTime;
import java.util.Comparator;
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

    /**
     * 저장 결과를 확인하려고 쓰는 헬퍼다. 후보 목록 조회는 프로덕션에 없어서(#44에서
     * 페이지네이션과 함께 만든다) 여기서 물품으로 걸러 순위대로 정렬한다.
     */
    private List<DealCandidate> findAll() {
        entityManager.flush();
        entityManager.clear();
        return dealCandidateRepository.findAll().stream()
                .filter(candidate -> candidate.getAuctionItem().getAuctionItemId()
                        .equals(auctionItem.getAuctionItemId()))
                .sorted(Comparator.comparing(DealCandidate::getCandidateRank))
                .toList();
    }

    private Bid newBid(AuctionItem item, User bidder, long amount) {
        return entityManager.persist(Bid.builder()
                .auctionItem(item)
                .bidder(bidder)
                .amount(amount)
                .build());
    }

    /** 네이티브 insert는 영속성 컨텍스트를 우회하므로, 입찰을 먼저 DB에 내려보내야 한다. */
    private int insertCandidates() {
        entityManager.flush();
        return dealCandidateRepository.insertCandidatesFromBids(auctionItem.getAuctionItemId());
    }

    @Test
    @DisplayName("후보는 WAITING 상태로 저장된다")
    void newCandidateStartsWaiting() {

        newCandidate(auctionItem, "bidder@hot6ix.com", 1, 15_000L);

        assertThat(findAll().getFirst().getStatus()).isEqualTo(DealCandidateStatus.WAITING);
    }

    @Test
    @DisplayName("후보가 있으면 존재 검사가 참이다")
    void existsByAuctionItemIdReturnsTrueWhenCandidateExists() {

        newCandidate(auctionItem, "bidder@hot6ix.com", 1, 15_000L);
        entityManager.flush();

        assertThat(dealCandidateRepository
                .existsCandidate(auctionItem.getAuctionItemId())).isTrue();
    }

    @Test
    @DisplayName("다른 물품에만 후보가 있으면 존재 검사가 거짓이다")
    void existsByAuctionItemIdReturnsFalseWhenOnlyOtherItemHasCandidate() {

        newCandidate(newAuctionItem("다른물품"), "others@hot6ix.com", 1, 99_000L);
        entityManager.flush();

        assertThat(dealCandidateRepository
                .existsCandidate(auctionItem.getAuctionItemId())).isFalse();
    }

    @Test
    @DisplayName("같은 입찰자가 여러 번 입찰해도 최고가로 후보 한 행만 만든다")
    void insertKeepsOnlyHighestBidPerBidder() {

        User bidder = newUser("bidder@hot6ix.com", "원기");
        newBid(auctionItem, bidder, 11_000L);
        newBid(auctionItem, bidder, 13_000L);
        newBid(auctionItem, bidder, 12_000L);

        assertThat(insertCandidates()).isEqualTo(1);

        List<DealCandidate> candidates = findAll();
        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().getBidder().getUserId()).isEqualTo(bidder.getUserId());
        assertThat(candidates.getFirst().getBidAmount()).isEqualTo(13_000L);
    }

    @Test
    @DisplayName("순위는 금액 내림차순으로 1부터 연속해 매겨진다")
    void insertAssignsContinuousRanksByAmountDesc() {

        newBid(auctionItem, newUser("second@hot6ix.com", "이등"), 13_000L);
        newBid(auctionItem, newUser("third@hot6ix.com", "삼등"), 12_000L);
        newBid(auctionItem, newUser("first@hot6ix.com", "일등"), 15_000L);

        assertThat(insertCandidates()).isEqualTo(3);

        assertThat(findAll())
                .extracting(DealCandidate::getCandidateRank, DealCandidate::getBidAmount)
                .containsExactly(
                        tuple(1, 15_000L),
                        tuple(2, 13_000L),
                        tuple(3, 12_000L));
    }

    /** 순위를 매긴 뒤 걸러내면 번호에 구멍이 생기므로, 조인으로 미리 빠져야 한다. */
    @Test
    @DisplayName("탈퇴 회원은 후보에서 빠지고 남은 사람으로 순위가 이어진다")
    void insertExcludesDeletedBidderWithoutRankGap() {

        User withdrawn = newUser("withdrawn@hot6ix.com", "탈퇴함");
        withdrawn.softDelete(LocalDateTime.of(2026, 7, 29, 20, 0));

        newBid(auctionItem, withdrawn, 99_000L);
        newBid(auctionItem, newUser("active1@hot6ix.com", "활성1"), 13_000L);
        newBid(auctionItem, newUser("active2@hot6ix.com", "활성2"), 12_000L);

        assertThat(insertCandidates()).isEqualTo(2);

        assertThat(findAll())
                .extracting(DealCandidate::getCandidateRank, DealCandidate::getBidAmount)
                .containsExactly(tuple(1, 13_000L), tuple(2, 12_000L));
    }

    @Test
    @DisplayName("다른 물품의 입찰은 후보로 만들지 않는다")
    void insertExcludesOtherItemBids() {

        newBid(auctionItem, newUser("mine@hot6ix.com", "내물품"), 11_000L);
        newBid(newAuctionItem("다른물품"), newUser("others@hot6ix.com", "남의물품"), 99_000L);

        assertThat(insertCandidates()).isEqualTo(1);
        assertThat(findAll()).hasSize(1);
        assertThat(findAll().getFirst().getBidAmount()).isEqualTo(11_000L);
    }

    @Test
    @DisplayName("입찰이 없으면 0을 반환하고 후보를 만들지 않는다")
    void insertReturnsZeroWhenNoBid() {

        assertThat(insertCandidates()).isZero();
        assertThat(findAll()).isEmpty();
    }

    /** SQL에 문자열로 박은 status가 enum과 어긋나는 회귀를 잡는다. */
    @Test
    @DisplayName("후보는 WAITING 상태로, 처리 시각은 비워진 채 저장된다")
    void insertFillsStatusAsWaiting() {

        newBid(auctionItem, newUser("bidder@hot6ix.com", "원기"), 11_000L);

        insertCandidates();

        DealCandidate candidate = findAll().getFirst();
        assertThat(candidate.getStatus()).isEqualTo(DealCandidateStatus.WAITING);
        assertThat(candidate.getCompletedAt()).isNull();
        assertThat(candidate.getFailedAt()).isNull();
    }

    @Test
    @DisplayName("COMPLETED 후보가 있는지 상태로 확인한다")
    void existsByStatusDetectsCompletedCandidate() {

        DealCandidate candidate = newCandidate(auctionItem, "bidder@hot6ix.com", 1, 15_000L);
        entityManager.flush();

        Long auctionItemId = auctionItem.getAuctionItemId();
        assertThat(dealCandidateRepository.existsCompletedCandidate(auctionItemId)).isFalse();

        candidate.complete(LocalDateTime.of(2026, 7, 30, 10, 0));
        entityManager.flush();

        assertThat(dealCandidateRepository.existsCompletedCandidate(auctionItemId)).isTrue();
    }

    @Test
    @DisplayName("현재 낙찰자는 WAITING 중 순위가 가장 낮은 후보다")
    void findFirstWaitingReturnsLowestRank() {

        newCandidate(auctionItem, "first@hot6ix.com", 1, 15_000L)
                .fail(LocalDateTime.of(2026, 7, 30, 10, 0));
        newCandidate(auctionItem, "second@hot6ix.com", 2, 13_000L);
        newCandidate(auctionItem, "third@hot6ix.com", 3, 12_000L);
        entityManager.flush();
        entityManager.clear();

        DealCandidate current = dealCandidateRepository
                .findCurrentWinner(auctionItem.getAuctionItemId())
                .orElseThrow();

        assertThat(current.getCandidateRank()).isEqualTo(2);
        assertThat(Hibernate.isInitialized(current.getBidder())).isTrue();
    }

    @Test
    @DisplayName("다음 차례는 실패한 순위보다 뒤의 WAITING 후보다")
    void findNextWaitingSkipsEarlierRanks() {

        newCandidate(auctionItem, "first@hot6ix.com", 1, 15_000L);
        newCandidate(auctionItem, "second@hot6ix.com", 2, 13_000L);
        entityManager.flush();
        entityManager.clear();

        Long auctionItemId = auctionItem.getAuctionItemId();

        assertThat(dealCandidateRepository.findNextWaitingCandidate(auctionItemId, 1))
                .get()
                .extracting(DealCandidate::getCandidateRank)
                .isEqualTo(2);

        assertThat(dealCandidateRepository.findNextWaitingCandidate(auctionItemId, 2)).isEmpty();
    }

    @Test
    @DisplayName("앞 순번이 대기 중인지로 지금 차례인지를 판단한다")
    void existsWaitingCandidateBeforeDetectsEarlierWaiting() {

        newCandidate(auctionItem, "first@hot6ix.com", 1, 15_000L);
        newCandidate(auctionItem, "second@hot6ix.com", 2, 13_000L);
        entityManager.flush();
        entityManager.clear();

        Long auctionItemId = auctionItem.getAuctionItemId();

        assertThat(dealCandidateRepository.existsWaitingCandidateBefore(auctionItemId, 2)).isTrue();
        assertThat(dealCandidateRepository.existsWaitingCandidateBefore(auctionItemId, 1)).isFalse();
    }

    @Test
    @DisplayName("앞 순번이 이미 처리됐으면 지금이 그 다음 차례다")
    void existsWaitingCandidateBeforeIgnoresResolvedRanks() {

        newCandidate(auctionItem, "first@hot6ix.com", 1, 15_000L)
                .fail(LocalDateTime.of(2026, 7, 30, 10, 0));
        newCandidate(auctionItem, "second@hot6ix.com", 2, 13_000L);
        entityManager.flush();
        entityManager.clear();

        assertThat(dealCandidateRepository
                .existsWaitingCandidateBefore(auctionItem.getAuctionItemId(), 2)).isFalse();
    }

    @Test
    @DisplayName("다른 물품의 후보 ID로는 조회되지 않는다")
    void findByIdAndAuctionItemIdScopesToItem() {

        DealCandidate mine = newCandidate(auctionItem, "mine@hot6ix.com", 1, 15_000L);
        DealCandidate others = newCandidate(newAuctionItem("다른물품"), "others@hot6ix.com", 1, 99_000L);
        entityManager.flush();
        entityManager.clear();

        Long auctionItemId = auctionItem.getAuctionItemId();

        assertThat(dealCandidateRepository.findCandidate(auctionItemId, mine.getDealCandidateId()))
                .isPresent();
        assertThat(dealCandidateRepository.findCandidate(auctionItemId, others.getDealCandidateId()))
                .isEmpty();
    }
}
