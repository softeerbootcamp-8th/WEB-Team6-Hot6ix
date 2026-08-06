package com.hot6ix.upbid.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus;
import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import com.hot6ix.upbid.domain.product.entity.ProductSortType;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureTestEntityManager
@Import(JpaConfig.class)
class ProductRepositoryTest extends AbstractMySqlContainerTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private SellerProfile newSellerProfile(String email) {
        User user = userRepository.saveAndFlush(User.builder()
                .email(email)
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build());

        return sellerProfileRepository.saveAndFlush(SellerProfile.builder()
                .user(user)
                .storeName("승민상점")
                .build());
    }

    private Product newProduct(SellerProfile sellerProfile) {
        return Product.builder()
                .sellerProfile(sellerProfile)
                .name("승민의 노트북")
                .description("깨끗합니다")
                .build();
    }

    private Product newProduct(SellerProfile sellerProfile, String name) {
        return productRepository.saveAndFlush(Product.builder()
                .sellerProfile(sellerProfile)
                .name(name)
                .build());
    }

    private AuctionRoom newAuctionRoom(SellerProfile sellerProfile) {
        return entityManager.persist(AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(sellerProfile)
                .name("승민상점 경매방")
                .build());
    }

    private AuctionItem newAuctionItem(AuctionRoom auctionRoom, Product product, AuctionItemStatus status) {
        return entityManager.persist(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(product)
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(status)
                .build());
    }

    private DealCandidate newCandidate(
            AuctionItem item, String bidderEmail, int candidateRank, DealCandidateStatus status) {

        User bidder = userRepository.saveAndFlush(User.builder()
                .email(bidderEmail)
                .password("password")
                .nickname("입찰자" + candidateRank)
                .phoneNumber("010-1234-5678")
                .build());

        return entityManager.persist(DealCandidate.builder()
                .auctionItem(item)
                .bidder(bidder)
                .candidateRank(candidateRank)
                .bidAmount(10_000L)
                .status(status)
                .build());
    }

    /** 후보가 된 뒤에 탈퇴한 회원. 후보 생성 시점에 걸러지는 경우와는 다른 경로다. */
    private DealCandidate withdrawnCandidate(
            AuctionItem item, String bidderEmail, int candidateRank, DealCandidateStatus status) {

        DealCandidate candidate = newCandidate(item, bidderEmail, candidateRank, status);
        candidate.getBidder().softDelete(LocalDateTime.of(2026, 7, 30, 9, 0));
        return candidate;
    }

    @Test
    @DisplayName("name 없이 저장하면 예외가 발생한다")
    void name_notNull_violated() {

        SellerProfile sellerProfile = newSellerProfile("seller1@hot6ix.com");
        Product product = Product.builder()
                .sellerProfile(sellerProfile)
                .build();

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("sellerProfile 없이 저장하면 예외가 발생한다")
    void sellerProfile_notNull_violated() {

        Product product = Product.builder()
                .name("승민의 노트북")
                .build();

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("소유자의 활성 상품을 productId로 조회한다")
    void findByProductIdAndSellerProfile_found() {

        SellerProfile sellerProfile = newSellerProfile("seller2@hot6ix.com");
        Product product = productRepository.saveAndFlush(newProduct(sellerProfile));

        Optional<Product> found = productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                product.getProductId(), sellerProfile.getSellerProfileId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("승민의 노트북");
    }

    @Test
    @DisplayName("soft delete된 상품은 조회되지 않는다")
    void findByProductIdAndSellerProfile_excludesDeleted() {

        SellerProfile sellerProfile = newSellerProfile("seller3@hot6ix.com");
        Product product = productRepository.saveAndFlush(newProduct(sellerProfile));
        product.softDelete(LocalDateTime.now());
        productRepository.flush();

        Optional<Product> found = productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                product.getProductId(), sellerProfile.getSellerProfileId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("다른 판매자의 상품은 조회되지 않는다")
    void findByProductIdAndSellerProfile_excludesOtherOwner() {

        SellerProfile owner = newSellerProfile("seller4@hot6ix.com");
        SellerProfile other = newSellerProfile("seller5@hot6ix.com");
        Product product = productRepository.saveAndFlush(newProduct(owner));

        Optional<Product> found = productRepository.findByProductIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                product.getProductId(), other.getSellerProfileId());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("연결된 AuctionItem이 없으면 UNREGISTERED로 조회된다")
    void search_unregistered() {

        SellerProfile sellerProfile = newSellerProfile("seller6@hot6ix.com");
        newProduct(sellerProfile, "미등록상품");

        List<ProductSummaryResponseDto> results =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, null, null).getContent();

        assertThat(results).extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.UNREGISTERED);
    }

    @Test
    @DisplayName("AuctionItem 상태에 따라 READY·IN_PROGRESS·ENDED로 매핑된다")
    void search_mapsAuctionItemStatus() {

        SellerProfile sellerProfile = newSellerProfile("seller7@hot6ix.com");
        AuctionRoom room = newAuctionRoom(sellerProfile);

        Product ready = newProduct(sellerProfile, "대기상품");
        newAuctionItem(room, ready, AuctionItemStatus.READY);

        Product inProgress = newProduct(sellerProfile, "진행상품");
        newAuctionItem(room, inProgress, AuctionItemStatus.IN_PROGRESS);

        Product sold = newProduct(sellerProfile, "낙찰상품");
        AuctionItem soldItem = newAuctionItem(room, sold, AuctionItemStatus.SOLD);
        newCandidate(soldItem, "bidder7-1@hot6ix.com", 1, DealCandidateStatus.WAITING);

        Product failed = newProduct(sellerProfile, "유찰상품");
        newAuctionItem(room, failed, AuctionItemStatus.FAILED);
        entityManager.flush();

        List<ProductSummaryResponseDto> results =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, null, null).getContent();

        assertThat(results)
                .filteredOn(r -> r.productId().equals(ready.getProductId()))
                .extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.READY);
        assertThat(results)
                .filteredOn(r -> r.productId().equals(inProgress.getProductId()))
                .extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.IN_PROGRESS);
        assertThat(results)
                .filteredOn(r -> r.productId().equals(sold.getProductId()))
                .extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.ENDED);
        // 유찰은 판매자가 다시 팔 수 있어야 하므로 ENDED가 아니라 UNREGISTERED로 되돌아간다.
        assertThat(results)
                .filteredOn(r -> r.productId().equals(failed.getProductId()))
                .extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.UNREGISTERED);
    }

    @Test
    @DisplayName("낙찰 후보 전원이 실패하면 ENDED가 아니라 UNREGISTERED로 되돌아간다 — 다시 팔 수 있어야 한다")
    void search_soldWithAllFailedCandidates_isUnregistered() {

        SellerProfile sellerProfile = newSellerProfile("seller13@hot6ix.com");
        AuctionRoom room = newAuctionRoom(sellerProfile);

        Product allFailed = newProduct(sellerProfile, "전원실패상품");
        AuctionItem item = newAuctionItem(room, allFailed, AuctionItemStatus.SOLD);
        newCandidate(item, "bidder13-1@hot6ix.com", 1, DealCandidateStatus.FAILED);
        newCandidate(item, "bidder13-2@hot6ix.com", 2, DealCandidateStatus.FAILED);
        entityManager.flush();

        List<ProductSummaryResponseDto> results =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, null, null).getContent();

        assertThat(results).extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.UNREGISTERED);
    }

    @Test
    @DisplayName("낙찰됐지만 아직 후보가 없으면(마감 직후 창) UNREGISTERED로 본다")
    void search_soldWithNoCandidatesYet_isUnregistered() {

        SellerProfile sellerProfile = newSellerProfile("seller15@hot6ix.com");
        AuctionRoom room = newAuctionRoom(sellerProfile);

        Product sold = newProduct(sellerProfile, "후보없음상품");
        newAuctionItem(room, sold, AuctionItemStatus.SOLD);
        entityManager.flush();

        List<ProductSummaryResponseDto> results =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, null, null).getContent();

        assertThat(results).extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.UNREGISTERED);
    }

    @Test
    @DisplayName("WAITING 후보의 입찰자가 탈퇴하면 판매자 화면(거래 현황)과 같이 UNREGISTERED로 본다")
    void search_soldWithWithdrawnWaitingBidder_isUnregistered() {

        SellerProfile sellerProfile = newSellerProfile("seller16@hot6ix.com");
        AuctionRoom room = newAuctionRoom(sellerProfile);

        Product product = newProduct(sellerProfile, "탈퇴후보상품");
        AuctionItem item = newAuctionItem(room, product, AuctionItemStatus.SOLD);
        withdrawnCandidate(item, "bidder16-1@hot6ix.com", 1, DealCandidateStatus.WAITING);
        entityManager.flush();

        List<ProductSummaryResponseDto> results =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, null, null).getContent();

        assertThat(results).extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.UNREGISTERED);
    }

    @Test
    @DisplayName("COMPLETED 후보는 낙찰자가 탈퇴했어도 이미 끝난 거래라 ENDED로 본다")
    void search_soldWithWithdrawnCompletedBidder_isEnded() {

        SellerProfile sellerProfile = newSellerProfile("seller17@hot6ix.com");
        AuctionRoom room = newAuctionRoom(sellerProfile);

        Product product = newProduct(sellerProfile, "완료후탈퇴상품");
        AuctionItem item = newAuctionItem(room, product, AuctionItemStatus.SOLD);
        withdrawnCandidate(item, "bidder17-1@hot6ix.com", 1, DealCandidateStatus.COMPLETED);
        entityManager.flush();

        List<ProductSummaryResponseDto> results =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, null, null).getContent();

        assertThat(results).extracting(ProductSummaryResponseDto::status)
                .containsExactly(ProductListingStatus.ENDED);
    }

    @Test
    @DisplayName("UNREGISTERED 필터는 이력 없음·유찰·전원실패 상품을 모두 포함한다 (3VL 회귀)")
    void search_filtersByUnregistered_includesFailedAndAllFailed() {

        SellerProfile sellerProfile = newSellerProfile("seller18@hot6ix.com");
        AuctionRoom room = newAuctionRoom(sellerProfile);

        Product neverListed = newProduct(sellerProfile, "이력없음상품");

        Product failed = newProduct(sellerProfile, "유찰상품2");
        newAuctionItem(room, failed, AuctionItemStatus.FAILED);

        Product allFailed = newProduct(sellerProfile, "전원실패상품2");
        AuctionItem allFailedItem = newAuctionItem(room, allFailed, AuctionItemStatus.SOLD);
        newCandidate(allFailedItem, "bidder18-1@hot6ix.com", 1, DealCandidateStatus.FAILED);

        Product ready = newProduct(sellerProfile, "대기상품2");
        newAuctionItem(room, ready, AuctionItemStatus.READY);

        Product ended = newProduct(sellerProfile, "종료상품2");
        AuctionItem endedItem = newAuctionItem(room, ended, AuctionItemStatus.SOLD);
        newCandidate(endedItem, "bidder18-2@hot6ix.com", 1, DealCandidateStatus.WAITING);
        entityManager.flush();

        List<ProductSummaryResponseDto> results = productRepository.search(
                sellerProfile.getSellerProfileId(), null, ProductListingStatus.UNREGISTERED, null, null).getContent();

        assertThat(results).extracting(ProductSummaryResponseDto::productId)
                .containsExactlyInAnyOrder(
                        neverListed.getProductId(), failed.getProductId(), allFailed.getProductId());
    }

    @Test
    @DisplayName("ENDED 필터에는 살아 있는 거래가 붙은 SOLD 물품만 나온다 (물품 없는 상품이 섞이지 않는다)")
    void search_filtersByEnded_excludesUnregistered() {

        SellerProfile sellerProfile = newSellerProfile("seller19@hot6ix.com");
        AuctionRoom room = newAuctionRoom(sellerProfile);

        newProduct(sellerProfile, "이력없음상품2");

        Product ended = newProduct(sellerProfile, "종료상품3");
        AuctionItem endedItem = newAuctionItem(room, ended, AuctionItemStatus.SOLD);
        newCandidate(endedItem, "bidder19-1@hot6ix.com", 1, DealCandidateStatus.WAITING);
        entityManager.flush();

        List<ProductSummaryResponseDto> results = productRepository.search(
                sellerProfile.getSellerProfileId(), null, ProductListingStatus.ENDED, null, null).getContent();

        assertThat(results).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(ended.getProductId());
    }

    @Test
    @DisplayName("상세용 파생 상태 조회가 목록과 같은 값을 준다")
    void findListingStatus() {

        SellerProfile sellerProfile = newSellerProfile("seller12@hot6ix.com");
        AuctionRoom room = newAuctionRoom(sellerProfile);

        Product unregistered = newProduct(sellerProfile, "미등록상품");

        Product sold = newProduct(sellerProfile, "낙찰상품");
        AuctionItem soldItem = newAuctionItem(room, sold, AuctionItemStatus.SOLD);
        newCandidate(soldItem, "bidder12-1@hot6ix.com", 1, DealCandidateStatus.WAITING);

        Product failedProduct = newProduct(sellerProfile, "유찰상품3");
        newAuctionItem(room, failedProduct, AuctionItemStatus.FAILED);
        entityManager.flush();

        assertThat(productRepository.findListingStatus(unregistered.getProductId()))
                .contains(ProductListingStatus.UNREGISTERED);
        assertThat(productRepository.findListingStatus(sold.getProductId()))
                .contains(ProductListingStatus.ENDED);
        // 유찰도 findListingStatus(상세·수정 응답)와 search(목록)가 같은 값을 줘야 한다.
        assertThat(productRepository.findListingStatus(failedProduct.getProductId()))
                .contains(ProductListingStatus.UNREGISTERED);
    }

    @Test
    @DisplayName("status 필터를 걸면 해당 상태의 상품만 조회된다")
    void search_filtersByStatus() {

        SellerProfile sellerProfile = newSellerProfile("seller8@hot6ix.com");
        AuctionRoom room = newAuctionRoom(sellerProfile);

        newProduct(sellerProfile, "미등록상품");
        Product ready = newProduct(sellerProfile, "대기상품");
        newAuctionItem(room, ready, AuctionItemStatus.READY);
        entityManager.flush();

        List<ProductSummaryResponseDto> results = productRepository.search(
                sellerProfile.getSellerProfileId(), null, ProductListingStatus.READY, null, null).getContent();

        assertThat(results).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(ready.getProductId());
    }

    @Test
    @DisplayName("keyword로 상품명을 검색한다")
    void search_filtersByKeyword() {

        SellerProfile sellerProfile = newSellerProfile("seller9@hot6ix.com");
        Product notebook = newProduct(sellerProfile, "승민의 노트북");
        newProduct(sellerProfile, "키보드");

        List<ProductSummaryResponseDto> results =
                productRepository.search(sellerProfile.getSellerProfileId(), "노트북", null, null, null).getContent();

        assertThat(results).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(notebook.getProductId());
    }

    @Test
    @DisplayName("productId 최신순으로 정렬되고, page를 넘기면 그 다음 구간이 나온다")
    void search_ordersByProductIdDescAcrossPages() {

        SellerProfile sellerProfile = newSellerProfile("seller10@hot6ix.com");
        Product first = newProduct(sellerProfile, "상품1");
        Product second = newProduct(sellerProfile, "상품2");
        Product third = newProduct(sellerProfile, "상품3");

        List<ProductSummaryResponseDto> all =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, null, null).getContent();

        assertThat(all).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(third.getProductId(), second.getProductId(), first.getProductId());

        // 한 페이지 2건이면 1페이지(0-based)에는 가장 오래된 한 건만 남는다.
        Page<ProductSummaryResponseDto> secondPage =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, 1, 2);

        assertThat(secondPage.getContent()).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(first.getProductId());
    }

    @Test
    @DisplayName("오래된 순으로 정렬하면 먼저 등록한 상품이 앞에 오고, 쪽을 넘겨도 순서가 이어진다")
    void search_ordersByProductIdAscWhenOldestFirst() {

        SellerProfile sellerProfile = newSellerProfile("seller22@hot6ix.com");
        Product first = newProduct(sellerProfile, "상품1");
        Product second = newProduct(sellerProfile, "상품2");
        Product third = newProduct(sellerProfile, "상품3");

        List<ProductSummaryResponseDto> all = productRepository.search(
                sellerProfile.getSellerProfileId(), null, null,
                ProductSortType.OLDEST, null, null).getContent();

        assertThat(all).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(first.getProductId(), second.getProductId(), third.getProductId());

        // 한 페이지 2건이면 1페이지(0-based)에는 가장 최근 한 건만 남는다.
        Page<ProductSummaryResponseDto> secondPage = productRepository.search(
                sellerProfile.getSellerProfileId(), null, null,
                ProductSortType.OLDEST, 1, 2);

        assertThat(secondPage.getContent()).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(third.getProductId());
    }

    @Test
    @DisplayName("정렬을 생략하면 최신순이다")
    void search_defaultsToLatestWhenSortOmitted() {

        SellerProfile sellerProfile = newSellerProfile("seller23@hot6ix.com");
        Product first = newProduct(sellerProfile, "상품1");
        Product second = newProduct(sellerProfile, "상품2");

        List<ProductSummaryResponseDto> results = productRepository.search(
                sellerProfile.getSellerProfileId(), null, null, null, null, null).getContent();

        assertThat(results).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(second.getProductId(), first.getProductId());
    }

    @Test
    @DisplayName("size만큼만 담고 전체 개수와 전체 페이지 수를 함께 준다")
    void search_reportsTotalsForRequestedSize() {

        SellerProfile sellerProfile = newSellerProfile("seller14@hot6ix.com");
        newProduct(sellerProfile, "상품A");
        newProduct(sellerProfile, "상품B");
        newProduct(sellerProfile, "상품C");

        Page<ProductSummaryResponseDto> page =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, null, 2);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("전체 페이지 수를 넘는 page는 빈 목록이지만 전체 개수는 그대로 준다")
    void search_pageBeyondRangeIsEmptyButKeepsTotals() {

        SellerProfile sellerProfile = newSellerProfile("seller20@hot6ix.com");
        newProduct(sellerProfile, "상품D");
        newProduct(sellerProfile, "상품E");

        Page<ProductSummaryResponseDto> page =
                productRepository.search(sellerProfile.getSellerProfileId(), null, null, 5, 2);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("전체 개수는 필터를 적용한 뒤의 개수다 — 목록과 어긋나면 페이지 수가 틀린다")
    void search_totalElementsRespectsFilter() {

        SellerProfile sellerProfile = newSellerProfile("seller21@hot6ix.com");
        AuctionRoom room = newAuctionRoom(sellerProfile);

        newProduct(sellerProfile, "미등록상품3");
        newProduct(sellerProfile, "미등록상품4");
        Product ready = newProduct(sellerProfile, "대기상품3");
        newAuctionItem(room, ready, AuctionItemStatus.READY);
        entityManager.flush();

        Page<ProductSummaryResponseDto> page = productRepository.search(
                sellerProfile.getSellerProfileId(), null, ProductListingStatus.READY, null, 1);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 판매자의 상품과 soft delete된 상품은 목록에 포함되지 않는다")
    void search_excludesOtherOwnersAndDeleted() {

        SellerProfile owner = newSellerProfile("seller11@hot6ix.com");
        SellerProfile other = newSellerProfile("seller12@hot6ix.com");

        Product visible = newProduct(owner, "노출상품");
        Product deleted = newProduct(owner, "삭제상품");
        deleted.softDelete(LocalDateTime.now());
        productRepository.flush();
        newProduct(other, "타인상품");

        List<ProductSummaryResponseDto> results =
                productRepository.search(owner.getSellerProfileId(), null, null, null, null).getContent();

        assertThat(results).extracting(ProductSummaryResponseDto::productId)
                .containsExactly(visible.getProductId());
    }
}
