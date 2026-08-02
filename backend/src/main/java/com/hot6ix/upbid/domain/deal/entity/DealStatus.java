package com.hot6ix.upbid.domain.deal.entity;

/**
 * 화면에 보이는 후보의 거래 상태. 저장하지 않고 {@code (status, candidateRank)}에서 계산한다.
 * "지금 차례"는 다른 후보들과의 관계에서 정해지는 값이라, 컬럼으로 두면 둘째 진실이 생긴다.
 *
 * <p>{@link DealCandidateStatus}에 없는 {@code IN_PROGRESS}가 있다. DB의 {@code WAITING}은
 * "아직 처리되지 않음"이고, 그중 지금 낙찰 권한을 가진 한 명만 진행 중이다.
 */
public enum DealStatus {
    WAITING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
