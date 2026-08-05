package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionParticipantRepository extends JpaRepository<AuctionParticipant, Long> {

    /**
     * 해당 방에서 유저가 약관에 동의한 기록이 있는지 확인한다.
     * {@code agreed_at}이 존재해야 동의한 것으로 본다.
     */
    boolean existsByAuctionRoom_AuctionRoomIdAndUser_UserIdAndAgreedAtIsNotNull(
            Long auctionRoomId, Long userId);

    /**
     * 약관 동의를 기록한다. 참여 행이 없으면 동의 정보와 함께 새로 만들고, 이미 있으면 아무것도 안 한다.
     *
     * <p>{@code insertIfAbsent}와 같은 이유로 {@code select}로 방 존재를 확인한다.
     * 방이 없으면 select 결과가 0행이라 insert가 일어나지 않는다.
     *
     * <p>같은 방에 대해 동의는 최초 한 번만 유효하다. 이미 동의한 행이 있으면 no-op으로 끝내
     * 원래 동의 시각과 버전을 보존한다.
     *
     * @return 새로 넣었으면 1, 이미 있거나 방이 없으면 0
     */
    @Modifying
    @Query(value = """
            insert into auction_participants (auction_room_id, user_id, agreed_at, terms_version)
            select :roomId, :userId, now(), :termsVersion from auction_rooms ar
             where ar.auction_room_id = :roomId and ar.deleted_at is null
                on duplicate key update auction_participant_id = auction_participant_id
            """, nativeQuery = true)
    int recordAgreement(@Param("roomId") Long roomId, @Param("userId") Long userId,
                        @Param("termsVersion") String termsVersion);
}
