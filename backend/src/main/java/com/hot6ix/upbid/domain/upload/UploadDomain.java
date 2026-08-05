package com.hot6ix.upbid.domain.upload;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 업로드한 파일이 들어갈 S3 키의 앞부분.
 *
 * <p>클라이언트는 이 enum 이름만 고를 수 있고 경로 문자열은 못 보낸다. 경로를 클라이언트가
 * 정하면 다른 사용자의 키를 적어 넣어 남의 파일을 덮어쓸 수 있다.
 *
 * <p>여기 적힌 두 prefix에만 IAM 정책이 {@code s3:PutObject}를 허용한다. 값을 늘리려면
 * 버킷 정책과 IAM 정책도 같이 늘려야 한다.
 */
@Getter
@RequiredArgsConstructor
public enum UploadDomain {

    SELLER_PROFILE("seller-profiles"),
    PRODUCT("products"),
    USER_PROFILE("user-profiles");

    private final String prefix;
}
