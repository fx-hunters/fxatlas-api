package com.divurve.domain.holding;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.holding.entity.Holding;
import com.divurve.domain.holding.entity.PurchaseFxRate;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보유 종목 CRUD 유스케이스 (이슈 #13, FR-XR-10, FR-ON-04).
 * 소유자 필터(NFR-SE-03)로 데이터를 격리하고, 매입 시점 환율 컨텍스트를 {@link PurchaseFxRateResolver} 로 채운다.
 * 진단(M1-7)이 사용할 현재가·원화 환산액은 여기서 계산하지 않는다.
 */
@UseCase
public class HoldingService {

    private final HoldingRepository holdingRepository;
    private final UserRepository userRepository;
    private final PurchaseFxRateResolver purchaseFxRateResolver;

    public HoldingService(
            HoldingRepository holdingRepository,
            UserRepository userRepository,
            PurchaseFxRateResolver purchaseFxRateResolver) {
        this.holdingRepository = holdingRepository;
        this.userRepository = userRepository;
        this.purchaseFxRateResolver = purchaseFxRateResolver;
    }

    /** 소유자의 보유 종목 목록. */
    @Transactional(readOnly = true)
    public List<Holding> list(UUID ownerId) {
        return holdingRepository.findByOwner_Id(ownerId);
    }

    /** 새 보유 종목 등록. 매입일이 있으면 매입 환율 컨텍스트를 함께 저장한다. */
    @Transactional
    public Holding create(
            UUID ownerId, String ticker, String currencyCode, double quantity, double avgPrice,
            LocalDate purchasedAt, BigDecimal purchaseFxRateFallbackKrw) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        Holding holding = Holding.create(owner, ticker, currencyCode, quantity, avgPrice);
        PurchaseFxRate fxRate =
                purchaseFxRateResolver.resolve(currencyCode, purchasedAt, purchaseFxRateFallbackKrw);
        holding.assignPurchaseContext(purchasedAt, fxRate);
        return holdingRepository.save(holding);
    }

    /** 수량·평균단가 수정. 매입 환율 근거는 유지된다. */
    @Transactional
    public Holding update(UUID ownerId, UUID holdingId, double quantity, double avgPrice) {
        Holding holding = loadOwned(ownerId, holdingId);
        holding.updateQuantities(quantity, avgPrice);
        return holding;
    }

    /** 소유자 소유의 종목만 삭제한다. */
    @Transactional
    public void delete(UUID ownerId, UUID holdingId) {
        Holding holding = loadOwned(ownerId, holdingId);
        holdingRepository.delete(holding);
    }

    private Holding loadOwned(UUID ownerId, UUID holdingId) {
        Holding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new NotFoundException("보유 종목을 찾을 수 없습니다."));
        if (!holding.getOwner().getId().equals(ownerId)) {
            // 소유자 격리 위반 — 존재 여부를 노출하지 않기 위해 동일한 404 로 응답한다(NFR-SE-03).
            throw new NotFoundException("보유 종목을 찾을 수 없습니다.");
        }
        return holding;
    }
}
