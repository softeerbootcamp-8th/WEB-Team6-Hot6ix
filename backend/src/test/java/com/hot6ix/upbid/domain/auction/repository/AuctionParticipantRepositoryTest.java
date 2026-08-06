package com.hot6ix.upbid.domain.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class AuctionParticipantRepositoryTest extends AbstractMySqlContainerTest {

    @Autowired
    private AuctionParticipantRepository auctionParticipantRepository;

    @Autowired
    private AuctionRoomRepository auctionRoomRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    private User newUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .password("password")
                .nickname("참여자")
                .phoneNumber("010-1234-5678")
                .build());
    }

    private AuctionRoom newAuctionRoom(String shareCode) {
        SellerProfile sellerProfile = sellerProfileRepository.saveAndFlush(SellerProfile.builder()
                .user(newUser("seller-" + shareCode + "@hot6ix.com"))
                .storeName("승민상점")
                .build());

        return auctionRoomRepository.saveAndFlush(AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(sellerProfile)
                .name("승민의 경매방")
                .shareCode(shareCode)
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build());
    }

    @Test
    @DisplayName("처음 구독하면 참여 행이 하나 생긴다")
    void insertIfAbsent_createsRow() {

        AuctionRoom room = newAuctionRoom("PARTICIPANT00001");
        User user = newUser("buyer1@hot6ix.com");

        int inserted = auctionParticipantRepository
                .insertIfAbsent(room.getAuctionRoomId(), user.getUserId());

        assertThat(inserted).isEqualTo(1);
        assertThat(auctionParticipantRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 사람이 다시 구독해도 행은 하나다")
    void insertIfAbsent_isIdempotent() {

        AuctionRoom room = newAuctionRoom("PARTICIPANT00002");
        User user = newUser("buyer2@hot6ix.com");

        auctionParticipantRepository.insertIfAbsent(room.getAuctionRoomId(), user.getUserId());
        auctionParticipantRepository.insertIfAbsent(room.getAuctionRoomId(), user.getUserId());

        assertThat(auctionParticipantRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("없는 방으로 구독하면 예외 없이 아무 행도 안 생긴다")
    void insertIfAbsent_skipsMissingRoom() {

        User user = newUser("buyer3@hot6ix.com");

        int inserted = auctionParticipantRepository.insertIfAbsent(999_999L, user.getUserId());

        assertThat(inserted).isZero();
        assertThat(auctionParticipantRepository.count()).isZero();
    }

    @Test
    @DisplayName("삭제된 방으로 구독하면 아무 행도 안 생긴다")
    void insertIfAbsent_skipsDeletedRoom() {

        AuctionRoom room = newAuctionRoom("PARTICIPANT00004");
        room.softDelete(LocalDateTime.now());
        auctionRoomRepository.saveAndFlush(room);

        User user = newUser("buyer4@hot6ix.com");

        int inserted = auctionParticipantRepository
                .insertIfAbsent(room.getAuctionRoomId(), user.getUserId());

        assertThat(inserted).isZero();
        assertThat(auctionParticipantRepository.count()).isZero();
    }
}
