package com.line1thon.jisiknamu.domain.ad.service;

import com.line1thon.jisiknamu.domain.ad.dto.AdViewRequest;
import com.line1thon.jisiknamu.domain.ad.dto.AdViewResponse;
import com.line1thon.jisiknamu.domain.ad.entity.AdPlacement;
import com.line1thon.jisiknamu.domain.ad.repository.AdCounterRepository;
import com.line1thon.jisiknamu.domain.user.repository.UserRepository;
import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdService {

    private static final int AD_INTERVAL = 3;

    private final AdCounterRepository adCounterRepository;
    private final UserRepository userRepository;

    /**
     * 위치별 횟수를 1 올리고, 모든 위치를 합친 회원의 전체 횟수가 1·4·7·10…번째일 때 광고를 띄운다.
     * 잎 배치와 기록 열람이 동시에 와도 같은 합계를 보지 않도록 회원 행을 잠가 한 명의 요청을 차례로 처리한다.
     */
    @Transactional
    public AdViewResponse recordView(Long userId, AdViewRequest request) {
        AdPlacement placement = request.adPlacement();
        if (placement == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        adCounterRepository.increment(userId, placement.name());
        int total = adCounterRepository.sumViewCount(userId);
        // 광고 제거 결제는 추후 개발. 결제 회원이면 여기서 항상 false를 준다
        boolean showAd = total % AD_INTERVAL == 1;
        return new AdViewResponse(placement, total, showAd);
    }
}
