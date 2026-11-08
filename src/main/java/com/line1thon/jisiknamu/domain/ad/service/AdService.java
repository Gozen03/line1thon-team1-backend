package com.line1thon.jisiknamu.domain.ad.service;

import com.line1thon.jisiknamu.domain.ad.dto.AdViewRequest;
import com.line1thon.jisiknamu.domain.ad.dto.AdViewResponse;
import com.line1thon.jisiknamu.domain.ad.entity.AdPlacement;
import com.line1thon.jisiknamu.domain.ad.repository.AdCounterRepository;
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

    /**
     * 위치별 횟수를 1 올리고, 1번째와 3의 배수 번째에 광고를 띄운다.
     */
    @Transactional
    public AdViewResponse recordView(Long userId, AdViewRequest request) {
        AdPlacement placement = request.adPlacement();
        if (placement == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        adCounterRepository.increment(userId, placement.name());
        int count = adCounterRepository.findViewCount(userId, placement.name());
        // 광고 제거 결제는 추후 개발. 결제 회원이면 여기서 항상 false를 준다
        boolean showAd = count == 1 || count % AD_INTERVAL == 0;
        return new AdViewResponse(placement, count, showAd);
    }
}
