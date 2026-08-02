package com.hot6ix.upbid.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 실패 응답의 {@code code}로 그대로 나가는 값이라 <b>errorCode는 공용 계약</b>이다.
 * 프론트가 이 숫자로 분기하므로 이미 배포된 값의 의미를 바꾸지 않는다.
 *
 * <p>도메인마다 천 번대 블록을 하나 잡는다. 현재 배정:
 *
 * <pre>
 *   2xxx  공통          CommonErrorType
 *   3xxx  판매자 프로필  SellerProfileErrorType
 *   4xxx  경매 물품·경매방  AuctionErrorType
 *   6xxx  거래·낙찰      DealErrorType
 *   7xxx  입찰          BidErrorType
 *   8xxx  SMS 인증      SmsVerificationErrorType
 * </pre>
 *
 * <p>새 도메인은 위에 없는 천 번대를 잡고 이 목록에 추가한다. 같은 뜻의 에러를 도메인마다
 * 다시 정의하지 않고 기존 것을 재사용한다 — 예를 들어 물품이 없을 때는 거래 쪽에서도
 * {@code AuctionErrorType.AUCTION_ITEM_NOT_FOUND}를 쓴다.
 */
public interface ErrorType {

    HttpStatus getHttpStatus();
    Integer getErrorCode();
    String getMessage();
}
