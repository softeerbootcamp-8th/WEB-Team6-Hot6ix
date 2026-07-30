package com.hot6ix.upbid.domain.deal.repository;

import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealCandidateRepository extends JpaRepository<DealCandidate, Long> {

    /**
     * 물품에 낙찰 후보가 이미 만들어졌는지 확인한다. 같은 마감 이벤트를 두 번 받아도 후보가
     * 한 번만 생기게 하는 멱등 검사다. 순위 유니크 제약이 없으므로
     * {@code AuctionItemRepository.findByIdForUpdate}로 물품 행을 잠근 뒤에 호출한다.
     *
     * @param auctionItemId 확인할 물품 ID
     * @return 후보가 하나라도 있으면 true
     */
    boolean existsByAuctionItem_AuctionItemId(Long auctionItemId);

    /**
     * 물품의 낙찰 후보를 순위 오름차순으로 모두 조회한다. 한 물품의 후보는 최대 5행이라,
     * 거래 상태 변경에 필요한 판단 재료({@code COMPLETED} 존재 여부, 현재 낙찰자, 대상 후보)를
     * 전체 조회 한 번으로 모두 얻는다. {@code bidder}는 차순위 알림에 낙찰자 ID가 필요해
     * fetch join한다.
     *
     * @param auctionItemId 조회할 물품 ID
     * @return 순위 오름차순 후보 목록. 후보가 없으면 빈 목록
     */
    @Query("select dc from DealCandidate dc "
            + "join fetch dc.bidder "
            + "where dc.auctionItem.auctionItemId = :auctionItemId "
            + "order by dc.candidateRank asc")
    List<DealCandidate> findAllByAuctionItemId(@Param("auctionItemId") Long auctionItemId);
}
