package com.line1thon.jisiknamu.domain.leaf.repository;

import com.line1thon.jisiknamu.domain.leaf.entity.LeafTopic;
import com.line1thon.jisiknamu.domain.leaf.entity.TopicStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeafTopicRepository extends JpaRepository<LeafTopic, Long> {

    void deleteAllByLeafId(Long leafId);

    @Query("select t.topic from LeafTopic t where t.leaf.id = :leafId and t.status = :status order by t.id")
    List<String> findTopics(@Param("leafId") Long leafId, @Param("status") TopicStatus status);
}
