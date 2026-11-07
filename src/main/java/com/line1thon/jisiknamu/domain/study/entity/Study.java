package com.line1thon.jisiknamu.domain.study.entity;

import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "studies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Study extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tree_id", nullable = false)
    private Tree tree;

    // 재도전·복습 출제에 쓰도록 입력 때의 나무 값을 복사해 둔다
    @Column(nullable = false, length = 30)
    private String subjectName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StudyLevel studyLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InputType inputType;

    @Column(columnDefinition = "TEXT")
    private String textContent;

    @Column(length = 200)
    private String memo;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Builder
    private Study(Tree tree, InputType inputType, String textContent, String memo, String extractedText) {
        this.tree = tree;
        this.subjectName = tree.getSubjectName();
        this.studyLevel = tree.getStudyLevel();
        this.inputType = inputType;
        this.textContent = textContent;
        this.memo = memo;
        this.extractedText = extractedText;
    }
}
