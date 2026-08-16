package com.hot6ix.upbid.domain.auction.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemBulkAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemStartRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemBulkAddResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemCloseEarlyResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.service.AuctionItemCloseService;
import com.hot6ix.upbid.domain.auction.service.AuctionItemService;
import com.hot6ix.upbid.domain.product.exception.ProductErrorType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AuctionItemController.class)
@Import(GlobalExceptionHandler.class)
class AuctionItemControllerTest extends AbstractControllerTest {

    @MockitoBean
    private AuctionItemService auctionItemService;

    @MockitoBean
    private AuctionItemCloseService auctionItemCloseService;

    /** 공개 조회는 숫자 PK를 받지 않는다. 방을 지목하는 값은 항상 이 공유 코드다. */
    private static final String SHARE_CODE = "abcdefghij123456";

    private AuctionItemAddRequestDto newAddRequest() {
        return AuctionItemAddRequestDto.builder()
                .productId(20L)
                .startingPrice(50_000L)
                .build();
    }

    private AuctionItemBulkAddRequestDto newBulkRequest(Long... productIds) {
        return AuctionItemBulkAddRequestDto.builder()
                .items(Arrays.stream(productIds)
                        .map(productId -> AuctionItemAddRequestDto.builder()
                                .productId(productId)
                                .startingPrice(50_000L)
                                .build())
                        .toList())
                .build();
    }

    private AuctionItemStartRequestDto newStartRequest(Integer durationMinutes) {
        return AuctionItemStartRequestDto.builder()
                .durationMinutes(durationMinutes)
                .build();
    }

    private AuctionItemSummaryResponseDto sampleSummary() {
        return new AuctionItemSummaryResponseDto(
                1L,
                "한정판 피규어",
                "https://cdn.hot6ix.com/item.png",
                50_000L,
                AuctionItemStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 7, 29, 21, 0));
    }

    private AuctionItemDetailResponseDto sampleDetail() {
        return new AuctionItemDetailResponseDto(
                1L,
                10L,
                "한정판 피규어",
                "미개봉 정품",
                "https://cdn.hot6ix.com/item.png",
                "https://instagram.com/hot6ix",
                10_000L,
                50_000L,
                1_000L,
                AuctionItemStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 7, 29, 21, 0));
    }

    @Test
    @DisplayName("경매방 물품 목록을 조회하면 200과 물품 배열을 반환한다")
    void getSummaries() throws Exception {

        when(auctionItemService.getSummaries(SHARE_CODE, LOGIN_USER_ID)).thenReturn(List.of(sampleSummary()));

        mockMvc.perform(get("/api/v1/auction-rooms/share/{shareCode}/auction-items", SHARE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("경매 물품 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].auctionItemId").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("한정판 피규어"))
                .andExpect(jsonPath("$.data[0].leaderboard").isArray())
                .andExpect(jsonPath("$.data[0].leaderboard").isEmpty())
                .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("물품이 없는 경매방은 200과 빈 배열을 반환한다")
    void getSummariesReturnsEmptyArray() throws Exception {

        when(auctionItemService.getSummaries(SHARE_CODE, LOGIN_USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/auction-rooms/share/{shareCode}/auction-items", SHARE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("없는 공유 코드의 물품 목록을 조회하면 404와 4002를 반환한다")
    void getSummariesRoomNotFound() throws Exception {

        when(auctionItemService.getSummaries("nope", LOGIN_USER_ID))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/auction-rooms/share/{shareCode}/auction-items", "nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4002))
                .andExpect(jsonPath("$.message").value("존재하지 않는 경매방입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("물품 상세를 조회하면 200과 물품 정보를 반환한다")
    void getDetail() throws Exception {

        when(auctionItemService.getDetail(SHARE_CODE, 1L, LOGIN_USER_ID)).thenReturn(sampleDetail());

        mockMvc.perform(get("/api/v1/auction-rooms/share/{shareCode}/auction-items/{itemId}",
                        SHARE_CODE, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("경매 물품 상세 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.auctionItemId").value(1))
                .andExpect(jsonPath("$.data.auctionRoomId").value(10))
                .andExpect(jsonPath("$.data.startingPrice").value(10000))
                .andExpect(jsonPath("$.data.currentPrice").value(50000))
                .andExpect(jsonPath("$.data.bidIncrement").value(1000));
    }

    @Test
    @DisplayName("비로그인 사용자도 경매방 물품 목록을 조회할 수 있다")
    void getSummariesAllowsGuest() throws Exception {

        비로그인_상태로_바꾼다();
        when(auctionItemService.getSummaries(SHARE_CODE, null)).thenReturn(List.of(sampleSummary()));

        mockMvc.perform(get("/api/v1/auction-rooms/share/{shareCode}/auction-items", SHARE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("비로그인 사용자도 물품 상세를 조회할 수 있다")
    void getDetailAllowsGuest() throws Exception {

        비로그인_상태로_바꾼다();
        when(auctionItemService.getDetail(SHARE_CODE, 1L, null)).thenReturn(sampleDetail());

        mockMvc.perform(get("/api/v1/auction-rooms/share/{shareCode}/auction-items/{itemId}",
                        SHARE_CODE, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("없거나 그 방 소속이 아닌 물품을 상세 조회하면 404와 4001을 반환한다")
    void getDetailNotFound() throws Exception {

        when(auctionItemService.getDetail(SHARE_CODE, 999L, LOGIN_USER_ID))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/auction-rooms/share/{shareCode}/auction-items/{itemId}",
                        SHARE_CODE, 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.message").value("존재하지 않는 경매 물품입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * 방만 공유 코드로 가려도 물품이 숫자 ID로 열려 있으면 열거 지점이 옮겨갈 뿐이다.
     * 매핑이 사라졌다는 것을 회귀로 못 박는다.
     *
     * <p>물품 목록 경로는 404가 아니라 405다 — 같은 경로에 소유자용 POST(물품 추가)가 남아 있어
     * Spring이 "경로는 있는데 GET은 안 된다"고 답한다. 어느 쪽이든 읽히지 않는 것은 같다.
     */
    @Test
    @DisplayName("숫자 PK로 들어오는 공개 조회 경로는 더 이상 없다")
    void legacyNumericPublicPathsAreGone() throws Exception {

        mockMvc.perform(get("/api/v1/auction-rooms/10/auction-items"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/api/v1/auction-items/1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("물품을 추가하면 201과 추가된 물품을 반환한다")
    void add() throws Exception {

        when(auctionItemService.add(eq(LOGIN_USER_ID), eq(10L), any(AuctionItemAddRequestDto.class)))
                .thenReturn(sampleDetail());

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAddRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("경매방에 물품이 추가되었습니다."))
                .andExpect(jsonPath("$.data.auctionItemId").value(1))
                .andExpect(jsonPath("$.data.bidIncrement").value(1000));
    }

    @Test
    @DisplayName("벌크로 추가하면 201과 추가·거절 목록을 함께 반환한다")
    void addAll() throws Exception {

        when(auctionItemService.addAll(eq(LOGIN_USER_ID), eq(10L), any(AuctionItemBulkAddRequestDto.class)))
                .thenReturn(new AuctionItemBulkAddResponseDto(
                        List.of(sampleDetail()),
                        List.of(AuctionItemBulkAddResponseDto.Failure.of(
                                21L, AuctionErrorType.PRODUCT_ALREADY_IN_AUCTION))));

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newBulkRequest(20L, 21L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.added[0].auctionItemId").value(1))
                .andExpect(jsonPath("$.data.failed[0].productId").value(21))
                .andExpect(jsonPath("$.data.failed[0].code").value(4005));
    }

    @Test
    @DisplayName("추가할 물품이 하나도 없으면 400을 반환한다")
    void addAllEmptyItems() throws Exception {

        AuctionItemBulkAddRequestDto request = AuctionItemBulkAddRequestDto.builder()
                .items(List.of())
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("items"));
    }

    @Test
    @DisplayName("벌크 항목의 시작가가 0이면 400을 반환한다")
    void addAllInvalidStartingPrice() throws Exception {

        AuctionItemBulkAddRequestDto request = AuctionItemBulkAddRequestDto.builder()
                .items(List.of(AuctionItemAddRequestDto.builder()
                        .productId(20L)
                        .startingPrice(0L)
                        .build()))
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    @DisplayName("경매방 물품 상한을 넘기면 409와 4009를 반환한다")
    void addAllLimitExceeded() throws Exception {

        when(auctionItemService.addAll(eq(LOGIN_USER_ID), eq(10L), any(AuctionItemBulkAddRequestDto.class)))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_LIMIT_EXCEEDED));

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newBulkRequest(20L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4009));
    }

    @Test
    @DisplayName("상품 ID가 없으면 추가 시 400을 반환한다")
    void addMissingProductId() throws Exception {

        AuctionItemAddRequestDto request = AuctionItemAddRequestDto.builder()
                .startingPrice(50_000L)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("productId"));
    }

    @Test
    @DisplayName("시작가가 0이면 추가 시 400을 반환한다")
    void addZeroStartingPrice() throws Exception {

        AuctionItemAddRequestDto request = AuctionItemAddRequestDto.builder()
                .productId(20L)
                .startingPrice(0L)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("startingPrice"));
    }

    @Test
    @DisplayName("시작가가 1조를 넘으면 추가 시 400을 반환한다")
    void addStartingPriceOutOfRange() throws Exception {

        AuctionItemAddRequestDto request = AuctionItemAddRequestDto.builder()
                .productId(20L)
                .startingPrice(1_000_000_000_001L)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("startingPrice"));
    }

    @Test
    @DisplayName("종료된 경매방에 추가하면 409와 4004를 반환한다")
    void addRoomClosed() throws Exception {

        when(auctionItemService.add(eq(LOGIN_USER_ID), eq(10L), any(AuctionItemAddRequestDto.class)))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_CLOSED));

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAddRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    @DisplayName("이미 경매방에 올라간 상품을 추가하면 409와 4005를 반환한다")
    void addProductAlreadyInAuction() throws Exception {

        when(auctionItemService.add(eq(LOGIN_USER_ID), eq(10L), any(AuctionItemAddRequestDto.class)))
                .thenThrow(new ApplicationException(AuctionErrorType.PRODUCT_ALREADY_IN_AUCTION));

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAddRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4005));
    }

    @Test
    @DisplayName("없거나 남의 경매방에 물품을 추가하면 404와 4002를 반환한다")
    void addRoomNotFound() throws Exception {

        when(auctionItemService.add(eq(LOGIN_USER_ID), eq(999L), any(AuctionItemAddRequestDto.class)))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        mockMvc.perform(post("/api/v1/auction-rooms/999/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAddRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    @DisplayName("남의 상품을 추가하면 404와 5001을 반환한다")
    void addProductNotFound() throws Exception {

        when(auctionItemService.add(eq(LOGIN_USER_ID), eq(10L), any(AuctionItemAddRequestDto.class)))
                .thenThrow(new ApplicationException(ProductErrorType.PRODUCT_NOT_FOUND));

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAddRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("비로그인 사용자는 물품을 추가할 수 없다")
    void addRejectsGuest() throws Exception {

        비로그인_상태로_바꾼다();

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAddRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    @DisplayName("물품을 빼면 200을 반환한다")
    void remove() throws Exception {

        mockMvc.perform(delete("/api/v1/auction-rooms/10/auction-items/30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("경매방에서 물품이 제외되었습니다."));
    }

    @Test
    @DisplayName("이미 시작된 물품을 빼면 409와 4006을 반환한다")
    void removeAlreadyStarted() throws Exception {

        doThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_ALREADY_STARTED))
                .when(auctionItemService).remove(LOGIN_USER_ID, 10L, 30L);

        mockMvc.perform(delete("/api/v1/auction-rooms/10/auction-items/30"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4006));
    }

    @Test
    @DisplayName("없거나 남의 경매방에서 물품을 빼면 404와 4002를 반환한다")
    void removeRoomNotFound() throws Exception {

        doThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND))
                .when(auctionItemService).remove(LOGIN_USER_ID, 999L, 30L);

        mockMvc.perform(delete("/api/v1/auction-rooms/999/auction-items/30"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    @DisplayName("없는 물품을 빼면 404와 4001을 반환한다")
    void removeItemNotFound() throws Exception {

        doThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND))
                .when(auctionItemService).remove(LOGIN_USER_ID, 10L, 999L);

        mockMvc.perform(delete("/api/v1/auction-rooms/10/auction-items/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    @DisplayName("비로그인 사용자는 물품을 뺄 수 없다")
    void removeRejectsGuest() throws Exception {

        비로그인_상태로_바꾼다();

        mockMvc.perform(delete("/api/v1/auction-rooms/10/auction-items/30"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    @DisplayName("물품 경매를 시작하면 200과 갱신된 물품 상세를 반환한다")
    void start() throws Exception {

        when(auctionItemService.start(eq(30L), eq(LOGIN_USER_ID), any(AuctionItemStartRequestDto.class)))
                .thenReturn(sampleDetail());

        mockMvc.perform(post("/api/v1/auction-items/30/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStartRequest(30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.endAt").exists());
    }

    @Test
    @DisplayName("경매 시간이 없으면 시작 시 400을 반환한다")
    void startWithoutDuration() throws Exception {

        mockMvc.perform(post("/api/v1/auction-items/30/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStartRequest(null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("durationMinutes"));
    }

    @Test
    @DisplayName("경매 시간이 0이면 시작 시 400을 반환한다")
    void startWithZeroDuration() throws Exception {

        mockMvc.perform(post("/api/v1/auction-items/30/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStartRequest(0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("durationMinutes"));
    }

    /**
     * 거절만 검증하면 상한·하한이 한 칸 어긋나 있어도 통과한다. 경계 안쪽 값이 실제로
     * 받아들여지는지 함께 본다.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 720})
    @DisplayName("경매 시간이 1분과 720분이면 시작이 받아들여진다")
    void startAcceptsBoundaryDuration(int durationMinutes) throws Exception {

        when(auctionItemService.start(eq(30L), eq(LOGIN_USER_ID), any(AuctionItemStartRequestDto.class)))
                .thenReturn(sampleDetail());

        mockMvc.perform(post("/api/v1/auction-items/30/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStartRequest(durationMinutes))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("경매 시간이 12시간을 넘으면 시작 시 400을 반환한다")
    void startWithDurationOutOfRange() throws Exception {

        mockMvc.perform(post("/api/v1/auction-items/30/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStartRequest(721))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("durationMinutes"));
    }

    @Test
    @DisplayName("없거나 남의 물품을 시작하면 404와 4001을 반환한다")
    void startItemNotFound() throws Exception {

        when(auctionItemService.start(eq(999L), eq(LOGIN_USER_ID), any(AuctionItemStartRequestDto.class)))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        mockMvc.perform(post("/api/v1/auction-items/999/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStartRequest(30))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    @DisplayName("종료된 경매방의 물품을 시작하면 409와 4004를 반환한다")
    void startRoomClosed() throws Exception {

        when(auctionItemService.start(eq(30L), eq(LOGIN_USER_ID), any(AuctionItemStartRequestDto.class)))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_CLOSED));

        mockMvc.perform(post("/api/v1/auction-items/30/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStartRequest(30))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    @DisplayName("대기 중이 아닌 물품을 시작하면 409와 4007을 반환한다")
    void startItemNotReady() throws Exception {

        when(auctionItemService.start(eq(30L), eq(LOGIN_USER_ID), any(AuctionItemStartRequestDto.class)))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_READY));

        mockMvc.perform(post("/api/v1/auction-items/30/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStartRequest(30))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4007));
    }

    @Test
    @DisplayName("이미 3개가 진행 중이면 시작 시 409와 4008을 반환한다")
    void startLimitExceeded() throws Exception {

        when(auctionItemService.start(eq(30L), eq(LOGIN_USER_ID), any(AuctionItemStartRequestDto.class)))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_START_LIMIT_EXCEEDED));

        mockMvc.perform(post("/api/v1/auction-items/30/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStartRequest(30))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4008));
    }

    @Test
    @DisplayName("비로그인 사용자는 물품을 시작할 수 없다")
    void startRejectsGuest() throws Exception {

        비로그인_상태로_바꾼다();

        mockMvc.perform(post("/api/v1/auction-items/30/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newStartRequest(30))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    @DisplayName("마감을 앞당기면 200과 앞당겨진 마감 시각을 반환한다")
    void closeEarly() throws Exception {

        when(auctionItemCloseService.closeEarly(LOGIN_USER_ID, 30L))
                .thenReturn(new AuctionItemCloseEarlyResponseDto(
                        30L, LocalDateTime.now().plusSeconds(60), 60));

        mockMvc.perform(post("/api/v1/auction-items/30/close-early"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.auctionItemId").value(30))
                .andExpect(jsonPath("$.data.remainingSeconds").value(60))
                .andExpect(jsonPath("$.data.endAt").exists());
    }

    @Test
    @DisplayName("없거나 남의 물품의 마감을 앞당기면 404와 4001을 반환한다")
    void closeEarlyItemNotFound() throws Exception {

        when(auctionItemCloseService.closeEarly(LOGIN_USER_ID, 999L))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        mockMvc.perform(post("/api/v1/auction-items/999/close-early"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    @DisplayName("진행 중이 아닌 물품의 마감을 앞당기면 409와 4010을 반환한다")
    void closeEarlyItemNotInProgress() throws Exception {

        when(auctionItemCloseService.closeEarly(LOGIN_USER_ID, 30L))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_IN_PROGRESS));

        mockMvc.perform(post("/api/v1/auction-items/30/close-early"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4010));
    }

    @Test
    @DisplayName("이미 마감이 임박했으면 앞당기기에 409와 4011을 반환한다")
    void closeEarlyAlreadyClosingSoon() throws Exception {

        when(auctionItemCloseService.closeEarly(LOGIN_USER_ID, 30L))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_ALREADY_CLOSING_SOON));

        mockMvc.perform(post("/api/v1/auction-items/30/close-early"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4011));
    }

    @Test
    @DisplayName("비로그인 사용자는 마감을 앞당길 수 없다")
    void closeEarlyRejectsGuest() throws Exception {

        비로그인_상태로_바꾼다();

        mockMvc.perform(post("/api/v1/auction-items/30/close-early"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1005));
    }
}
