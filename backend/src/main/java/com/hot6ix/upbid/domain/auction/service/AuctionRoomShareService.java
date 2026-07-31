package com.hot6ix.upbid.domain.auction.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Service;

/**
 * 경매방 공유(share_code, 공유 링크·QR) 관련 로직을 담당한다. 지금은 공유 코드 문자열 생성만
 * 담당하며, 공유 정보 조회·QR 생성은 [x04-경매방-공유-QR] PR에서 이 서비스에 추가된다.
 */
@Service
public class AuctionRoomShareService {

    private static final String SHARE_CODE_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int SHARE_CODE_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 경매방 공유 코드 후보를 하나 생성한다. QR 스캔·링크 복사로만 쓰이므로 사람이 직접
     * 읽거나 타이핑할 일이 없어, 문자 혼동 방지를 위한 별도 문자셋 제한은 두지 않는다.
     * 유일성은 호출 측(방 저장 시 유니크 제약 위반 재시도)에서 보장한다.
     *
     * @return 16자 영숫자 랜덤 코드
     */
    public String generateCandidateShareCode() {
        StringBuilder code = new StringBuilder(SHARE_CODE_LENGTH);
        for (int i = 0; i < SHARE_CODE_LENGTH; i++) {
            code.append(SHARE_CODE_ALPHABET.charAt(secureRandom.nextInt(SHARE_CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
