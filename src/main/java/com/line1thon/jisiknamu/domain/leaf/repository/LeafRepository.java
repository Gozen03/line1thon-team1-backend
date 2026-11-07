package com.line1thon.jisiknamu.domain.leaf.repository;

import com.line1thon.jisiknamu.domain.leaf.entity.Leaf;
import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeafRepository extends JpaRepository<Leaf, Long> {

    long countByTreeId(Long treeId);

    interface TreeLeafStats {
        Long getTreeId();

        long getLeafCount();

        long getReviewPendingCount();
    }

    // 복습 가능 조건은 Leaf.isReviewAvailable과 같다
    @Query("""
            select l.tree.id as treeId,
                   count(l) as leafCount,
                   sum(case when l.leafType in :reviewableGrades and l.reviewDueAt <= :now then 1 else 0 end) as reviewPendingCount
            from Leaf l
            where l.tree.user.id = :userId
            group by l.tree.id
            """)
    List<TreeLeafStats> findStatsByUserId(@Param("userId") Long userId,
                                          @Param("reviewableGrades") Collection<LeafGrade> reviewableGrades,
                                          @Param("now") LocalDateTime now);

    @Query("select l from Leaf l join fetch l.study where l.tree.id = :treeId order by l.position asc")
    List<Leaf> findAllWithStudyByTreeId(@Param("treeId") Long treeId);
}
