package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionParticipantRepository extends JpaRepository<AuctionParticipant, Long> {

    /**
     * 참여 행을 한 문장으로 넣는다. 이미 있으면 아무것도 안 하고, 없는 방이면 안 넣는다.
     *
     * <p>{@code exists}로 읽고 없으면 {@code save}하면 탭 두 개가 동시에 붙을 때 둘 다
     * "없음"을 읽고 둘 다 INSERT한다. 한 문장이면 읽기와 쓰기 사이가 벌어지지 않아,
     * MySQL이 유니크 인덱스 {@code uk_auction_participants_room_user}에서 두 요청을
     * 한 줄로 세운다. 늦은 쪽은 {@code on duplicate key update}가 no-op으로 끝낸다.
     *
     * <p>{@code values} 대신 {@code select}를 쓰는 이유는 방 존재와 soft delete를 같은
     * 문장에서 거르기 위해서다. 없는 방이면 select가 0행이라 FK 위반 예외 대신 0행이 된다.
     *
     * @return 새로 넣었으면 1, 이미 있거나 방이 없으면 0
     */
    @Modifying
    @Query(value = """
            insert into auction_participants (auction_room_id, user_id)
            select :roomId, :userId from auction_rooms ar
             where ar.auction_room_id = :roomId and ar.deleted_at is null
                on duplicate key update auction_participant_id = auction_participant_id
            """, nativeQuery = true)
    int insertIfAbsent(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
