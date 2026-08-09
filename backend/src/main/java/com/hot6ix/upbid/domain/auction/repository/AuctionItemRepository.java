package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionItemRepository extends JpaRepository<AuctionItem, Long>, AuctionItemRepositoryCustom {

    /**
     * 이 경매방에 속한 물품 중 한 번이라도 READY가 아닌 상태로 경매에 올라간 적이 있는 게
     * 있는지 확인한다. 경매방 설정 수정(PATCH) 시 "경매 시작 전"만 허용하는 규칙을
     * 검증하는 데 쓰인다.
     */
    boolean existsByAuctionRoom_AuctionRoomIdAndStatusNot(Long auctionRoomId, AuctionItemStatus status);

    /**
     * 경매방에 등록된 물품 개수를 센다. 경매방 응답 DTO의 itemCount에 쓰인다.
     */
    long countByAuctionRoom_AuctionRoomId(Long auctionRoomId);

    /**
     * 경매방에서 지정 상태인 물품 개수를 센다. 물품 시작 시 "방당 동시 3개" 제한을
     * 검증하는 데 쓴다. 세고 나서 바꾸는 흐름이라 호출 전에 경매방 행을 잠가야 한다.
     */
    long countByAuctionRoom_AuctionRoomIdAndStatus(Long auctionRoomId, AuctionItemStatus status);

    /**
     * 목록 정렬 순위를 만드는 식. 진행중 → 대기 → 낙찰 → 유찰 순이며
     * {@code AuctionItemStatus} 선언 순서와 다르므로 식으로 계산한다.
     * 별칭 {@code ai}에 묶여 있으므로 다른 별칭을 쓰는 쿼리에 그대로 가져다 쓸 수 없다.
     * 네 상태를 모두 명시하고 {@code else}는 5로 둔다. 상태가 추가되면 유찰과 섞이지 않고
     * 목록 맨 뒤로 밀려 눈에 띈다.
     */
    String STATUS_RANK = "case ai.status"
            + " when com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.IN_PROGRESS then 1"
            + " when com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.READY then 2"
            + " when com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.SOLD then 3"
            + " when com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus.FAILED then 4"
            + " else 5 end";

    /**
     * 목록 조회 상한. 페이지네이션이 없으므로 한 응답의 크기를 여기서만 막는다.
     * 물품이 이보다 많은 경매방이 생기면 뒤쪽이 조용히 잘리므로, 그때는 상한을 올릴 게
     * 아니라 페이지네이션을 다시 넣어야 한다.
     */
    int MAX_SUMMARY_SIZE = 100;

    /**
     * 경매방의 물품 목록을 상태 우선 순서로 최대 {@link #MAX_SUMMARY_SIZE}건 조회한다.
     * 페이지네이션은 두지 않는다. 한 경매방의 물품이 한 응답에 다 담기는 규모라는 전제이며,
     * 쿼리 한 번이 곧 하나의 스냅샷이라 페이지 경계에서 항목이 밀리거나 빠지지 않는다.
     */
    default List<AuctionItemSummaryResponseDto> findSummaries(Long auctionRoomId) {
        return findSummaries(auctionRoomId, Limit.of(MAX_SUMMARY_SIZE));
    }

    @Query("select new com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto("
            + "  ai.auctionItemId, p.name, p.imageUrl, ai.currentPrice, ai.status, ai.endAt) "
            + "from AuctionItem ai "
            + "join ai.product p "
            + "where ai.auctionRoom.auctionRoomId = :auctionRoomId "
            + "order by " + STATUS_RANK + " asc, ai.auctionItemId asc")
    List<AuctionItemSummaryResponseDto> findSummaries(
            @Param("auctionRoomId") Long auctionRoomId, Limit limit);

    /**
     * 경매방의 물품별 결과를 최대 {@link #MAX_SUMMARY_SIZE}건 조회한다. 정렬과 상한을
     * {@link #findSummaries}와 같게 두는 이유는 두 목록이 같은 방을 보여주기 때문이다 —
     * 순서가 다르면 물품 목록과 결과 목록에서 같은 물품이 다른 자리에 있게 된다.
     *
     * <p>최고 입찰자를 {@code left join}으로 가져온다. 입찰이 한 번도 없었으면
     * {@code leaderUser}가 없고, 그런 물품이 결과에서 빠지면 유찰을 셀 수 없다.
     *
     * <p>탈퇴한 회원도 걸러내지 않는다. 낙찰은 지나간 사실이라 낙찰자가 나갔다고 결과에서
     * 사라지면 안 된다 — {@code DealRepository.findDeals}가 거래 내역에서 같은 판단을 한다.
     */
    default List<AuctionItemResultProjection> findResults(Long auctionRoomId) {
        return findResults(auctionRoomId, Limit.of(MAX_SUMMARY_SIZE));
    }

    @Query("select new com.hot6ix.upbid.domain.auction.repository.AuctionItemResultProjection("
            + "  ai.auctionItemId, p.name, p.imageUrl, ai.status, ai.currentPrice, lu.nickname) "
            + "from AuctionItem ai "
            + "join ai.product p "
            + "left join ai.leaderUser lu "
            + "where ai.auctionRoom.auctionRoomId = :auctionRoomId "
            + "order by " + STATUS_RANK + " asc, ai.auctionItemId asc")
    List<AuctionItemResultProjection> findResults(
            @Param("auctionRoomId") Long auctionRoomId, Limit limit);

    /**
     * 경매방의 마감된 물품을 최근 마감 순으로 조회한다. 거래 현황이 쓰는 목록이며, 아직
     * 시작하지 않았거나 진행 중인 물품은 거래가 시작된 적이 없어 빠진다.
     *
     * <p>정렬 키를 둘 두는 이유는 순서가 하나로 정해지게 하기 위해서다. 같은 시각에 마감된
     * 물품이 흔하고, 그때 순서가 흔들리면 화면이 요청마다 다르게 보인다.
     */
    default List<ClosedAuctionItemProjection> findClosedItems(Long auctionRoomId) {
        return findClosedItems(auctionRoomId,
                List.of(AuctionItemStatus.SOLD, AuctionItemStatus.FAILED),
                Limit.of(MAX_SUMMARY_SIZE));
    }

    @Query("select new com.hot6ix.upbid.domain.auction.repository.ClosedAuctionItemProjection("
            + "  ai.auctionItemId, p.name, ai.status) "
            + "from AuctionItem ai "
            + "join ai.product p "
            + "where ai.auctionRoom.auctionRoomId = :auctionRoomId "
            + "and ai.status in :statuses "
            + "order by ai.endAt desc, ai.auctionItemId desc")
    List<ClosedAuctionItemProjection> findClosedItems(@Param("auctionRoomId") Long auctionRoomId,
                                                      @Param("statuses") Collection<AuctionItemStatus> statuses,
                                                      Limit limit);

    /**
     * 물품 상세를 조회한다. 상태로 거르지 않으므로 낙찰·유찰된 물품도 조회된다.
     * 유찰 화면이 시작가를 표시하므로 {@code startingPrice}도 함께 내린다 —
     * 유찰이면 입찰이 없어 {@code currentPrice}가 시작가와 같지만, 그건 결과일 뿐이다.
     */
    @Query("select new com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto("
            + "  ai.auctionItemId, ai.auctionRoom.auctionRoomId, "
            + "  p.name, p.description, p.imageUrl, p.referenceUrl, "
            + "  ai.startingPrice, ai.currentPrice, ai.bidIncrement, ai.status, ai.endAt) "
            + "from AuctionItem ai "
            + "join ai.product p "
            + "where ai.auctionItemId = :auctionItemId")
    Optional<AuctionItemDetailResponseDto> findDetail(@Param("auctionItemId") Long auctionItemId);

    /**
     * 물품 행에 쓰기 락을 걸고 조회한다. 거래 상태 변경은 읽고 검사한 뒤 쓰는 흐름이라 상태
     * 검사만으로는 동시 요청을 막지 못한다. 트랜잭션 안에서만 호출해야 한다.
     * fetch join하지 않는 이유는 MySQL {@code FOR UPDATE}가 조인된 행까지 잠그기 때문이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ai from AuctionItem ai where ai.auctionItemId = :auctionItemId")
    Optional<AuctionItem> findByIdForUpdate(@Param("auctionItemId") Long auctionItemId);

    /**
     * 경매방에서 지정 상태인 물품의 ID를 <b>오름차순으로</b> 조회한다. 방 종료가 진행 중인 물품을
     * 한꺼번에 마감할 때 쓴다.
     *
     * <p>정렬이 이 쿼리의 핵심이다. 방 종료는 물품마다 행 락을 잡는데, 잡는 순서가 요청마다
     * 다르면 같은 방을 동시에 닫는 두 요청이 서로의 물품을 기다리다 데드락에 빠진다. ID
     * 오름차순으로 고정하면 그 순환이 만들어지지 않는다.
     *
     * <p>엔티티가 아니라 ID만 뽑는 이유는 실제 마감이 {@code AuctionItemCloseService}에서
     * 행 락을 걸고 다시 읽기 때문이다. 여기서 엔티티를 읽어봐야 그 사이에 값이 바뀔 수 있다.
     */
    @Query("select ai.auctionItemId from AuctionItem ai "
            + "where ai.auctionRoom.auctionRoomId = :auctionRoomId and ai.status = :status "
            + "order by ai.auctionItemId asc")
    List<Long> findIdsByRoomAndStatus(
            @Param("auctionRoomId") Long auctionRoomId, @Param("status") AuctionItemStatus status);

    /**
     * 마감 임박 알림을 판정하는 데 필요한 값만 한 번에 읽는다. 알림 시각이
     * {@code endAt - softCloseTriggerSeconds}라 물품과 경매방 양쪽 값이 함께 필요하다.
     *
     * <p><b>행 락을 걸지 않는다.</b> 알림은 상태를 바꾸지 않는 순수 조회이고, 하필 입찰이 가장
     * 몰리는 구간에서 도는 조회라 여기서 {@code FOR UPDATE}를 잡으면 입찰을 막는다.
     * 마감({@code findByIdForUpdate})이 락을 잡는 것은 그쪽이 쓰기를 하기 때문이다.
     */
    @Query("select new com.hot6ix.upbid.domain.auction.repository.ClosingSoonItemProjection("
            + "  ar.auctionRoomId, p.name, ai.status, ai.endAt, ar.softCloseTriggerSeconds) "
            + "from AuctionItem ai "
            + "join ai.product p "
            + "join ai.auctionRoom ar "
            + "where ai.auctionItemId = :auctionItemId")
    Optional<ClosingSoonItemProjection> findClosingSoonView(@Param("auctionItemId") Long auctionItemId);

    /**
     * 진행 중이면서 마감 시각이 정해진 물품을 전부 조회한다. 서버 기동 시 마감 예약을 다시
     * 거는 {@code AuctionRecoveryRunner}가 쓴다.
     *
     * <p>경매방으로 좁히지 않는 것이 이 쿼리의 요점이다. 프로세스가 죽으면 예약이 방을
     * 가리지 않고 전부 사라지므로 복구도 전부를 대상으로 해야 한다.
     *
     * <p>{@code endAt}이 null인 물품은 뺀다. 시작하면 반드시 채워지는 값이라 정상 경로에는
     * 없지만, 있더라도 예약을 걸 시각이 없어 어차피 아무것도 할 수 없다.
     */
    @Query("select new com.hot6ix.upbid.domain.auction.repository.InProgressAuctionItemProjection("
            + "  ai.auctionItemId, ai.endAt) "
            + "from AuctionItem ai "
            + "where ai.status = :status and ai.endAt is not null "
            + "order by ai.endAt asc")
    List<InProgressAuctionItemProjection> findScheduleTargets(@Param("status") AuctionItemStatus status);

    /** 물품 전체를 읽지 않고 상태만 본다. 마감됐는지 판정하는 데 쓴다. */
    @Query("select ai.status from AuctionItem ai where ai.auctionItemId = :auctionItemId")
    Optional<AuctionItemStatus> findStatus(@Param("auctionItemId") Long auctionItemId);

    /**
     * 물품을 올린 판매자의 회원 ID를 조회한다. 판매자 본인 입찰을 거르는 데 쓰고,
     * 낙찰 후보 목록에서 요청자가 판매자인지 판정하는 데도 쓴다.
     * 조회에 {@link #findByIdForUpdate}를 쓰면 읽기 요청이 거래 상태 변경을 막으므로 쓰지 않는다.
     *
     * @return 판매자의 회원 ID. 물품이 없거나 경매방에 판매자가 없으면 빈 값
     */
    @Query("select sp.user.userId from AuctionItem ai "
            + "join ai.auctionRoom ar "
            + "join ar.sellerProfile sp "
            + "where ai.auctionItemId = :auctionItemId")
    Optional<Long> findSellerUserId(@Param("auctionItemId") Long auctionItemId);

    /**
     * 입찰이 <b>행 락을 잡기 전에</b> 필요한 값을 한 번에 읽는다. {@link #findSellerUserId}가
     * 하던 일에 상품명과 Soft Close 설정을 얹은 것이다.
     *
     * <p>이 넷은 원래 락 안에서 읽혔다. 이벤트에 넣을 상품명은 {@code auctionItem.getProduct()}로,
     * 연장 판단에 쓰는 방 설정은 {@code auctionItem.getAuctionRoom()}으로 꺼내는데 둘 다 지연
     * 로딩이라 그 자리에서 SELECT 가 나갔고, 하필 그 자리가 락 안이었다. 락은 줄을 세우는
     * 장치라 한 요청이 잡는 시간이 뒤에 선 요청 수만큼 곱해진다.
     *
     * <p>{@code findSellerUserId}를 넓히지 않고 따로 둔 것은 그쪽을 낙찰 후보 조회
     * ({@code DealCandidateService})도 쓰기 때문이다. 거기서는 판매자 ID 하나만 필요해서,
     * 넓히면 안 쓰는 값 넷을 매번 같이 읽게 된다.
     *
     * <p><b>여기서 읽은 값으로 현재가나 상태를 판단하면 안 된다.</b> 그건 입찰마다 바뀌므로
     * 락을 잡고 다시 읽어야 한다({@link #findByIdForUpdate}).
     */
    @Query("select new com.hot6ix.upbid.domain.auction.repository.BidContextProjection("
            + "  sp.user.userId, ar.auctionRoomId, p.name, "
            + "  ar.softCloseTriggerSeconds, ar.softCloseExtendSeconds) "
            + "from AuctionItem ai "
            + "join ai.auctionRoom ar "
            + "join ar.sellerProfile sp "
            + "join ai.product p "
            + "where ai.auctionItemId = :auctionItemId")
    Optional<BidContextProjection> findBidContext(@Param("auctionItemId") Long auctionItemId);

    /**
     * 이 물품이 속한 경매방에 입찰자의 참여 행이 있는지 확인한다. 입찰을 받을지 판정하는 데 쓴다.
     *
     * <p>{@code agreed_at}이 채워진 행만 참여로 본다. 그 행은 공유 코드를 알고 로그인한
     * 사용자가 약관 동의 API를 부를 때만 생기므로, 물품 ID만 알고 보낸 요청과 링크를 거쳐
     * 들어온 요청이 여기서 갈린다.
     *
     * <p>조회에 {@link #findByIdForUpdate}를 쓰지 않고 별개 쿼리로 두는 이유는 이 판정이
     * 물품 상태와 무관해서 락 없이 끝나기 때문이다. 자격이 없는 요청은 락을 잡기 전에 거절된다.
     */
    @Query("select count(ap) > 0 from AuctionItem ai "
            + "join ai.auctionRoom ar "
            + "join AuctionParticipant ap on ap.auctionRoom = ar "
            + "where ai.auctionItemId = :auctionItemId "
            + "  and ap.user.userId = :userId "
            + "  and ap.agreedAt is not null")
    boolean existsParticipant(@Param("auctionItemId") Long auctionItemId,
                              @Param("userId") Long userId);
}
