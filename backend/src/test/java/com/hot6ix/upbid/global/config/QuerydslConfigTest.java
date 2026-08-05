package com.hot6ix.upbid.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.entity.QProduct;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * QueryDSL 설정이 실제로 붙었는지만 확인한다. 쿼리 전환은 다음 PR이라 검증 대상이 아니다.
 * 이 테스트가 잡는 것은 세 가지다 — Q타입 생성(컴파일 단계), JPAQueryFactory Bean 등록(주입),
 * QueryDSL이 만든 JPQL을 Hibernate가 파싱해 실제 DB로 보내는지(실행).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class QuerydslConfigTest extends AbstractMySqlContainerTest {

    @Autowired
    private JPAQueryFactory queryFactory;

    @DisplayName("Q타입과 JPAQueryFactory로 만든 쿼리가 실제로 실행된다")
    @Test
    void querydslSetup() {
        List<Product> found = queryFactory
                .selectFrom(QProduct.product)
                .where(QProduct.product.deletedAt.isNull())
                .fetch();

        assertThat(found).isEmpty();
    }
}
