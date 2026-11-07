package com.line1thon.jisiknamu.domain.tree.repository;

import com.line1thon.jisiknamu.domain.tree.entity.Tree;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TreeRepository extends JpaRepository<Tree, Long> {

    List<Tree> findAllByUserIdOrderByIdAsc(Long userId);

    long countByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tree t where t.id = :id")
    Optional<Tree> findByIdForUpdate(@Param("id") Long id);
}
