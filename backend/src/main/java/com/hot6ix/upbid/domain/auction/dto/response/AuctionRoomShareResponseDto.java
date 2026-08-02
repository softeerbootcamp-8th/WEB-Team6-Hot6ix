package com.hot6ix.upbid.domain.auction.dto.response;

/**
 * 소유자에게만 주는 경매방 공유 정보. QR은 서버가 이미지로 만들지 않고 클라이언트가 이
 * shareUrl 문자열을 그대로 렌더링하므로, 이미지·QR 관련 값은 담지 않는다.
 */
public record AuctionRoomShareResponseDto(
        String shareUrl
) {
}
