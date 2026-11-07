package com.line1thon.jisiknamu.domain.quiz.repository;

import com.line1thon.jisiknamu.domain.quiz.entity.Answer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    @Query("select a from Answer a join fetch a.question q where q.quizSet.id = :quizSetId order by q.number")
    List<Answer> findAllWithQuestionByQuizSetId(@Param("quizSetId") Long quizSetId);
}
