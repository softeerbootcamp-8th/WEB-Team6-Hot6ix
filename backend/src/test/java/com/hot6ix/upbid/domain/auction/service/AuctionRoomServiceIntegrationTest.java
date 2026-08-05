package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.sse.service.RoomSseManager;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * share_code 충돌 시 재시도가 실제 DB·트랜잭션 경계에서도 동작하는지 확인하는 회귀 테스트다.
 * {@link AuctionRoomServiceTest}는 Mockito 목만 쓰는 단위 테스트라 진짜 Hibernate 세션이
 * 없고, 그래서 애초에 "첫 충돌 이후 재시도가 항상 깨지던" 버그를 잡지 못했다. 이 테스트는
 * {@link AuctionRoomService}를 실제 Testcontainers MySQL로 띄워서 그 회귀를 막는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, AuctionRoomService.class})
// AuctionRoomService.create()가 Propagation.NOT_SUPPORTED라, @DataJpaTest 기본 트랜잭션(롤백
// 전제) 안에서 셋업 데이터를 만들면 create() 내부의 각 저장이 그 미커밋 행을 못 봐서 락
// 대기로 걸린다. 셋업도 실제 커밋되게 테스트 자체의 트랜잭션을 꺼둔다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuctionRoomServiceIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired
    private AuctionRoomService auctionRoomService;

    @Autowired
    private AuctionRoomRepository auctionRoomRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private AuctionRoomShareService auctionRoomShareService;

    // @DataJpaTest 슬라이스에는 sse 도메인 빈이 안 올라온다. 목록 조회만 쓰는 의존이라 목으로 채운다.
    @MockitoBean
    private RoomSseManager roomSseManager;

    // 이벤트 발행 빈도 슬라이스에 없다. 이 테스트가 보는 건 share_code 재시도라
    // 발행 내용은 검증하지 않고, 컨텍스트만 뜨게 목으로 채운다.
    @MockitoBean
    private DomainEventPublisher domainEventPublisher;

    private SellerProfile newSellerProfile() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("regression-" + System.nanoTime() + "@hot6ix.com")
                .password("password")
                .nickname("regression")
                .phoneNumber("010-0000-0009")
                .build());

        return sellerProfileRepository.saveAndFlush(SellerProfile.builder()
                .user(user)
                .storeName("regression store")
                .build());
    }

    @Test
    @DisplayName("share_code가 충돌해도 재시도해서 실제로 생성에 성공한다")
    void create_retriesSuccessfullyOnRealShareCodeCollision() {

        SellerProfile sellerProfile = newSellerProfile();

        AuctionRoom existing = AuctionRoom.builder()
                .bidIncrement(1_000L)
                .sellerProfile(sellerProfile)
                .name("이미 있는 방")
                .shareCode("REGRESSIONDUPLICATE")
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();
        auctionRoomRepository.saveAndFlush(existing);

        when(auctionRoomShareService.generateCandidateShareCode())
                .thenReturn("REGRESSIONDUPLICATE")   // 첫 시도는 충돌
                .thenReturn("REGRESSIONUNIQUECODE");  // 두 번째 시도는 성공

        AuctionRoomCreateRequestDto request = AuctionRoomCreateRequestDto.builder()
                .bidIncrement(1_000L)
                .name("회귀 테스트 경매방")
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();

        AuctionRoomPublicResponseDto response =
                auctionRoomService.create(sellerProfile.getUser().getUserId(), request);

        assertThat(response.auctionRoomId()).isNotNull();
        assertThat(response.name()).isEqualTo("회귀 테스트 경매방");
        verify(auctionRoomShareService, times(2)).generateCandidateShareCode();
    }
}
