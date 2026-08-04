package com.hot6ix.upbid.domain.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomListItemResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class AuctionRoomRepositoryTest extends AbstractMySqlContainerTest {

    @Autowired
    private AuctionRoomRepository auctionRoomRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuctionItemRepository auctionItemRepository;

    @Autowired
    private EntityManager entityManager;

    private SellerProfile newSellerProfile(String email) {
        User user = userRepository.saveAndFlush(User.builder()
                .email(email)
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build());

        return sellerProfileRepository.saveAndFlush(SellerProfile.builder()
                .user(user)
                .storeName("승민상점")
                .build());
    }

    private AuctionRoom newAuctionRoom(SellerProfile sellerProfile, String shareCode) {
        return auctionRoomRepository.saveAndFlush(AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(sellerProfile)
                .name("승민의 경매방")
                .shareCode(shareCode)
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build());
    }

    private AuctionRoom newAuctionRoom(SellerProfile sellerProfile, String shareCode,
                                       String name, AuctionRoomStatus status) {
        return auctionRoomRepository.saveAndFlush(AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(sellerProfile)
                .name(name)
                .status(status)
                .shareCode(shareCode)
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build());
    }

    private void addItem(AuctionRoom auctionRoom, SellerProfile sellerProfile, String productName) {
        Product product = productRepository.saveAndFlush(Product.builder()
                .sellerProfile(sellerProfile)
                .name(productName)
                .description("미개봉 정품")
                .build());

        auctionItemRepository.saveAndFlush(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(product)
                .startingPrice(50_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.READY)
                .build());
    }

    @Test
    @DisplayName("내 경매방만 최신순으로 나오고 물품 수가 함께 채워진다")
    void search_returnsOwnRoomsWithItemCount() {

        SellerProfile mine = newSellerProfile("mine@hot6ix.com");
        SellerProfile other = newSellerProfile("other@hot6ix.com");

        AuctionRoom first = newAuctionRoom(mine, "SEARCH0000000001", "첫 번째 방", AuctionRoomStatus.BEFORE);
        AuctionRoom second = newAuctionRoom(mine, "SEARCH0000000002", "두 번째 방", AuctionRoomStatus.OPEN);
        newAuctionRoom(other, "SEARCH0000000003", "남의 방", AuctionRoomStatus.BEFORE);

        addItem(first, mine, "피규어");
        addItem(first, mine, "포토카드");

        List<AuctionRoomListItemResponseDto> found =
                auctionRoomRepository.search(mine.getSellerProfileId(), null, null, null, 20);

        assertThat(found).hasSize(2);
        // auctionRoomId 내림차순이라 나중에 만든 방이 앞에 온다.
        assertThat(found.get(0).auctionRoomId()).isEqualTo(second.getAuctionRoomId());
        assertThat(found.get(0).itemCount()).isZero();
        assertThat(found.get(1).auctionRoomId()).isEqualTo(first.getAuctionRoomId());
        assertThat(found.get(1).itemCount()).isEqualTo(2L);
        assertThat(found.get(1).participantCount()).isNull();
    }

    @Test
    @DisplayName("soft delete된 경매방은 목록에서 빠진다")
    void search_excludesDeleted() {

        SellerProfile sellerProfile = newSellerProfile("search-deleted@hot6ix.com");
        AuctionRoom auctionRoom = newAuctionRoom(sellerProfile, "SEARCH0000000004", "지운 방", AuctionRoomStatus.BEFORE);
        auctionRoom.softDelete(LocalDateTime.now());
        auctionRoomRepository.flush();

        List<AuctionRoomListItemResponseDto> found =
                auctionRoomRepository.search(sellerProfile.getSellerProfileId(), null, null, null, 20);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("상태와 이름으로 거를 수 있다")
    void search_filtersByStatusAndKeyword() {

        SellerProfile sellerProfile = newSellerProfile("search-filter@hot6ix.com");
        newAuctionRoom(sellerProfile, "SEARCH0000000005", "7월 라이브 경매", AuctionRoomStatus.OPEN);
        newAuctionRoom(sellerProfile, "SEARCH0000000006", "8월 준비 중 경매", AuctionRoomStatus.BEFORE);

        List<AuctionRoomListItemResponseDto> byStatus = auctionRoomRepository.search(
                sellerProfile.getSellerProfileId(), null, AuctionRoomStatus.OPEN, null, 20);
        List<AuctionRoomListItemResponseDto> byKeyword = auctionRoomRepository.search(
                sellerProfile.getSellerProfileId(), "8월", null, null, 20);

        assertThat(byStatus).singleElement()
                .extracting(AuctionRoomListItemResponseDto::name).isEqualTo("7월 라이브 경매");
        assertThat(byKeyword).singleElement()
                .extracting(AuctionRoomListItemResponseDto::name).isEqualTo("8월 준비 중 경매");
    }

    @Test
    @DisplayName("커서보다 오래된 경매방만 다음 쪽으로 나오고 다음 쪽 판정을 위해 한 건을 더 읽는다")
    void search_paginatesByCursor() {

        SellerProfile sellerProfile = newSellerProfile("search-cursor@hot6ix.com");
        AuctionRoom first = newAuctionRoom(sellerProfile, "SEARCH0000000007", "1", AuctionRoomStatus.BEFORE);
        AuctionRoom second = newAuctionRoom(sellerProfile, "SEARCH0000000008", "2", AuctionRoomStatus.BEFORE);
        AuctionRoom third = newAuctionRoom(sellerProfile, "SEARCH0000000009", "3", AuctionRoomStatus.BEFORE);

        // size 2를 요청하면 hasNext 판정용으로 3건을 읽어온다.
        List<AuctionRoomListItemResponseDto> firstPage =
                auctionRoomRepository.search(sellerProfile.getSellerProfileId(), null, null, null, 2);
        List<AuctionRoomListItemResponseDto> nextPage = auctionRoomRepository.search(
                sellerProfile.getSellerProfileId(), null, null, second.getAuctionRoomId(), 2);

        assertThat(firstPage).hasSize(3);
        assertThat(firstPage.get(0).auctionRoomId()).isEqualTo(third.getAuctionRoomId());
        assertThat(nextPage).singleElement()
                .extracting(AuctionRoomListItemResponseDto::auctionRoomId)
                .isEqualTo(first.getAuctionRoomId());
    }

    @Test
    @DisplayName("활성 경매방을 auctionRoomId로 조회한다")
    void findByAuctionRoomIdAndDeletedAtIsNull_found() {

        SellerProfile sellerProfile = newSellerProfile("seller1@hot6ix.com");
        AuctionRoom auctionRoom = newAuctionRoom(sellerProfile, "CODE0000000000001");

        Optional<AuctionRoom> found =
                auctionRoomRepository.findByAuctionRoomIdAndDeletedAtIsNull(auctionRoom.getAuctionRoomId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("승민의 경매방");
    }

    @Test
    @DisplayName("soft delete된 경매방은 조회되지 않는다")
    void findByAuctionRoomIdAndDeletedAtIsNull_excludesDeleted() {

        SellerProfile sellerProfile = newSellerProfile("seller2@hot6ix.com");
        AuctionRoom auctionRoom = newAuctionRoom(sellerProfile, "CODE0000000000002");
        auctionRoom.softDelete(LocalDateTime.now());
        auctionRoomRepository.flush();

        Optional<AuctionRoom> found =
                auctionRoomRepository.findByAuctionRoomIdAndDeletedAtIsNull(auctionRoom.getAuctionRoomId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("소유자의 활성 경매방을 auctionRoomId로 조회한다")
    void findByAuctionRoomIdAndSellerProfile_found() {

        SellerProfile sellerProfile = newSellerProfile("seller3@hot6ix.com");
        AuctionRoom auctionRoom = newAuctionRoom(sellerProfile, "CODE0000000000003");

        Optional<AuctionRoom> found = auctionRoomRepository
                .findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                        auctionRoom.getAuctionRoomId(), sellerProfile.getSellerProfileId());

        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("다른 판매자의 경매방은 조회되지 않는다")
    void findByAuctionRoomIdAndSellerProfile_excludesOtherOwner() {

        SellerProfile owner = newSellerProfile("seller4@hot6ix.com");
        SellerProfile other = newSellerProfile("seller5@hot6ix.com");
        AuctionRoom auctionRoom = newAuctionRoom(owner, "CODE0000000000004");

        Optional<AuctionRoom> found = auctionRoomRepository
                .findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                        auctionRoom.getAuctionRoomId(), other.getSellerProfileId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("soft delete된 경매방은 소유자로 조회해도 나오지 않는다")
    void findByAuctionRoomIdAndSellerProfile_excludesDeleted() {

        SellerProfile sellerProfile = newSellerProfile("seller6@hot6ix.com");
        AuctionRoom auctionRoom = newAuctionRoom(sellerProfile, "CODE0000000000005");
        auctionRoom.softDelete(LocalDateTime.now());
        auctionRoomRepository.flush();

        Optional<AuctionRoom> found = auctionRoomRepository
                .findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                        auctionRoom.getAuctionRoomId(), sellerProfile.getSellerProfileId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("활성 경매방은 존재 확인에 true를 반환한다")
    void existsByAuctionRoomIdAndDeletedAtIsNull_true() {

        SellerProfile sellerProfile = newSellerProfile("seller7@hot6ix.com");
        AuctionRoom auctionRoom = newAuctionRoom(sellerProfile, "CODE0000000000006");

        boolean exists = auctionRoomRepository.existsByAuctionRoomIdAndDeletedAtIsNull(auctionRoom.getAuctionRoomId());

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("soft delete된 경매방은 존재 확인에 false를 반환한다")
    void existsByAuctionRoomIdAndDeletedAtIsNull_falseWhenDeleted() {

        SellerProfile sellerProfile = newSellerProfile("seller8@hot6ix.com");
        AuctionRoom auctionRoom = newAuctionRoom(sellerProfile, "CODE0000000000007");
        auctionRoom.softDelete(LocalDateTime.now());
        auctionRoomRepository.flush();

        boolean exists = auctionRoomRepository.existsByAuctionRoomIdAndDeletedAtIsNull(auctionRoom.getAuctionRoomId());

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 경매방은 존재 확인에 false를 반환한다")
    void existsByAuctionRoomIdAndDeletedAtIsNull_falseWhenNotFound() {

        boolean exists = auctionRoomRepository.existsByAuctionRoomIdAndDeletedAtIsNull(999_999L);

        assertThat(exists).isFalse();
    }

    /**
     * 물품 시작의 "방당 동시 3개" 검사가 이 락에 걸려 있다. 트랜잭션이 둘 필요해 실제 차단은
     * 볼 수 없으므로 락 모드만 단정한다. {@code clear()}가 필요한 이유는 같은 트랜잭션에서
     * INSERT한 엔티티가 이미 쓰기 상태로 표시돼 있기 때문이다.
     */
    @Test
    @DisplayName("락 조회는 경매방에 쓰기 락을 걸고 그대로 돌려준다")
    void findByIdForUpdate_locksRoom() {

        SellerProfile sellerProfile = newSellerProfile("seller-lock@hot6ix.com");
        AuctionRoom auctionRoom = newAuctionRoom(sellerProfile, "CODE0000000000100");
        Long auctionRoomId = auctionRoom.getAuctionRoomId();
        entityManager.clear();

        AuctionRoom locked = auctionRoomRepository.findByIdForUpdate(auctionRoomId).orElseThrow();

        assertThat(locked.getAuctionRoomId()).isEqualTo(auctionRoomId);
        assertThat(entityManager.getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    /**
     * 이름에 {@code DeletedAtIsNull}이 없어 오해하기 쉬운 자리라, 필터가 실제로 걸려 있는지
     * 확인한다. 이게 빠지면 삭제된 경매방의 물품도 시작된다.
     */
    @Test
    @DisplayName("soft delete된 경매방은 락 조회에서도 나오지 않는다")
    void findByIdForUpdate_excludesDeleted() {

        SellerProfile sellerProfile = newSellerProfile("seller-lock-deleted@hot6ix.com");
        AuctionRoom auctionRoom = newAuctionRoom(sellerProfile, "CODE0000000000101");
        auctionRoom.softDelete(LocalDateTime.now());
        auctionRoomRepository.flush();

        assertThat(auctionRoomRepository.findByIdForUpdate(auctionRoom.getAuctionRoomId())).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 경매방을 락 조회하면 빈 값을 돌려준다")
    void findByIdForUpdate_emptyWhenNotFound() {

        assertThat(auctionRoomRepository.findByIdForUpdate(999_999L)).isEmpty();
    }
}
