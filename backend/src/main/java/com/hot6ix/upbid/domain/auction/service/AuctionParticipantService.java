package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuctionParticipantService {

    static final String CURRENT_TERMS_VERSION = "v1";

    private final AuctionParticipantRepository auctionParticipantRepository;
    private final AuctionRoomRepository auctionRoomRepository;

    /**
     * 경매방 입장 약관 동의를 기록한다.
     *
     * <p>참여 행이 없으면 동의 정보와 함께 새로 만들고, 이미 있으면 동의 시각과 버전을 갱신한다.
     * {@code upsertAgreement}가 0을 반환하면 방이 없거나 삭제된 것이므로 예외를 던진다.
     */
    @Transactional
    public void agree(String shareCode, Long userId) {
        Long roomId = auctionRoomRepository.findIdByShareCode(shareCode)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        auctionParticipantRepository.recordAgreement(roomId, userId, CURRENT_TERMS_VERSION);
    }
}
