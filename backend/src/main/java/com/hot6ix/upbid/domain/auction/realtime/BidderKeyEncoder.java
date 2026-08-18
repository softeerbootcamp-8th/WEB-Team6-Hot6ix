package com.hot6ix.upbid.domain.auction.realtime;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** 회원 ID 대신 방 안에서만 안정적으로 쓰는 익명 입찰자 식별값을 만든다. */
@Component
@EnableConfigurationProperties(BidderKeyProperties.class)
public class BidderKeyEncoder {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_BYTES = 16;

    private final SecretKeySpec secretKey;

    public BidderKeyEncoder(BidderKeyProperties properties) {
        String secret = properties.bidderKeySecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("upbid.auction.live-state.bidder-key-secret은 비어 있을 수 없다");
        }
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String encode(long roomId, long userId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secretKey);
            byte[] digest = mac.doFinal((roomId + ":" + userId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Arrays.copyOf(digest, KEY_BYTES));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("bidderKey HMAC을 만들 수 없다", e);
        }
    }
}
