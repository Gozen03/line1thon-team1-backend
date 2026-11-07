package com.line1thon.jisiknamu.domain.tree.repository;

import com.line1thon.jisiknamu.domain.tree.entity.Tree;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreeRepository extends JpaRepository<Tree, Long> {

    List<Tree> findAllByUserIdOrderByIdAsc(Long userId);

    long countByUserId(Long userId);
}
