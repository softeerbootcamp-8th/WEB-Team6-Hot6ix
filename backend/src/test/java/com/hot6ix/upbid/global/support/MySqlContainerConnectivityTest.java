package com.hot6ix.upbid.global.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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
