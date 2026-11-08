package com.line1thon.jisiknamu.domain.ad.repository;

import com.line1thon.jisiknamu.domain.ad.entity.AdCounter;
import com.line1thon.jisiknamu.domain.ad.entity.AdCounterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdCounterRepository extends JpaRepository<AdCounter, AdCounterId> {

    // 동시에 여러 번 불려도 횟수가 빠지지 않도록 DB에서 한 번에 더한다
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into ad_counters (user_id, ad_type, view_count) values (:userId, :adType, 1)
            on duplicate key update view_count = view_count + 1
            """)
    void increment(@Param("userId") Long userId, @Param("adType") String adType);

    @Query(nativeQuery = true, value = "select view_count from ad_counters where user_id = :userId and ad_type = :adType")
    int findViewCount(@Param("userId") Long userId, @Param("adType") String adType);
}
