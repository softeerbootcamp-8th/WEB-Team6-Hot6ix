package com.hot6ix.upbid.domain.bid.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.bid.entity.Bid;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.AutoConfigureTestEntityManager;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureTestEntityManager
@Import(JpaConfig.class)
class BidRepositoryTest extends AbstractMySqlContainerTest {

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private TestEntityManager entityManager;

    private SellerProfile sellerProfile;
    private AuctionItem auctionItem;

    /**
     * 입찰 하나를 넣으려면 User → SellerProfile → Product·AuctionRoom → AuctionItem이
     * 먼저 있어야 한다. Repository가 없는 것들은 TestEntityManager로 직접 저장한다.
     */
    @BeforeEach
    void setUp() {
        User seller = newUser("seller@hot6ix.com", "승민");

        sellerProfile = entityManager.persist(SellerProfile.builder()
                .user(seller)
                .storeName("승민상점")
                .build());

        auctionItem = newAuctionItem("포토카드");
    }

    private User newUser(String email, String nickname) {
        return entityManager.persist(User.builder()
                .email(email)
                .password("password")
                .nickname(nickname)
                .phoneNumber("010-1234-5678")
                .build());
    }

    private AuctionItem newAuctionItem(String productName) {
        AuctionRoom auctionRoom = entityManager.persist(AuctionRoom.builder()
                .sellerProfile(sellerProfile)
                .name("승민상점 경매방")
                .build());

        Product product = entityManager.persist(Product.builder()
                .sellerProfile(sellerProfile)
                .name(productName)
                .description("미개봉 정품")
                .build());

        return entityManager.persist(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(product)
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.SOLD)
                .endAt(LocalDateTime.of(2026, 7, 29, 21, 0))
                .build());
    }

    @Test
    @DisplayName("같은 물품에 같은 금액으로 두 번 저장하면 unique 제약에 걸린다")
    void rejectsSameAmountOnSameItem() {

        User first = newUser("first@hot6ix.com", "먼저");
        User second = newUser("second@hot6ix.com", "나중");

        bidRepository.saveAndFlush(Bid.builder()
                .auctionItem(auctionItem).bidder(first).amount(12_000L).build());

        assertThatThrownBy(() -> bidRepository.saveAndFlush(Bid.builder()
                .auctionItem(auctionItem).bidder(second).amount(12_000L).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("물품이 다르면 같은 금액이어도 저장된다")
    void allowsSameAmountOnDifferentItems() {

        User bidder = newUser("bidder@hot6ix.com", "입찰자");

        bidRepository.saveAndFlush(Bid.builder()
                .auctionItem(auctionItem).bidder(bidder).amount(12_000L).build());
        Bid saved = bidRepository.saveAndFlush(Bid.builder()
                .auctionItem(newAuctionItem("다른물품")).bidder(bidder).amount(12_000L).build());

        assertThat(saved.getBidId()).isNotNull();
    }

    /** 순위 산정 뒤에 걸러내면 상한을 못 채운다. 남은 사람들로 채워지는지 확인한다. */

}
