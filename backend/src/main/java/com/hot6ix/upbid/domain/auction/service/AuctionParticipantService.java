package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.UserErrorType;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class AuctionParticipantService {

    static final String CURRENT_TERMS_VERSION = "v1";

    private final AuctionParticipantRepository auctionParticipantRepository;
    private final AuctionRoomRepository auctionRoomRepository;
    private final AuctionRedisStore auctionRedisStore;
    private final UserRepository userRepository;

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
        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorType.USER_NOT_FOUND));
        String nickname = user.getNickname();

        auctionParticipantRepository.recordAgreement(roomId, userId, CURRENT_TERMS_VERSION);

        // 입찰 판정이 Redis 참여자 SET을 보므로 여기서도 더한다(이슈 #246의 비교군 C).
        //
        // 반드시 커밋 뒤다. 커밋 전에 넣고 트랜잭션이 롤백되면 DB에 참여 행이 없는 회원이
        // SET에만 남아 입찰이 통과한다. 동의하지 않은 사람이 입찰하는 것이라 그 방향으로
        // 틀리면 안 된다. 반대로 늦게 넣어서 생기는 구간은 그 회원이 7008을 한 번 받는 것뿐이다.
        // 트랜잭션 밖에서 불리면 기다릴 커밋이 없으므로 바로 넣는다. BidMetrics가 같은 방식으로
        // 동기화를 건다.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            auctionRedisStore.addParticipant(roomId, userId, nickname);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auctionRedisStore.addParticipant(roomId, userId, nickname);
            }
        });
    }
}
