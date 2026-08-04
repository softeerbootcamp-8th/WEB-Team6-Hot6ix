package com.hot6ix.upbid.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void softDelete(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    /**
     * soft delete를 되돌린다. 삭제된 행을 지우고 새로 넣는 대신 되살리는 흐름에서 쓴다
     * — 그래야 이 행을 가리키던 다른 테이블의 FK가 그대로 유효하다.
     */
    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
