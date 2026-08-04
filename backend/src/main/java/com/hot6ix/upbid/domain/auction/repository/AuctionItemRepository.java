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

public interface AuctionItemRepository extends JpaRepository<AuctionItem, Long> {

    /**
     * 이 상품이 어느 경매방엔가 물품으로 올라가 있는지 확인한다. 상태를 따지지 않는 이유는
     * "한 상품은 한 번에 한 경매방에만" 규칙이 상태와 무관하기 때문이다. 물품을 빼면 행이
     * 물리 삭제되므로, 뺀 상품은 이 검사를 자연히 통과해 다시 올릴 수 있다.
     *
     * <p>이 검사만으로는 동시 요청 두 건이 함께 통과할 수 있어
     * {@code auction_items.product_id}에 unique 제약이 함께 걸려 있다. 여기서 거르는 건
     * 정상 경로에서 읽기 쉬운 에러를 주기 위한 것이고, 최후 방어선은 그 제약이다.
     */
    boolean existsByProduct_ProductId(Long productId);

    /**
     * 넘긴 상품들 중 <b>이미 어느 경매방엔가 올라가 있는 상품의 ID만</b> 골라낸다.
     * {@link #existsByProduct_ProductId}의 벌크판이며 판정 규칙도 같다(상태를 따지지 않는다).
     *
     * <p>벌크 추가에서 상품 수만큼 exists 쿼리를 돌리지 않으려고 한 번에 조회한다.
     * 엔티티가 아니라 ID만 뽑는 이유는 이 값이 "거절 목록"을 만드는 데만 쓰이기 때문이다.
     */
    @Query("select ai.product.productId from AuctionItem ai where ai.product.productId in :productIds")
    List<Long> findProductIdsIn(@Param("productIds") List<Long> productIds);

    /**
     * 이 상품이 한 번이라도 READY가 아닌 상태로 경매에 올라간 적이 있는지 확인한다
     * (진행중·낙찰·유찰 전부 포함). Product 수정·삭제 시 "경매방이 시작된 적 있는 상품은
     * 이후로도 계속 수정·삭제 불가" 규칙을 검증하는 데 쓰인다.
     */
    boolean existsByProduct_ProductIdAndStatusNot(Long productId, AuctionItemStatus status);

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
}
