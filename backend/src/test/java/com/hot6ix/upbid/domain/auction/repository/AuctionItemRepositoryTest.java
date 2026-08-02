package com.hot6ix.upbid.domain.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
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
class AuctionItemRepositoryTest extends AbstractMySqlContainerTest {

    @Autowired
    private AuctionItemRepository auctionItemRepository;

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
    @DisplayName("READY 상태의 AuctionItem만 있으면 시작된 적 없는 것으로 본다")
    void existsByProductAndStatusNot_false_whenOnlyReady() {

        AuctionRoom auctionRoom = newAuctionRoom("승민상점 경매방");
        Product product = newProduct("대기상품");
        newAuctionItem(auctionRoom, product, AuctionItemStatus.READY);
        entityManager.flush();

        boolean started = auctionItemRepository.existsByProduct_ProductIdAndStatusNot(
                product.getProductId(), AuctionItemStatus.READY);

        assertThat(started).isFalse();
    }

    @Test
    @DisplayName("READY가 아닌 AuctionItem이 하나라도 있으면 시작된 적 있는 것으로 본다")
    void existsByProductAndStatusNot_true_whenNotReady() {

        AuctionRoom auctionRoom = newAuctionRoom("승민상점 경매방");
        Product product = newProduct("낙찰상품");
        newAuctionItem(auctionRoom, product, AuctionItemStatus.SOLD);
        entityManager.flush();

        boolean started = auctionItemRepository.existsByProduct_ProductIdAndStatusNot(
                product.getProductId(), AuctionItemStatus.READY);

        assertThat(started).isTrue();
    }

    @Test
    @DisplayName("연결된 AuctionItem이 없으면 시작된 적 없는 것으로 본다")
    void existsByProductAndStatusNot_false_whenNoAuctionItem() {

        Product product = newProduct("미등록상품");
        entityManager.flush();

        boolean started = auctionItemRepository.existsByProduct_ProductIdAndStatusNot(
                product.getProductId(), AuctionItemStatus.READY);

        assertThat(started).isFalse();
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

    @Test
    @DisplayName("경매방에 올라간 상품이면 상태와 무관하게 존재 검사가 참이다")
    void existsByProductIdIgnoresStatus() {

        AuctionRoom auctionRoom = newAuctionRoom("승민상점 경매방");
        Product ready = newProduct("대기상품");
        Product sold = newProduct("낙찰상품");
        newAuctionItem(auctionRoom, ready, AuctionItemStatus.READY);
        newAuctionItem(auctionRoom, sold, AuctionItemStatus.SOLD);
        entityManager.flush();

        assertThat(auctionItemRepository.existsByProduct_ProductId(ready.getProductId())).isTrue();
        assertThat(auctionItemRepository.existsByProduct_ProductId(sold.getProductId())).isTrue();
    }

    @Test
    @DisplayName("어느 경매방에도 없는 상품이면 존재 검사가 거짓이다")
    void existsByProductIdIsFalseWhenNotListed() {

        Product product = newProduct("미등록상품");
        entityManager.flush();

        assertThat(auctionItemRepository.existsByProduct_ProductId(product.getProductId())).isFalse();
    }

    /**
     * 서비스의 존재 검사는 읽고-검사하고-쓰는 흐름이라 동시 요청 두 건이 함께 통과할 수 있다.
     * 최후 방어선인 unique 제약이 실제 스키마에 붙었는지는 mock으로 확인할 수 없어 여기서 본다.
     *
     * <p>ID 전략이 IDENTITY라 {@code persist()} 시점에 INSERT가 바로 나가므로, 제약 위반도
     * 뒤따르는 {@code flush()}가 아니라 그 자리에서 터진다.
     */
    @Test
    @DisplayName("같은 상품을 두 물품으로 올리면 unique 제약에 걸린다")
    void productIdIsUnique() {

        Product product = newProduct("중복상품");
        newAuctionItem(newAuctionRoom("첫 번째 방"), product, AuctionItemStatus.READY);
        entityManager.flush();

        AuctionRoom secondRoom = newAuctionRoom("두 번째 방");

        assertThatThrownBy(() -> newAuctionItem(secondRoom, product, AuctionItemStatus.READY))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("uk_auction_items_product_id");
    }

    @Test
    @DisplayName("물품을 빼면 그 상품을 다시 다른 경매방에 올릴 수 있다")
    void productCanBeRelistedAfterRemoval() {

        Product product = newProduct("재등록상품");
        AuctionItem first = newAuctionItem(newAuctionRoom("첫 번째 방"), product, AuctionItemStatus.READY);
        entityManager.flush();

        auctionItemRepository.delete(first);
        entityManager.flush();

        assertThat(auctionItemRepository.existsByProduct_ProductId(product.getProductId())).isFalse();

        newAuctionItem(newAuctionRoom("두 번째 방"), product, AuctionItemStatus.READY);
        entityManager.flush();

        assertThat(auctionItemRepository.existsByProduct_ProductId(product.getProductId())).isTrue();
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
}
