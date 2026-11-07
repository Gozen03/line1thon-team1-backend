package com.line1thon.jisiknamu.global.entity;

import com.line1thon.jisiknamu.global.util.KstTime;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * created_at 공통 컬럼. 값은 저장할 때 자동으로 들어간다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void setCreatedAt() {
        this.createdAt = KstTime.now();
    }
}
