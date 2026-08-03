package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemStartRequestDto;
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
 * {@link AuctionItemService#start} 가 {@code READ_COMMITTED}를 선언하는 이유를 못 박는다.
 *
 * <p>"방당 동시 3개" 검사는 경매방 행 락과 개수 세기로 이뤄지는데, <b>락만으로는 부족하다.</b>
 * 기본 격리 수준인 REPEATABLE READ에서는 트랜잭션의 첫 일반 조회 시점에 읽기 뷰가 고정되므로,
 * 경매방 락을 <b>기다리는 동안</b> 다른 요청이 커밋한 진행중 물품이 개수에 잡히지 않는다.
 * 그러면 락이 요청을 줄 세워도 낡은 값으로 통과시켜 4개가 될 수 있다.
 *
 * <p>아래 두 단정이 그 차이를 실제 MySQL에서 보여준다. 단위 테스트로는 재현할 수 없는 결함이라
 * 여기에 남긴다. {@code start()}에서 격리 수준을 떼면 서비스는 이 테스트가 REPEATABLE READ로
 * 보여주는 쪽 동작을 하게 된다.
 *
 * <p>테스트 자체의 트랜잭션을 꺼두는 이유는, 셋업 데이터가 실제로 커밋돼야 별도 트랜잭션에서
 * 보이기 때문이다({@link AuctionRoomServiceIntegrationTest}와 같은 이유).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuctionItemStartIsolationTest extends AbstractMySqlContainerTest {

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
    @DisplayName("start()는 READ_COMMITTED를 선언한다")
    void startDeclaresReadCommitted() throws NoSuchMethodException {

        Transactional transactional = AuctionItemService.class
                .getMethod("start", Long.class, Long.class, AuctionItemStartRequestDto.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.isolation())
                .as("떼면 방당 동시 3개 제한이 조용히 뚫린다 — 아래 두 테스트가 그 이유다")
                .isEqualTo(Isolation.READ_COMMITTED);
    }

    @Test
    @DisplayName("REPEATABLE READ에서는 일반 조회 뒤에 커밋된 진행중 물품이 개수에 잡히지 않는다")
    void repeatableReadMissesConcurrentlyStartedItem() {

        assertThat(countAfterConcurrentStart(TransactionDefinition.ISOLATION_REPEATABLE_READ))
                .as("읽기 뷰가 먼저 고정돼 낡은 0을 센다 — 이 값으로 3개 제한을 판단하면 뚫린다")
                .isZero();
    }

    @Test
    @DisplayName("READ COMMITTED에서는 일반 조회 뒤에 커밋된 진행중 물품도 개수에 잡힌다")
    void readCommittedSeesConcurrentlyStartedItem() {

        assertThat(countAfterConcurrentStart(TransactionDefinition.ISOLATION_READ_COMMITTED))
                .as("start()가 이 격리 수준을 선언하는 이유다")
                .isEqualTo(1);
    }

    /**
     * {@code start()}의 문장 순서를 그대로 재현한다. 판매자 프로필 조회(일반 SELECT)로 읽기 뷰가
     * 만들어진 뒤 다른 트랜잭션이 진행중 물품을 커밋하고, 그다음 개수를 센다. 경매방 락을 기다리는
     * 구간을 "다른 트랜잭션이 커밋을 끝낼 때까지 기다린다"로 치환한 것이라 타이밍에 의존하지 않는다.
     *
     * @return 개수 세기가 돌려준 값
     */
    private long countAfterConcurrentStart(int isolationLevel) {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newAuctionRoom(sellerProfile);

        TransactionTemplate isolated = new TransactionTemplate(transactionTemplate.getTransactionManager());
        isolated.setIsolationLevel(isolationLevel);

        return isolated.execute(status -> {
            sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(
                    sellerProfile.getUser().getUserId());

            startItemInAnotherTransaction(sellerProfile, auctionRoom);

            return auctionItemRepository.countByAuctionRoom_AuctionRoomIdAndStatus(
                    auctionRoom.getAuctionRoomId(), AuctionItemStatus.IN_PROGRESS);
        });
    }

    /** 다른 판매자가 같은 방의 물품을 먼저 시작시킨 상황을 만든다. 커밋까지 끝내고 돌아온다. */
    private void startItemInAnotherTransaction(SellerProfile sellerProfile, AuctionRoom auctionRoom) {
        CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(status -> {
            Product product = productRepository.saveAndFlush(Product.builder()
                    .sellerProfile(sellerProfile)
                    .name("먼저 시작된 물품")
                    .description("미개봉 정품")
                    .build());

            auctionItemRepository.saveAndFlush(AuctionItem.builder()
                    .auctionRoom(auctionRoom)
                    .product(product)
                    .startingPrice(10_000L)
                    .bidIncrement(1_000L)
                    .status(AuctionItemStatus.IN_PROGRESS)
                    .build());
        })).join();
    }

    private SellerProfile newSellerProfile() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("isolation" + System.nanoTime() + "@hot6ix.com")
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
