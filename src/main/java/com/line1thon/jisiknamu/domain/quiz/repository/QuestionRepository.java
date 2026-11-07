package com.line1thon.jisiknamu.domain.quiz.repository;

import com.line1thon.jisiknamu.domain.quiz.entity.Question;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findAllByQuizSetIdOrderByNumberAsc(Long quizSetId);

    @Query("""
            select q.content from Question q
            where q.quizSet.study.id = :studyId and q.quizSet.type in :types
            order by q.id
            """)
    List<String> findContents(@Param("studyId") Long studyId, @Param("types") Collection<QuizSetType> types);
}
