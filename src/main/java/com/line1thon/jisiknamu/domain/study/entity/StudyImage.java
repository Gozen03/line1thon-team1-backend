package com.line1thon.jisiknamu.domain.study.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "study_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_id", nullable = false)
    private Study study;

    @Column(name = "s3_key", nullable = false, length = 300)
    private String s3Key;

    @Column(nullable = false)
    private int sortOrder;

    @Builder
    private StudyImage(Study study, String s3Key, int sortOrder) {
        this.study = study;
        this.s3Key = s3Key;
        this.sortOrder = sortOrder;
    }
}
