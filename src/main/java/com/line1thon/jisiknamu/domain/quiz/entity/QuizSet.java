package com.line1thon.jisiknamu.domain.quiz.entity;

import com.line1thon.jisiknamu.domain.leaf.entity.Leaf;
import com.line1thon.jisiknamu.domain.study.entity.Study;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "quiz_sets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_id", nullable = false)
    private Study study;

    // REVIEW 세트만 값이 있다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leaf_id")
    private Leaf leaf;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuizSetType type;

    @Column(nullable = false)
    private int attemptNo;

    private Integer correctCount;

    @Column(nullable = false)
    private int gradedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuizSetStatus status;

    private LocalDateTime submittedAt;

    @Builder
    private QuizSet(Study study, Leaf leaf, QuizSetType type, int attemptNo, int gradedCount) {
        this.study = study;
        this.leaf = leaf;
        this.type = type;
        this.attemptNo = attemptNo;
        this.gradedCount = gradedCount;
        this.status = QuizSetStatus.CREATED;
    }
}
