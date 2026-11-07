package com.line1thon.jisiknamu.domain.ad.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ad_counters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdCounter {

    @EmbeddedId
    private AdCounterId id;

    @Column(nullable = false)
    private int viewCount;

    public AdCounter(Long userId, AdPlacement adType) {
        this.id = new AdCounterId(userId, adType);
        this.viewCount = 0;
    }
}
