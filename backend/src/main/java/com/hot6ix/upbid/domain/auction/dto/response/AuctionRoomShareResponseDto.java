package com.hot6ix.upbid.domain.auction.dto.response;

/**
 * 소유자에게만 주는 경매방 공유 정보. QR은 서버가 이미지로 만들지 않고 클라이언트가 이
 * shareUrl 문자열을 그대로 렌더링하므로, 이미지·QR 관련 값은 담지 않는다.
 */
public record AuctionRoomShareResponseDto(
        String shareUrl,
        /**
         * 공유 링크에 박혀 있는 코드를 따로도 준다. 생성 완료 화면이 "경매방으로 이동"을 그리려면
         * 코드가 필요한데, shareUrl에서 잘라 쓰게 하면 링크 형식이 바뀔 때 화면이 같이 깨진다.
         */
        String shareCode
) {
}
