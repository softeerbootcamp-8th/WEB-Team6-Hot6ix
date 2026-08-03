package com.hot6ix.upbid.domain.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
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
}
