package com.hot6ix.upbid.domain.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionParticipant;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
class AuctionItemRepositoryTest extends AbstractMySqlContainerTest {

    @Autowired
    private AuctionItemRepository auctionItemRepository;

    @Autowired
    private AuctionParticipantRepository auctionParticipantRepository;

    @Autowired
    private TestEntityManager entityManager;

    private SellerProfile sellerProfile;

    /**
     * 물품 하나를 넣으려면 User → SellerProfile → Product와 AuctionRoom이 먼저 있어야 한다.
     * Product·AuctionRoom은 Repository가 없으므로 TestEntityManager로 직접 저장한다.
     */
    @BeforeEach
    void setUp() {
        User user = entityManager.persist(User.builder()
                .email("seller@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build());

        sellerProfile = entityManager.persist(SellerProfile.builder()
                .user(user)
                .storeName("승민상점")
                .build());
    }

    /**
     * 마감 임박 알림의 중복 발송을 막는 조건부 UPDATE 와, 재동기화가 읽는 조회를 실제 DB 에
     * 대고 본다. <b>둘 다 목으로는 검증되지 않는 것이 있어서 여기 둔다</b> — 앞은 조건이 실제로
     * 행을 걸러내는지, 뒤는 같은 타입 칼럼 셋이 제자리에 들어가는지다.
     */
    @Nested
    @DisplayName("마감 임박 알림")
    class ClosingSoon {

        private static final int TRIGGER_SECONDS = 60;
        private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 12, 21, 0);
        private static final LocalDateTime END_AT = LocalDateTime.of(2026, 8, 12, 21, 10);
        /** {@link #END_AT} 물품의 알림 시각. */
        private static final LocalDateTime NOTIFY_AT = END_AT.minusSeconds(TRIGGER_SECONDS);

        @Test
        @DisplayName("자리를 먼저 잡은 하나만 1을 받는다")
        void onlyTheFirstClaimWins() {

            AuctionItem item = newInProgressItem();

            int first = markNotified(item, NOTIFY_AT, NOTIFY_AT);
            int second = markNotified(item, NOTIFY_AT, NOTIFY_AT.plusSeconds(1));

            // 늦게 온 쪽이 0 을 받아야 알림이 한 번만 나간다.
            assertThat(first).isEqualTo(1);
            assertThat(second).isZero();
        }

        @Test
        @DisplayName("연장으로 알림 시각이 밀리면 다시 1을 받는다")
        void claimsAgainAfterSoftCloseExtension() {

            AuctionItem item = newInProgressItem();
            markNotified(item, NOTIFY_AT, NOTIFY_AT);

            int again = markNotified(item, NOTIFY_AT.plusSeconds(30), NOTIFY_AT.plusSeconds(30));

            // 연장 구간을 벗어났다 다시 들어온 경우다. 여기서 막으면 재발송이 죽는다.
            assertThat(again).isEqualTo(1);
        }

        @Test
        @DisplayName("찍은 시각이 실제로 저장된다")
        void storesNotifiedAt() {

            AuctionItem item = newInProgressItem();

            markNotified(item, NOTIFY_AT, NOTIFY_AT);
            entityManager.clear();

            assertThat(entityManager.find(AuctionItem.class, item.getAuctionItemId()).getNotifiedAt())
                    .isEqualTo(NOTIFY_AT);
        }

        @Test
        @DisplayName("재동기화 조회가 마감·시작·알림 시각을 제자리에 담는다")
        void mapsEachTimestampToItsOwnField() {

            AuctionItem item = newInProgressItem();
            markNotified(item, NOTIFY_AT, NOTIFY_AT);
            entityManager.flush();
            entityManager.clear();

            List<InProgressAuctionItemProjection> targets =
                    auctionItemRepository.findScheduleTargets(AuctionItemStatus.IN_PROGRESS);

            // 셋 다 LocalDateTime 이라 순서를 바꿔 넣어도 컴파일과 기동 검증을 그대로 통과한다.
            // 서로 다른 값을 넣어 두는 것이 이 테스트의 전부다.
            assertThat(targets).singleElement().satisfies(target -> {
                assertThat(target.endAt()).isEqualTo(END_AT);
                assertThat(target.startedAt()).isEqualTo(STARTED_AT);
                assertThat(target.notifiedAt()).isEqualTo(NOTIFY_AT);
                assertThat(target.softCloseTriggerSeconds()).isEqualTo(TRIGGER_SECONDS);
            });
        }

        @Test
        @DisplayName("연장 설정이 없는 방의 물품도 조회된다")
        void includesItemWithoutSoftCloseTrigger() {

            AuctionRoom room = entityManager.persist(AuctionRoom.builder()
                    .bidIncrement(1_000L)
                    .sellerProfile(sellerProfile)
                    .name("트리거 없는 방")
                    .build());

            newInProgressItem(room);

            // 마감 예약은 트리거와 무관하게 걸어야 한다. 조인 때문에 빠지면 안 닫힌다.
            assertThat(auctionItemRepository.findScheduleTargets(AuctionItemStatus.IN_PROGRESS))
                    .singleElement()
                    .satisfies(target -> assertThat(target.softCloseTriggerSeconds()).isNull());
        }

        @Test
        @DisplayName("마감 시각이 바뀌었으면 자리를 못 잡는다")
        void failsWhenEndAtChanged() {

            AuctionItem item = newInProgressItem();

            // 읽은 뒤 UPDATE 가 락을 기다리는 사이에 연장이 커밋된 상황이다. 낡은 end_at 을
            // 조건으로 걸어야 여기서 걸린다.
            int claimed = auctionItemRepository.markNotified(item.getAuctionItemId(),
                    AuctionItemStatus.IN_PROGRESS, END_AT.plusSeconds(30), NOTIFY_AT, NOTIFY_AT);

            assertThat(claimed).isZero();
            entityManager.clear();
            assertThat(entityManager.find(AuctionItem.class, item.getAuctionItemId()).getNotifiedAt())
                    .as("실패한 선점이 값을 남기면 정작 알려야 할 때 자기 표시에 막힌다")
                    .isNull();
        }

        @Test
        @DisplayName("마감된 물품에는 자리를 못 잡는다")
        void failsWhenClosed() {

            AuctionItem item = newInProgressItem();
            entityManager.getEntityManager()
                    .createQuery("update AuctionItem ai set ai.status = :status "
                            + "where ai.auctionItemId = :id")
                    .setParameter("status", AuctionItemStatus.SOLD)
                    .setParameter("id", item.getAuctionItemId())
                    .executeUpdate();

            int claimed = markNotified(item, NOTIFY_AT, NOTIFY_AT);

            // 상태를 안 걸면 이미 닫힌 물품에 "곧 마감"이 나간다.
            assertThat(claimed).isZero();
        }

        private int markNotified(AuctionItem item, LocalDateTime notifyAt, LocalDateTime now) {
            return auctionItemRepository.markNotified(item.getAuctionItemId(),
                    AuctionItemStatus.IN_PROGRESS, END_AT, notifyAt, now);
        }

        private AuctionItem newInProgressItem() {
            return newInProgressItem(entityManager.persist(AuctionRoom.builder()
                    .bidIncrement(1_000L)
                    .sellerProfile(sellerProfile)
                    .name("임박 알림 방")
                    .softCloseTriggerSeconds(TRIGGER_SECONDS)
                    .softCloseExtendSeconds(30)
                    .build()));
        }

        private AuctionItem newInProgressItem(AuctionRoom room) {

            AuctionItem item = entityManager.persist(AuctionItem.builder()
                    .auctionRoom(room)
                    .product(newProduct("닌텐도 스위치"))
                    .startingPrice(10_000L)
                    .bidIncrement(1_000L)
                    .status(AuctionItemStatus.IN_PROGRESS)
                    .startedAt(STARTED_AT)
                    .originalEndAt(END_AT)
                    .endAt(END_AT)
                    .build());

            entityManager.flush();

            return item;
        }
    }

    private AuctionRoom newAuctionRoom(String name) {
        return entityManager.persist(AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(sellerProfile)
                .name(name)
                .build());
    }

    private Product newProduct(String name) {
        return entityManager.persist(Product.builder()
                .sellerProfile(sellerProfile)
                .name(name)
                .description("미개봉 정품")
                .imageUrl("https://cdn.hot6ix.com/" + name + ".png")
                .referenceUrl("https://instagram.com/hot6ix/" + name)
                .build());
    }

    private AuctionItem newAuctionItem(AuctionRoom auctionRoom, String productName, AuctionItemStatus status) {
        return newAuctionItem(auctionRoom, newProduct(productName), status);
    }

    private AuctionItem newAuctionItem(AuctionRoom auctionRoom, Product product, AuctionItemStatus status) {
        return entityManager.persist(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(product)
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(status)
                .endAt(LocalDateTime.of(2026, 7, 29, 21, 0))
                .build());
    }

    /** 입찰자. 판매자와 달리 SellerProfile이 없다. 전화번호는 unique가 아니라 겹쳐도 된다. */
    private User newBidder(String email) {
        return entityManager.persist(User.builder()
                .email(email)
                .password("password")
                .nickname("한기")
                .phoneNumber("010-1234-5678")
                .build());
    }

    @Test
    @DisplayName("목록은 상태 우선으로 정렬되고 같은 상태 안에서는 ID 오름차순이다")
    void findSummariesOrdersByStatusThenId() {

        AuctionRoom auctionRoom = newAuctionRoom("승민상점 경매방");

        AuctionItem ready = newAuctionItem(auctionRoom, "대기물품", AuctionItemStatus.READY);
        AuctionItem sold = newAuctionItem(auctionRoom, "낙찰물품", AuctionItemStatus.SOLD);
        AuctionItem inProgressFirst = newAuctionItem(auctionRoom, "진행물품1", AuctionItemStatus.IN_PROGRESS);
        AuctionItem failed = newAuctionItem(auctionRoom, "유찰물품", AuctionItemStatus.FAILED);
        AuctionItem inProgressSecond = newAuctionItem(auctionRoom, "진행물품2", AuctionItemStatus.IN_PROGRESS);
        entityManager.flush();

        List<AuctionItemSummaryResponseDto> summaries =
                auctionItemRepository.findSummaries(auctionRoom.getAuctionRoomId());

        assertThat(summaries)
                .extracting(AuctionItemSummaryResponseDto::auctionItemId)
                .containsExactly(
                        inProgressFirst.getAuctionItemId(),
                        inProgressSecond.getAuctionItemId(),
                        ready.getAuctionItemId(),
                        sold.getAuctionItemId(),
                        failed.getAuctionItemId());
    }

    @Test
    @DisplayName("다른 경매방의 물품은 목록에 포함되지 않는다")
    void findSummariesExcludesOtherRooms() {

        AuctionRoom target = newAuctionRoom("조회할 경매방");
        AuctionRoom other = newAuctionRoom("다른 경매방");

        AuctionItem targetItem = newAuctionItem(target, "조회대상", AuctionItemStatus.READY);
        newAuctionItem(other, "제외대상", AuctionItemStatus.READY);
        entityManager.flush();

        List<AuctionItemSummaryResponseDto> summaries =
                auctionItemRepository.findSummaries(target.getAuctionRoomId());

        assertThat(summaries)
                .extracting(AuctionItemSummaryResponseDto::auctionItemId)
                .containsExactly(targetItem.getAuctionItemId());
    }

    @Test
    @DisplayName("상세 조회는 각 필드를 올바른 자리에 매핑한다")
    void findDetailMapsFields() {

        AuctionRoom auctionRoom = newAuctionRoom("승민상점 경매방");
        AuctionItem item = newAuctionItem(auctionRoom, "한정판피규어", AuctionItemStatus.IN_PROGRESS);
        // 시작가와 현재가를 다르게 둬야 두 필드가 뒤바뀌어도 테스트가 잡아낸다.
        item.applyBid(sellerProfile.getUser(), 12_000L);
        entityManager.flush();

        AuctionItemDetailResponseDto detail =
                auctionItemRepository.findDetail(item.getAuctionItemId()).orElseThrow();

        assertThat(detail.auctionItemId()).isEqualTo(item.getAuctionItemId());
        assertThat(detail.auctionRoomId()).isEqualTo(auctionRoom.getAuctionRoomId());
        assertThat(detail.productName()).isEqualTo("한정판피규어");
        assertThat(detail.description()).isEqualTo("미개봉 정품");
        assertThat(detail.imageUrl()).isEqualTo("https://cdn.hot6ix.com/한정판피규어.png");
        assertThat(detail.referenceUrl()).isEqualTo("https://instagram.com/hot6ix/한정판피규어");
        assertThat(detail.startingPrice()).isEqualTo(10_000L);
        assertThat(detail.currentPrice()).isEqualTo(12_000L);
        assertThat(detail.bidIncrement()).isEqualTo(1_000L);
        assertThat(detail.status()).isEqualTo(AuctionItemStatus.IN_PROGRESS);
        assertThat(detail.endAt()).isEqualTo(LocalDateTime.of(2026, 7, 29, 21, 0));
    }

    @Test
    @DisplayName("결과 조회는 낙찰자를 함께 담고, 입찰이 없던 물품도 빠뜨리지 않는다")
    void findResultsMapsWinnerAndKeepsItemsWithoutBids() {

        User bidder = entityManager.persist(User.builder()
                .email("bidder@hot6ix.com")
                .password("password")
                .nickname("스니커홀릭")
                .phoneNumber("010-9999-8888")
                .build());

        AuctionRoom auctionRoom = newAuctionRoom("승민상점 경매방");

        AuctionItem sold = newAuctionItem(auctionRoom, "낙찰물품", AuctionItemStatus.SOLD);
        sold.applyBid(bidder, 85_000L);
        // 입찰이 한 번도 없어 leaderUser가 없다. left join이 아니면 이 행이 통째로 사라진다.
        AuctionItem failed = newAuctionItem(auctionRoom, "유찰물품", AuctionItemStatus.FAILED);
        entityManager.flush();

        List<AuctionItemResultProjection> results =
                auctionItemRepository.findResults(auctionRoom.getAuctionRoomId());

        assertThat(results)
                .extracting(AuctionItemResultProjection::auctionItemId)
                .containsExactly(sold.getAuctionItemId(), failed.getAuctionItemId());

        AuctionItemResultProjection soldRow = results.getFirst();
        assertThat(soldRow.productName()).isEqualTo("낙찰물품");
        assertThat(soldRow.imageUrl()).isEqualTo("https://cdn.hot6ix.com/낙찰물품.png");
        assertThat(soldRow.status()).isEqualTo(AuctionItemStatus.SOLD);
        assertThat(soldRow.currentPrice()).isEqualTo(85_000L);
        assertThat(soldRow.leaderNickname()).isEqualTo("스니커홀릭");

        AuctionItemResultProjection failedRow = results.getLast();
        assertThat(failedRow.status()).isEqualTo(AuctionItemStatus.FAILED);
        assertThat(failedRow.leaderNickname()).isNull();
    }

    @Test
    @DisplayName("결과 조회에 다른 경매방의 물품은 섞이지 않는다")
    void findResultsExcludesOtherRooms() {

        AuctionRoom target = newAuctionRoom("조회할 경매방");
        AuctionRoom other = newAuctionRoom("다른 경매방");

        AuctionItem targetItem = newAuctionItem(target, "조회대상", AuctionItemStatus.SOLD);
        newAuctionItem(other, "제외대상", AuctionItemStatus.SOLD);
        entityManager.flush();

        List<AuctionItemResultProjection> results =
                auctionItemRepository.findResults(target.getAuctionRoomId());

        assertThat(results)
                .extracting(AuctionItemResultProjection::auctionItemId)
                .containsExactly(targetItem.getAuctionItemId());
    }

    /**
     * 트랜잭션이 둘 필요해 락의 실제 차단은 볼 수 없어 락 모드만 단정한다. {@code clear()}가
     * 필요한 이유는 같은 트랜잭션에서 INSERT한 엔티티가 이미 쓰기 상태로 표시돼 있기 때문이다.
     */
    @Test
    @DisplayName("락 조회는 물품에 쓰기 락을 걸고 그대로 돌려준다")
    void findByIdForUpdateLocksItem() {

        AuctionItem item = newAuctionItem(newAuctionRoom("승민상점 경매방"), "낙찰물품", AuctionItemStatus.SOLD);
        Long auctionItemId = item.getAuctionItemId();
        entityManager.flush();
        entityManager.clear();

        AuctionItem locked = auctionItemRepository.findByIdForUpdate(auctionItemId).orElseThrow();

        assertThat(locked.getAuctionItemId()).isEqualTo(auctionItemId);
        assertThat(locked.getStatus()).isEqualTo(AuctionItemStatus.SOLD);
        assertThat(entityManager.getEntityManager().getLockMode(locked))
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    @DisplayName("없는 물품을 락 조회하면 빈 값을 돌려준다")
    void findByIdForUpdateReturnsEmptyWhenNotFound() {
        assertThat(auctionItemRepository.findByIdForUpdate(999L)).isEmpty();
    }

    /**
     * 판매자 본인 입찰 차단이 이 조회에 걸려 있다. 경로가 바뀌면 차단 대상이 바뀌므로
     * 경매방을 거쳐 회원까지 닿는지 실제 쿼리로 확인한다.
     */
    @Test
    @DisplayName("물품의 판매자 회원 ID를 경매방을 거쳐 조회한다")
    void findSellerUserIdFollowsAuctionRoom() {

        AuctionItem item = newAuctionItem(newAuctionRoom("승민상점 경매방"), "한정판피규어",
                AuctionItemStatus.IN_PROGRESS);
        entityManager.flush();
        entityManager.clear();

        assertThat(auctionItemRepository.findSellerUserId(item.getAuctionItemId()))
                .contains(sellerProfile.getUser().getUserId());
    }

    @Test
    @DisplayName("없는 물품의 판매자를 조회하면 빈 값을 돌려준다")
    void findSellerUserIdReturnsEmptyWhenNotFound() {
        assertThat(auctionItemRepository.findSellerUserId(999L)).isEmpty();
    }

    /**
     * 경매방에 판매자가 없으면 조인이 걸러 빈 값이 된다. Service가 이걸 물품 없음으로 바꿔
     * 입찰을 거절하므로, 판매자를 확인할 수 없는 물품은 통과하지 않는다.
     */
    @Test
    @DisplayName("경매방에 판매자가 없으면 판매자 조회가 빈 값이다")
    void findSellerUserIdReturnsEmptyWhenRoomHasNoSeller() {

        AuctionRoom roomWithoutSeller = entityManager.persist(AuctionRoom.builder()
                .bidIncrement(1_000L)
                .name("판매자 없는 방")
                .build());
        AuctionItem item = newAuctionItem(roomWithoutSeller, "주인없는물품", AuctionItemStatus.IN_PROGRESS);
        entityManager.flush();
        entityManager.clear();

        assertThat(auctionItemRepository.findSellerUserId(item.getAuctionItemId())).isEmpty();
    }

    /**
     * 유찰·거래 전원 실패 상품 재등록을 지원하면서 {@code product_id} unique 제약을 없앴다
     * ({@code AuctionItem} javadoc 참고). 예전엔 이 제약이 동시 등록 두 건이 함께 통과하는
     * 것을 막는 최후 방어선이었지만, 이제 그 역할은 상품 행 비관적 락(Service 계층)이 한다.
     * 여기서는 <b>DB가 더 이상 막지 않는다</b>는 것만 확인한다.
     */
    @Test
    @DisplayName("같은 상품을 두 물품으로 올릴 수 있다 (재등록 지원으로 unique 제약을 없앴다)")
    void productIdIsNoLongerUnique() {

        Product product = newProduct("재등록상품2");
        AuctionItem first = newAuctionItem(newAuctionRoom("첫 번째 방"), product, AuctionItemStatus.FAILED);
        entityManager.flush();

        AuctionRoom secondRoom = newAuctionRoom("두 번째 방");
        AuctionItem second = newAuctionItem(secondRoom, product, AuctionItemStatus.READY);
        entityManager.flush();

        assertThat(second.getAuctionItemId()).isNotEqualTo(first.getAuctionItemId());
        assertThat(auctionItemRepository.findAll())
                .filteredOn(ai -> ai.getProduct().getProductId().equals(product.getProductId()))
                .hasSize(2);
    }

    /**
     * unique 제약을 없애면서 {@code product_id}의 유일한 인덱스도 함께 사라진다 — 재등록
     * 판정({@code findBlockedProductIdsIn})과 "최신 물품" 상관 서브쿼리가 이 인덱스에
     * 의존하므로 명시적으로 다시 걸어 뒀다({@code AuctionItem}의 {@code @Table(indexes=...)}).
     * InnoDB가 FK 때문에 자동으로 인덱스를 만들어 주더라도 이름이 자동 생성이라 의존할 수
     * 없으므로, 우리가 붙인 이름의 인덱스가 실제로 있는지 확인한다.
     */
    @Test
    @DisplayName("product_id에 명시적 인덱스가 남아 있다")
    void productIdIndexExists() {

        Query query = entityManager.getEntityManager().createNativeQuery(
                "select count(*) from information_schema.statistics "
                        + "where table_schema = database() "
                        + "  and table_name = 'auction_items' "
                        + "  and index_name = 'idx_auction_items_product_id'");

        Number count = (Number) query.getSingleResult();
        assertThat(count.longValue()).isGreaterThan(0);
    }

    @Test
    @DisplayName("물품을 빼면 그 상품을 다시 다른 경매방에 올릴 수 있다")
    void productCanBeRelistedAfterRemoval() {

        Product product = newProduct("재등록상품");
        AuctionItem first = newAuctionItem(newAuctionRoom("첫 번째 방"), product, AuctionItemStatus.READY);
        entityManager.flush();

        auctionItemRepository.delete(first);
        entityManager.flush();

        assertThat(auctionItemRepository.findBlockedProductIdsIn(List.of(product.getProductId()))).isEmpty();

        newAuctionItem(newAuctionRoom("두 번째 방"), product, AuctionItemStatus.READY);
        entityManager.flush();

        assertThat(auctionItemRepository.findBlockedProductIdsIn(List.of(product.getProductId())))
                .containsExactly(product.getProductId());
    }

    /**
     * 물품 시작의 "방당 동시 3개" 검사가 이 개수에 걸려 있다. 다른 방의 진행 중 물품이나 같은 방의
     * 다른 상태가 섞여 세지면 제한이 엉뚱한 시점에 걸리므로 실제 쿼리로 확인한다.
     */
    @Test
    @DisplayName("진행 중 물품 개수는 같은 경매방의 IN_PROGRESS만 센다")
    void countByRoomAndStatusCountsOnlyMatchingItems() {

        AuctionRoom auctionRoom = newAuctionRoom("승민상점 경매방");
        newAuctionItem(auctionRoom, "진행물품1", AuctionItemStatus.IN_PROGRESS);
        newAuctionItem(auctionRoom, "진행물품2", AuctionItemStatus.IN_PROGRESS);
        newAuctionItem(auctionRoom, "대기물품", AuctionItemStatus.READY);
        newAuctionItem(auctionRoom, "낙찰물품", AuctionItemStatus.SOLD);
        newAuctionItem(auctionRoom, "유찰물품", AuctionItemStatus.FAILED);

        AuctionRoom otherRoom = newAuctionRoom("다른 경매방");
        newAuctionItem(otherRoom, "남의진행물품", AuctionItemStatus.IN_PROGRESS);
        entityManager.flush();

        assertThat(auctionItemRepository.countByAuctionRoom_AuctionRoomIdAndStatus(
                auctionRoom.getAuctionRoomId(), AuctionItemStatus.IN_PROGRESS)).isEqualTo(2);
        assertThat(auctionItemRepository.countByAuctionRoom_AuctionRoomIdAndStatus(
                otherRoom.getAuctionRoomId(), AuctionItemStatus.IN_PROGRESS)).isEqualTo(1);
    }

    @Test
    @DisplayName("진행 중 물품이 없는 경매방은 개수가 0이다")
    void countByRoomAndStatusReturnsZeroWhenNone() {

        AuctionRoom auctionRoom = newAuctionRoom("승민상점 경매방");
        newAuctionItem(auctionRoom, "대기물품", AuctionItemStatus.READY);
        entityManager.flush();

        assertThat(auctionItemRepository.countByAuctionRoom_AuctionRoomIdAndStatus(
                auctionRoom.getAuctionRoomId(), AuctionItemStatus.IN_PROGRESS)).isZero();
    }

    @Test
    @DisplayName("거래 현황용 조회는 마감된 물품만 최근 마감 순으로 돌려준다")
    void findClosedItemsReturnsOnlyClosedNewestFirst() {

        AuctionRoom auctionRoom = newAuctionRoom("승민상점 경매방");

        AuctionItem sold = entityManager.persist(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(newProduct("낙찰물품"))
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.SOLD)
                .endAt(LocalDateTime.of(2026, 7, 30, 21, 0))
                .build());
        AuctionItem failed = entityManager.persist(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(newProduct("유찰물품"))
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.FAILED)
                .endAt(LocalDateTime.of(2026, 7, 28, 21, 0))
                .build());
        newAuctionItem(auctionRoom, "진행물품", AuctionItemStatus.IN_PROGRESS);
        newAuctionItem(auctionRoom, "대기물품", AuctionItemStatus.READY);
        entityManager.flush();

        assertThat(auctionItemRepository.findClosedItems(auctionRoom.getAuctionRoomId()))
                .extracting(ClosedAuctionItemProjection::auctionItemId,
                        ClosedAuctionItemProjection::productName)
                .containsExactly(
                        tuple(sold.getAuctionItemId(), "낙찰물품"),
                        tuple(failed.getAuctionItemId(), "유찰물품"));
    }

    @Test
    @DisplayName("이 방에 약관 동의를 한 회원이면 참여자로 본다")
    void existsParticipantReturnsTrueForAgreedParticipant() {

        AuctionRoom auctionRoom = newAuctionRoom("승민상점 경매방");
        AuctionItem auctionItem = newAuctionItem(auctionRoom, "한정판피규어", AuctionItemStatus.IN_PROGRESS);
        User bidder = newBidder("buyer1@hot6ix.com");
        entityManager.flush();

        auctionParticipantRepository.recordAgreement(
                auctionRoom.getAuctionRoomId(), bidder.getUserId(), "v1");

        assertThat(auctionItemRepository.existsParticipant(
                auctionItem.getAuctionItemId(), bidder.getUserId())).isTrue();
    }

    @Test
    @DisplayName("참여 행이 있어도 agreed_at이 비어 있으면 참여자로 보지 않는다")
    void existsParticipantReturnsFalseWhenNotAgreed() {

        AuctionRoom auctionRoom = newAuctionRoom("승민상점 경매방");
        AuctionItem auctionItem = newAuctionItem(auctionRoom, "한정판피규어", AuctionItemStatus.IN_PROGRESS);
        User bidder = newBidder("buyer2@hot6ix.com");

        // 빌더에 agreedAt이 없어 null로 들어간다. 동의 없이 행만 생긴 상태를 만든다.
        entityManager.persist(AuctionParticipant.builder()
                .auctionRoom(auctionRoom)
                .user(bidder)
                .build());
        entityManager.flush();

        assertThat(auctionItemRepository.existsParticipant(
                auctionItem.getAuctionItemId(), bidder.getUserId())).isFalse();
    }

    @Test
    @DisplayName("다른 방에 동의한 회원은 이 방의 물품에 대해 참여자가 아니다")
    void existsParticipantReturnsFalseForOtherRoomParticipant() {

        AuctionRoom auctionRoom = newAuctionRoom("승민상점 경매방");
        AuctionRoom otherRoom = newAuctionRoom("다른 경매방");
        AuctionItem auctionItem = newAuctionItem(auctionRoom, "한정판피규어", AuctionItemStatus.IN_PROGRESS);
        User bidder = newBidder("buyer3@hot6ix.com");
        entityManager.flush();

        auctionParticipantRepository.recordAgreement(
                otherRoom.getAuctionRoomId(), bidder.getUserId(), "v1");

        assertThat(auctionItemRepository.existsParticipant(
                auctionItem.getAuctionItemId(), bidder.getUserId())).isFalse();
    }

}
