package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuctionRoomShareServiceTest {

    private final AuctionRoomShareService auctionRoomShareService = new AuctionRoomShareService();

    @Test
    @DisplayName("16자 영숫자 코드를 생성한다")
    void generateCandidateShareCode_lengthAndCharset() {

        String code = auctionRoomShareService.generateCandidateShareCode();

        assertThat(code).hasSize(16);
        assertThat(code).matches("^[0-9A-Za-z]{16}$");
    }

    @Test
    @DisplayName("호출할 때마다 다른 코드를 생성한다")
    void generateCandidateShareCode_differsBetweenCalls() {

        String first = auctionRoomShareService.generateCandidateShareCode();
        String second = auctionRoomShareService.generateCandidateShareCode();

        assertThat(first).isNotEqualTo(second);
    }
}
