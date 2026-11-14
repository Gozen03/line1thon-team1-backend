package com.line1thon.jisiknamu.domain.ad;

import static org.assertj.core.api.Assertions.assertThat;

import com.line1thon.jisiknamu.domain.ad.dto.AdViewRequest;
import com.line1thon.jisiknamu.domain.ad.dto.AdViewResponse;
import com.line1thon.jisiknamu.domain.ad.entity.AdPlacement;
import com.line1thon.jisiknamu.domain.ad.service.AdService;
import com.line1thon.jisiknamu.domain.user.entity.User;
import com.line1thon.jisiknamu.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 잎 배치와 기록 열람이 동시에 와도 전체 횟수가 빠지거나 겹치지 않는지 실제 MySQL로 확인한다 (트랜잭션 롤백 없이 실행 후 직접 지움)
@SpringBootTest
class AdConcurrencyTest {

    private static final int CALLS = 30;

    @Autowired
    private AdService adService;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @AfterEach
    void 회원을_지운다() {
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    void 동시에_불려도_전체_횟수가_1부터_차례로_매겨지고_광고는_3번에_1번() throws Exception {
        userId = userRepository.saveAndFlush(User.builder().nickname("광고동시").passwordHash("hash").build()).getId();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AdViewResponse>> futures = new ArrayList<>();
        for (int i = 0; i < CALLS; i++) {
            AdPlacement placement = i % 2 == 0 ? AdPlacement.LEAF_PLACEMENT : AdPlacement.RECORD_VIEW;
            futures.add(pool.submit(() -> {
                start.await();
                return adService.recordView(userId, new AdViewRequest(placement));
            }));
        }
        start.countDown();
        List<Integer> counts = new ArrayList<>();
        int ads = 0;
        for (Future<AdViewResponse> future : futures) {
            AdViewResponse response = future.get();
            counts.add(response.count());
            if (response.showAd()) {
                ads++;
            }
        }
        pool.shutdown();

        Collections.sort(counts);
        List<Integer> expected = new ArrayList<>();
        for (int i = 1; i <= CALLS; i++) {
            expected.add(i);
        }
        assertThat(counts).isEqualTo(expected);
        assertThat(ads).isEqualTo(CALLS / 3);
    }
}
