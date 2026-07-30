package com.hot6ix.upbid.domain.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
        return entityManager.persist(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(newProduct(productName))
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
        entityManager.flush();

        AuctionItemDetailResponseDto detail =
                auctionItemRepository.findDetail(item.getAuctionItemId()).orElseThrow();

        assertThat(detail.auctionItemId()).isEqualTo(item.getAuctionItemId());
        assertThat(detail.auctionRoomId()).isEqualTo(auctionRoom.getAuctionRoomId());
        assertThat(detail.productName()).isEqualTo("한정판피규어");
        assertThat(detail.description()).isEqualTo("미개봉 정품");
        assertThat(detail.imageUrl()).isEqualTo("https://cdn.hot6ix.com/한정판피규어.png");
        assertThat(detail.referenceUrl()).isEqualTo("https://instagram.com/hot6ix/한정판피규어");
        assertThat(detail.currentPrice()).isEqualTo(10_000L);
        assertThat(detail.bidIncrement()).isEqualTo(1_000L);
        assertThat(detail.status()).isEqualTo(AuctionItemStatus.IN_PROGRESS);
        assertThat(detail.endAt()).isEqualTo(LocalDateTime.of(2026, 7, 29, 21, 0));
    }
}
