package com.line1thon.jisiknamu.domain.study.repository;

import com.line1thon.jisiknamu.domain.study.entity.Study;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyRepository extends JpaRepository<Study, Long> {

    interface TreeLastStudied {
        Long getTreeId();

        LocalDateTime getLastStudiedAt();
    }

    @Query("""
            select s.tree.id as treeId, max(s.createdAt) as lastStudiedAt
            from Study s
            where s.tree.user.id = :userId
            group by s.tree.id
            """)
    List<TreeLastStudied> findLastStudiedByUserId(@Param("userId") Long userId);
}
