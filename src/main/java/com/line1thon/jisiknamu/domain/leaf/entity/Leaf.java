package com.line1thon.jisiknamu.domain.leaf.entity;

import com.line1thon.jisiknamu.domain.study.entity.Study;
import com.line1thon.jisiknamu.domain.tree.entity.Tree;
import com.line1thon.jisiknamu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "leaves")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Leaf extends BaseTimeEntity {

    public static final int MAX_PER_TREE = 24;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tree_id", nullable = false)
    private Tree tree;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_id", nullable = false)
    private Study study;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeafGrade leafType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeafGrade originalType;

    @Column(nullable = false)
    private int firstCorrectCount;

    @Column(columnDefinition = "TEXT")
    private String feedbackSummary;

    private LocalDateTime reviewDueAt;

    private LocalDateTime recoveredAt;

    @Builder
    private Leaf(Tree tree, Study study, int position, LeafGrade grade, int firstCorrectCount,
                 String feedbackSummary, LocalDateTime reviewDueAt) {
        this.tree = tree;
        this.study = study;
        this.position = position;
        this.leafType = grade;
        this.originalType = grade;
        this.firstCorrectCount = firstCorrectCount;
        this.feedbackSummary = feedbackSummary;
        this.reviewDueAt = reviewDueAt;
    }

    /** 재도전 결과로 같은 잎의 등급을 바꾼다. 재도전 결과가 이 잎의 처음 등급이 된다. */
    public void applyRetry(LeafGrade grade, int correctCount, String feedbackSummary, LocalDateTime reviewDueAt) {
        this.leafType = grade;
        this.originalType = grade;
        this.firstCorrectCount = correctCount;
        this.feedbackSummary = feedbackSummary;
        this.reviewDueAt = reviewDueAt;
    }

    /** 복습 성공: 초록잎이 되고 총평을 다시 쓴다. 처음 등급은 그대로 둔다. */
    public void recover(String feedbackSummary, LocalDateTime recoveredAt) {
        this.leafType = LeafGrade.GREEN;
        this.feedbackSummary = feedbackSummary;
        this.recoveredAt = recoveredAt;
        this.reviewDueAt = null;
    }

    /** 복습 실패: 등급과 총평은 그대로 두고 다음 복습 시각만 바꾼다. */
    public void postponeReview(LocalDateTime reviewDueAt) {
        this.reviewDueAt = reviewDueAt;
    }

    public boolean isReviewAvailable(LocalDateTime now) {
        return LeafGrade.REVIEWABLE.contains(leafType) && reviewDueAt != null && !reviewDueAt.isAfter(now);
    }

    public boolean isReviewed() {
        return recoveredAt != null;
    }
}
