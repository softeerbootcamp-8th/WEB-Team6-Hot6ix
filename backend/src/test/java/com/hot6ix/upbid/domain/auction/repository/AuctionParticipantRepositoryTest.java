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
    @DisplayName("처음 동의하면 참여 행이 하나 생기고 agreedAt이 기록된다")
    void recordAgreement_createsRow() {

        AuctionRoom room = newAuctionRoom("PARTICIPANT00001");
        User user = newUser("buyer1@hot6ix.com");

        int inserted = auctionParticipantRepository
                .recordAgreement(room.getAuctionRoomId(), user.getUserId(), "v1");

        assertThat(inserted).isEqualTo(1);
        assertThat(auctionParticipantRepository.count()).isEqualTo(1L);
        assertThat(auctionParticipantRepository
                .existsByAuctionRoom_AuctionRoomIdAndUser_UserIdAndAgreedAtIsNotNull(
                        room.getAuctionRoomId(), user.getUserId())).isTrue();
    }

    @Test
    @DisplayName("같은 사람이 다시 동의해도 행은 하나고 최초 동의 기록이 유지된다")
    void recordAgreement_isIdempotent() {

        AuctionRoom room = newAuctionRoom("PARTICIPANT00002");
        User user = newUser("buyer2@hot6ix.com");

        auctionParticipantRepository.recordAgreement(room.getAuctionRoomId(), user.getUserId(), "v1");
        auctionParticipantRepository.recordAgreement(room.getAuctionRoomId(), user.getUserId(), "v1");

        assertThat(auctionParticipantRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("없는 방으로 동의하면 예외 없이 아무 행도 안 생긴다")
    void recordAgreement_skipsMissingRoom() {

        User user = newUser("buyer3@hot6ix.com");

        int inserted = auctionParticipantRepository.recordAgreement(999_999L, user.getUserId(), "v1");

        assertThat(inserted).isZero();
        assertThat(auctionParticipantRepository.count()).isZero();
    }

    @Test
    @DisplayName("삭제된 방으로 동의하면 아무 행도 안 생긴다")
    void recordAgreement_skipsDeletedRoom() {

        AuctionRoom room = newAuctionRoom("PARTICIPANT00004");
        room.softDelete(LocalDateTime.now());
        auctionRoomRepository.saveAndFlush(room);

        User user = newUser("buyer4@hot6ix.com");

        int inserted = auctionParticipantRepository
                .recordAgreement(room.getAuctionRoomId(), user.getUserId(), "v1");

        assertThat(inserted).isZero();
        assertThat(auctionParticipantRepository.count()).isZero();
    }
}
