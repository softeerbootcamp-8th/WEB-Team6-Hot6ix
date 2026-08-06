package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemBulkAddRequestDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link AuctionItemService#add}·{@link AuctionItemService#addAll}이 {@code READ_COMMITTED}를
 * 선언하는 이유를 못 박는다. {@code auction_items.product_id}의 unique 제약을 없앤 뒤로는
 * 상품 행 락({@code ProductRepository.findOwnedForUpdate})이 동시 등록을 막는 유일한
 * 방어선인데, <b>락만으로는 부족하다.</b>
 *
 * <p>기본 격리 수준인 REPEATABLE READ에서는 트랜잭션의 첫 일반 조회(여기서는 판매자 프로필
 * 조회) 시점에 읽기 뷰가 고정된다. 그러면 상품 행 락을 <b>기다리는 동안</b> 다른 요청이
 * 커밋한 물품이 뒤따르는 {@code findBlockedProductIdsIn} 조회에 잡히지 않아, 락이 요청을
 * 줄 세워도 낡은 값(차단 물품 0건)으로 통과시킨다. {@link AuctionItemStartIsolationTest}가
 * {@code start()}에서 겪은 것과 같은 함정이다.
 *
 * <p>테스트 자체의 트랜잭션을 꺼두는 이유는, 셋업 데이터가 실제로 커밋돼야 별도 트랜잭션에서
 * 보이기 때문이다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuctionItemAddIsolationTest extends AbstractMySqlContainerTest {

    @Autowired
    private AuctionItemRepository auctionItemRepository;

    @Autowired
    private AuctionRoomRepository auctionRoomRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 아래 두 테스트는 격리 수준에 따른 <b>동작 차이</b>를 보여줄 뿐, 서비스가 실제로 그 수준을
     * 선언했는지는 확인하지 못한다(각자 자기 {@code TransactionTemplate}을 쓴다). 선언이 사라지면
     * 서비스만 조용히 깨지므로 여기서 함께 못 박는다.
     */
    @Test
    @DisplayName("add()·addAll()은 READ_COMMITTED를 선언한다")
    void addAndAddAllDeclareReadCommitted() throws NoSuchMethodException {

        Transactional addTx = AuctionItemService.class
                .getMethod("add", Long.class, Long.class, AuctionItemAddRequestDto.class)
                .getAnnotation(Transactional.class);
        Transactional addAllTx = AuctionItemService.class
                .getMethod("addAll", Long.class, Long.class, AuctionItemBulkAddRequestDto.class)
                .getAnnotation(Transactional.class);

        assertThat(addTx).isNotNull();
        assertThat(addTx.isolation())
                .as("떼면 unique 제약이 없는 상태에서 동시 등록이 조용히 뚫린다 — 아래 두 테스트가 그 이유다")
                .isEqualTo(Isolation.READ_COMMITTED);

        assertThat(addAllTx).isNotNull();
        assertThat(addAllTx.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    @Test
    @DisplayName("REPEATABLE READ에서는 상품 행 락을 기다리는 동안 커밋된 물품이 차단 판정에 잡히지 않는다")
    void repeatableReadMissesConcurrentlyAddedItem() {

        assertThat(blockedCountAfterConcurrentAdd(TransactionDefinition.ISOLATION_REPEATABLE_READ))
                .as("읽기 뷰가 먼저 고정돼 낡은 0을 센다 — 이 값으로 차단 여부를 판단하면 중복 등록이 뚫린다")
                .isZero();
    }

    @Test
    @DisplayName("READ COMMITTED에서는 상품 행 락을 기다리는 동안 커밋된 물품도 차단 판정에 잡힌다")
    void readCommittedSeesConcurrentlyAddedItem() {

        assertThat(blockedCountAfterConcurrentAdd(TransactionDefinition.ISOLATION_READ_COMMITTED))
                .as("add()·addAll()이 이 격리 수준을 선언하는 이유다")
                .isEqualTo(1);
    }

    /**
     * {@code add()}의 문장 순서를 그대로 재현한다. 판매자 프로필 조회(일반 SELECT)로 읽기 뷰가
     * 만들어진 뒤 다른 트랜잭션이 같은 상품에 물품을 커밋하고, 그다음 차단 판정을 돌린다. 상품
     * 행 락을 기다리는 구간을 "다른 트랜잭션이 커밋을 끝낼 때까지 기다린다"로 치환한 것이라
     * 타이밍에 의존하지 않는다.
     *
     * @return 차단 판정이 돌려준 물품 수
     */
    private int blockedCountAfterConcurrentAdd(int isolationLevel) {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newAuctionRoom(sellerProfile);
        Product product = productRepository.saveAndFlush(Product.builder()
                .sellerProfile(sellerProfile)
                .name("동시등록물품")
                .description("미개봉 정품")
                .build());

        TransactionTemplate isolated = new TransactionTemplate(transactionTemplate.getTransactionManager());
        isolated.setIsolationLevel(isolationLevel);

        return isolated.execute(status -> {
            sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(
                    sellerProfile.getUser().getUserId());

            addItemInAnotherTransaction(auctionRoom, product);

            return auctionItemRepository.findBlockedProductIdsIn(List.of(product.getProductId())).size();
        });
    }

    /** 다른 판매자 요청이 같은 상품을 먼저 READY 물품으로 올린 상황을 만든다. 커밋까지 끝내고 돌아온다. */
    private void addItemInAnotherTransaction(AuctionRoom auctionRoom, Product product) {
        CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(status ->
                auctionItemRepository.saveAndFlush(AuctionItem.builder()
                        .auctionRoom(auctionRoom)
                        .product(product)
                        .startingPrice(10_000L)
                        .bidIncrement(1_000L)
                        .status(AuctionItemStatus.READY)
                        .build())
        )).join();
    }

    private SellerProfile newSellerProfile() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("add-isolation" + System.nanoTime() + "@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build());

        return sellerProfileRepository.saveAndFlush(SellerProfile.builder()
                .user(user)
                .storeName("승민상점")
                .build());
    }

    private AuctionRoom newAuctionRoom(SellerProfile sellerProfile) {
        return auctionRoomRepository.saveAndFlush(AuctionRoom.builder()
                .sellerProfile(sellerProfile)
                .bidIncrement(1_000L)
                .name("승민의 경매방")
                .build());
    }
}
