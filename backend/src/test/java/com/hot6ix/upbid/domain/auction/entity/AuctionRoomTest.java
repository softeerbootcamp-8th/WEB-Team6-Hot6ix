package com.hot6ix.upbid.domain.auction.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuctionRoomTest {

    private AuctionRoom newAuctionRoom() {
        return AuctionRoom.builder()
                .name("승민의 경매방")
                .coverImageUrl("https://cdn.hot6ix.com/cover.png")
                .description("한정판 피규어 경매")
                .liveUrl("https://instagram.com/hot6ix")
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();
    }

    @Test
    @DisplayName("update()는 값이 온 필드만 덮어쓰고 나머지는 유지한다(부분 병합)")
    void update_partialMerge() {

        AuctionRoom auctionRoom = newAuctionRoom();

        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("새로운 경매방 이름")
                .build();

        auctionRoom.update(request);

        assertThat(auctionRoom.getName()).isEqualTo("새로운 경매방 이름");
        assertThat(auctionRoom.getCoverImageUrl()).isEqualTo("https://cdn.hot6ix.com/cover.png");
        assertThat(auctionRoom.getDescription()).isEqualTo("한정판 피규어 경매");
        assertThat(auctionRoom.getLiveUrl()).isEqualTo("https://instagram.com/hot6ix");
        assertThat(auctionRoom.getSoftCloseTriggerSeconds()).isEqualTo(30);
        assertThat(auctionRoom.getSoftCloseExtendSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("update()는 요청에 온 모든 필드를 덮어쓸 수 있다")
    void update_allFields() {

        AuctionRoom auctionRoom = newAuctionRoom();

        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("새로운 경매방 이름")
                .coverImageUrl("https://cdn.hot6ix.com/new-cover.png")
                .description("새로운 소개")
                .liveUrl("https://youtube.com/@newroom")
                .softCloseTriggerSeconds(10)
                .softCloseExtendSeconds(20)
                .build();

        auctionRoom.update(request);

        assertThat(auctionRoom.getName()).isEqualTo("새로운 경매방 이름");
        assertThat(auctionRoom.getCoverImageUrl()).isEqualTo("https://cdn.hot6ix.com/new-cover.png");
        assertThat(auctionRoom.getDescription()).isEqualTo("새로운 소개");
        assertThat(auctionRoom.getLiveUrl()).isEqualTo("https://youtube.com/@newroom");
        assertThat(auctionRoom.getSoftCloseTriggerSeconds()).isEqualTo(10);
        assertThat(auctionRoom.getSoftCloseExtendSeconds()).isEqualTo(20);
    }
}
