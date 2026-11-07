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

    public boolean isReviewAvailable(LocalDateTime now) {
        return LeafGrade.REVIEWABLE.contains(leafType) && reviewDueAt != null && !reviewDueAt.isAfter(now);
    }

    public boolean isReviewed() {
        return recoveredAt != null;
    }
}
