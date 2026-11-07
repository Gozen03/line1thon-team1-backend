package com.line1thon.jisiknamu.domain.quiz.entity;

import com.line1thon.jisiknamu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "answers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Answer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    private Integer selectedIndex;

    @Column(columnDefinition = "TEXT")
    private String essayText;

    // 서술형은 null
    @Column(name = "is_correct")
    private Boolean correct;

    @Column(columnDefinition = "TEXT")
    private String aiFeedback;

    @Builder
    private Answer(Question question, Integer selectedIndex, String essayText, Boolean correct, String aiFeedback) {
        this.question = question;
        this.selectedIndex = selectedIndex;
        this.essayText = essayText;
        this.correct = correct;
        this.aiFeedback = aiFeedback;
    }
}
