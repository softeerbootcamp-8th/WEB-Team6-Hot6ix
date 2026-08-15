package com.hot6ix.upbid.global.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mysql.MySQLContainer;

public abstract class AbstractMySqlContainerTest {

    /**
     * 커넥션 상한을 올려 둔다. 테스트는 Spring 컨텍스트를 여러 개 띄우고 그때마다 Hikari 풀이
     * 하나씩 생기는데, MySQL 기본 상한이 151이라 {@code DB_POOL_SIZE}를 20으로 올리자
     * 컨텍스트 9개 × 20 = 180으로 넘겨 {@code Too many connections}로 부팅이 실패했다.
     *
     * <p>앱 설정을 낮추지 않고 여기를 올리는 이유는, 컨텍스트가 뜨는지 보는 테스트가
     * <b>운영과 같은 풀 크기로</b> 돌아야 의미가 있기 때문이다. 운영은 앱 한 대에 컨텍스트가
     * 하나라 이 곱하기가 일어나지 않는다.
     */
    @ServiceConnection
    static final MySQLContainer MYSQL_CONTAINER = new MySQLContainer("mysql:8.4.9")
            .withCommand("--max-connections=500");

    static {
        MYSQL_CONTAINER.start();
    }
}
