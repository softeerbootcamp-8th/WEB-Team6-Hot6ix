package com.hot6ix.upbid.global.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * {@code @DataJpaTest}는 슬라이스 테스트인데도 앱 전체의 Spring Data 리포지토리 빈을 전부
 * 만들려고 시도한다. {@code ProductRepository}·{@code AuctionItemRepository}가 QueryDSL
 * Custom Repository(각각 {@code ProductRepositoryImpl}·{@code AuctionItemRepositoryImpl})로
 * 바뀌면서 생성자에 {@code JPAQueryFactory}가 필요해졌고, 이 클래스가 그 빈을 등록하는
 * {@link JpaConfig}를 가져오지 않으면 그 리포지토리들을 만들다가 컨텍스트 로딩 자체가
 * 실패한다. 이 테스트가 실제로 Product·AuctionItem을 쓰지 않아도 마찬가지다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class MySqlContainerConnectivityTest extends AbstractMySqlContainerTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Testcontainers로 띄운 MySQL 컨테이너에 실제로 연결되어 엔티티를 저장·조회할 수 있다")
    void connectsToContainerizedMySql() {
        User user = User.builder()
                .email("smoke@hot6ix.com")
                .password("password")
                .nickname("스모크")
                .phoneNumber("010-0000-0000")
                .build();

        User found = entityManager.persistFlushFind(user);

        assertThat(found.getUserId()).isNotNull();
    }
}
