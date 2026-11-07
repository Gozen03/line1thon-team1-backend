package com.line1thon.jisiknamu.domain.study.repository;

import com.line1thon.jisiknamu.domain.study.entity.StudyImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyImageRepository extends JpaRepository<StudyImage, Long> {

    @Query("select i.s3Key from StudyImage i where i.study.id = :studyId")
    List<String> findS3KeysByStudyId(@Param("studyId") Long studyId);

    @Query("select i.s3Key from StudyImage i where i.study.tree.id = :treeId")
    List<String> findS3KeysByTreeId(@Param("treeId") Long treeId);

    @Query("select i.s3Key from StudyImage i where i.study.tree.user.id = :userId")
    List<String> findS3KeysByUserId(@Param("userId") Long userId);
}
