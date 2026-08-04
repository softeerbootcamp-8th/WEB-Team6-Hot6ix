package com.hot6ix.upbid.domain.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.bid.entity.Bid;
import com.hot6ix.upbid.domain.product.entity.Product;
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
import org.springframework.data.domain.Limit;
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

    /** 물품과 상품은 Repository가 없으므로 EntityManager로 직접 저장한다. */
    private AuctionItem newItem(AuctionRoom auctionRoom, SellerProfile sellerProfile, String productName) {
        Product product = Product.builder()
                .sellerProfile(sellerProfile)
                .name(productName)
                .description("미개봉 정품")
                .build();
        entityManager.persist(product);

        AuctionItem item = AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(product)
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.IN_PROGRESS)
                .build();
        entityManager.persist(item);
        entityManager.flush();

        return item;
    }

    /** 입찰 한 건. {@code (auction_item_id, amount)}에 유니크 제약이 있어 금액을 달리 준다. */
    private void newBid(AuctionItem item, User bidder, long amount) {
        entityManager.persist(Bid.builder()
                .auctionItem(item)
                .bidder(bidder)
                .amount(amount)
                .build());
        entityManager.flush();
    }

    private List<AuctionRoom> findOwned(SellerProfile profile) {
        entityManager.flush();
        entityManager.clear();
        return auctionRoomRepository.findOwnedRooms(profile.getUser().getUserId());
    }

    private List<AuctionRoom> findParticipated(SellerProfile profile) {
        entityManager.flush();
        entityManager.clear();
        return auctionRoomRepository.findParticipatedRooms(profile.getUser().getUserId());
    }

    @Test
    @DisplayName("개설방 조회는 내 방만 돌려주고 가게 이름을 함께 읽어 온다")
    void findOwnedRoomsReturnsOnlyMine() {

        SellerProfile mine = newSellerProfile("mine@hot6ix.com");
        SellerProfile others = newSellerProfile("others@hot6ix.com");

        AuctionRoom myRoom = newAuctionRoom(mine, "CODE0000000000201");
        newAuctionRoom(others, "CODE0000000000202");

        List<AuctionRoom> owned = findOwned(mine);

        assertThat(owned)
                .extracting(AuctionRoom::getAuctionRoomId)
                .containsExactly(myRoom.getAuctionRoomId());
        // fetch join 이 빠지면 세션이 닫힌 뒤 이 접근에서 터진다.
        assertThat(owned.getFirst().getSellerProfile().getStoreName()).isEqualTo("승민상점");
    }

    @Test
    @DisplayName("삭제된 방은 개설방 조회에서 빠진다")
    void findOwnedRoomsExcludesDeleted() {

        SellerProfile mine = newSellerProfile("owned-deleted@hot6ix.com");

        AuctionRoom deleted = newAuctionRoom(mine, "CODE0000000000203");
        deleted.softDelete(LocalDateTime.now());
        auctionRoomRepository.flush();

        assertThat(findOwned(mine)).isEmpty();
    }

    @Test
    @DisplayName("참여방 조회는 내가 입찰한 남의 방만 돌려준다")
    void findParticipatedRoomsReturnsBidRooms() {

        SellerProfile mine = newSellerProfile("bidder@hot6ix.com");
        SellerProfile others = newSellerProfile("seller-joined@hot6ix.com");

        AuctionRoom joined = newAuctionRoom(others, "CODE0000000000204");
        AuctionItem item = newItem(joined, others, "남의물품");
        newBid(item, mine.getUser(), 11_000L);
        newAuctionRoom(others, "CODE0000000000205");

        assertThat(findParticipated(mine))
                .extracting(AuctionRoom::getAuctionRoomId)
                .containsExactly(joined.getAuctionRoomId());
    }

    /** exists 가 하는 일. 조인으로 풀면 입찰 수만큼 같은 방이 늘어난다. */
    @Test
    @DisplayName("한 방에 여러 번 입찰해도 참여방은 한 줄만 나온다")
    void findParticipatedRoomsDeduplicatesMultipleBids() {

        SellerProfile mine = newSellerProfile("multi-bid@hot6ix.com");
        SellerProfile others = newSellerProfile("seller-multi@hot6ix.com");

        AuctionRoom room = newAuctionRoom(others, "CODE0000000000206");
        AuctionItem first = newItem(room, others, "물품1");
        AuctionItem second = newItem(room, others, "물품2");
        newBid(first, mine.getUser(), 11_000L);
        newBid(first, mine.getUser(), 12_000L);
        newBid(second, mine.getUser(), 13_000L);

        assertThat(findParticipated(mine))
                .extracting(AuctionRoom::getAuctionRoomId)
                .containsExactly(room.getAuctionRoomId());
    }

    /**
     * 판매자 본인 입찰은 차단돼 있지만 그 규칙 이전 데이터가 남아 있을 수 있다. 조건이 없으면
     * 한 방이 개설방과 참여방 양쪽에 나와 목록에 두 줄이 된다.
     */
    @Test
    @DisplayName("내 방에 내가 입찰한 데이터가 있어도 참여방에는 안 들어간다")
    void findParticipatedRoomsExcludesMyOwnRoom() {

        SellerProfile mine = newSellerProfile("self-bid@hot6ix.com");

        AuctionRoom myRoom = newAuctionRoom(mine, "CODE0000000000207");
        AuctionItem item = newItem(myRoom, mine, "내물품");
        newBid(item, mine.getUser(), 11_000L);

        assertThat(findParticipated(mine)).isEmpty();
    }

    @Test
    @DisplayName("삭제된 방은 참여방 조회에서도 빠진다")
    void findParticipatedRoomsExcludesDeleted() {

        SellerProfile mine = newSellerProfile("joined-deleted@hot6ix.com");
        SellerProfile others = newSellerProfile("seller-joined-deleted@hot6ix.com");

        AuctionRoom room = newAuctionRoom(others, "CODE0000000000208");
        AuctionItem item = newItem(room, others, "삭제방물품");
        newBid(item, mine.getUser(), 11_000L);

        room.softDelete(LocalDateTime.now());
        auctionRoomRepository.flush();

        assertThat(findParticipated(mine)).isEmpty();
    }

    /** 참여 이력은 지나간 사실이라 상대가 프로필을 지워도 내 목록에서 빠지지 않는다. */
    @Test
    @DisplayName("판매자가 프로필을 지운 방은 참여방에 남는다")
    void findParticipatedRoomsKeepsRoomsWithDeletedSellerProfile() {

        SellerProfile mine = newSellerProfile("kept@hot6ix.com");
        SellerProfile others = newSellerProfile("seller-gone@hot6ix.com");

        AuctionRoom room = newAuctionRoom(others, "CODE0000000000209");
        AuctionItem item = newItem(room, others, "남은물품");
        newBid(item, mine.getUser(), 11_000L);

        others.softDelete(LocalDateTime.now());
        sellerProfileRepository.flush();

        assertThat(findParticipated(mine))
                .extracting(AuctionRoom::getAuctionRoomId)
                .containsExactly(room.getAuctionRoomId());
    }

    /**
     * 합친 뒤 자르는 것으로는 서버가 받는 행을 막지 못한다. 상한이 각 갈래의 쿼리에 걸려
     * 있는지를 작은 값으로 확인한다.
     */
    @Test
    @DisplayName("개설방 조회는 상한을 넘으면 최근에 만든 방까지만 돌려준다")
    void findOwnedRoomsStopsAtLimit() {

        SellerProfile mine = newSellerProfile("owned-limit@hot6ix.com");

        newAuctionRoom(mine, "CODE0000000000210");
        AuctionRoom recent = newAuctionRoom(mine, "CODE0000000000211");

        entityManager.flush();
        entityManager.clear();

        assertThat(auctionRoomRepository.findOwnedRooms(mine.getUser().getUserId(), Limit.of(1)))
                .extracting(AuctionRoom::getAuctionRoomId)
                .containsExactly(recent.getAuctionRoomId());
    }

    @Test
    @DisplayName("참여방 조회도 상한을 넘으면 최근에 만든 방까지만 돌려준다")
    void findParticipatedRoomsStopsAtLimit() {

        SellerProfile mine = newSellerProfile("joined-limit@hot6ix.com");
        SellerProfile others = newSellerProfile("seller-joined-limit@hot6ix.com");

        AuctionRoom older = newAuctionRoom(others, "CODE0000000000212");
        newBid(newItem(older, others, "예전물품"), mine.getUser(), 11_000L);
        AuctionRoom recent = newAuctionRoom(others, "CODE0000000000213");
        newBid(newItem(recent, others, "최근물품"), mine.getUser(), 12_000L);

        entityManager.flush();
        entityManager.clear();

        assertThat(auctionRoomRepository.findParticipatedRooms(mine.getUser().getUserId(), Limit.of(1)))
                .extracting(AuctionRoom::getAuctionRoomId)
                .containsExactly(recent.getAuctionRoomId());
    }
}
