package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuctionParticipantService {

    private final AuctionParticipantRepository auctionParticipantRepository;

    /**
     * 방에 들어온 로그인 사용자를 기록한다. 참여 경매방 목록이 이 기록을 읽는다.
     *
     * <p>게스트는 {@code userId}가 {@code null}이라 아무것도 안 한다.
     */
    @Transactional
    public void record(Long roomId, Long userId) {
        if (userId == null) {
            return;
        }

        auctionParticipantRepository.insertIfAbsent(roomId, userId);
    }
}
