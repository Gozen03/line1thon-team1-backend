package com.line1thon.jisiknamu.domain.quiz.repository;

import com.line1thon.jisiknamu.domain.quiz.entity.QuizSet;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetStatus;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {

    Optional<QuizSet> findFirstByStudyTreeIdAndStatusAndTypeInOrderByIdDesc(
            Long treeId, QuizSetStatus status, Collection<QuizSetType> types);

    List<QuizSet> findAllByStudyTreeIdAndStatusAndTypeIn(Long treeId, QuizSetStatus status, Collection<QuizSetType> types);

    @Query("select q from QuizSet q join fetch q.study s join fetch s.tree where q.id = :id")
    Optional<QuizSet> findWithStudyAndTreeById(@Param("id") Long id);
}
