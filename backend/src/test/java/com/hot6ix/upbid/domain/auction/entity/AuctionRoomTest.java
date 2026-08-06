package com.hot6ix.upbid.domain.auction.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuctionRoomTest {

    private AuctionRoom newAuctionRoom() {
        return AuctionRoom.builder()
                .bidIncrement(1_000L)
                .name("승민의 경매방")
                .coverImageUrl("https://cdn.hot6ix.com/cover.png")
                .description("한정판 피규어 경매")
                .liveUrl("https://instagram.com/hot6ix")
                .softCloseTriggerSeconds(60)
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
        assertThat(auctionRoom.getSoftCloseTriggerSeconds()).isEqualTo(60);
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
                .softCloseTriggerSeconds(90)
                .softCloseExtendSeconds(120)
                .build();

        auctionRoom.update(request);

        assertThat(auctionRoom.getName()).isEqualTo("새로운 경매방 이름");
        assertThat(auctionRoom.getCoverImageUrl()).isEqualTo("https://cdn.hot6ix.com/new-cover.png");
        assertThat(auctionRoom.getDescription()).isEqualTo("새로운 소개");
        assertThat(auctionRoom.getLiveUrl()).isEqualTo("https://youtube.com/@newroom");
        assertThat(auctionRoom.getSoftCloseTriggerSeconds()).isEqualTo(90);
        assertThat(auctionRoom.getSoftCloseExtendSeconds()).isEqualTo(120);
    }

    @Test
    @DisplayName("update()는 liveUrl·description에 빈 문자열이 오면 삭제로 보고 null로 만든다")
    void update_blankClearsLiveUrlAndDescription() {

        AuctionRoom auctionRoom = newAuctionRoom();

        auctionRoom.update(AuctionRoomUpdateRequestDto.builder()
                .liveUrl("")
                .description("   ")
                .build());

        assertThat(auctionRoom.getLiveUrl()).isNull();
        assertThat(auctionRoom.getDescription()).isNull();
    }

    @Test
    @DisplayName("update()로는 입찰 단위를 바꿀 수 없다")
    void update_keepsBidIncrement() {

        AuctionRoom auctionRoom = newAuctionRoom();

        auctionRoom.update(AuctionRoomUpdateRequestDto.builder()
                .name("새로운 경매방 이름")
                .build());

        assertThat(auctionRoom.getBidIncrement()).isEqualTo(1_000L);
    }
}
