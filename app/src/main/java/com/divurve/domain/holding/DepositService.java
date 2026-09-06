package com.divurve.domain.holding;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.holding.entity.PurchaseFxRate;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외화 예금 CRUD 유스케이스 (이슈 #13, FR-XR-10, FR-ON-04).
 * 소유자 필터(NFR-SE-03)로 데이터를 격리하고, 예치 시점 환율 컨텍스트를 {@link PurchaseFxRateResolver} 로 채운다.
 * 이 이슈에서는 조회·추가만 노출한다(수정·삭제는 후속 이슈에서 열 예정).
 */
@UseCase
public class DepositService {

    private final DepositRepository depositRepository;
    private final UserRepository userRepository;
    private final PurchaseFxRateResolver purchaseFxRateResolver;

    public DepositService(
            DepositRepository depositRepository,
            UserRepository userRepository,
            PurchaseFxRateResolver purchaseFxRateResolver) {
        this.depositRepository = depositRepository;
        this.userRepository = userRepository;
        this.purchaseFxRateResolver = purchaseFxRateResolver;
    }

    /** 소유자의 외화 예금 목록. */
    @Transactional(readOnly = true)
    public List<Deposit> list(UUID ownerId) {
        return depositRepository.findByOwner_Id(ownerId);
    }

    /** 새 외화 예금 등록. 매입일이 있으면 예치 시점 환율 컨텍스트를 함께 저장한다. */
    @Transactional
    public Deposit create(
            UUID ownerId, String currencyCode, BigDecimal amount,
            LocalDate purchasedAt, BigDecimal purchaseFxRateFallbackKrw) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        Deposit deposit = Deposit.create(owner, currencyCode, amount);
        PurchaseFxRate fxRate =
                purchaseFxRateResolver.resolve(currencyCode, purchasedAt, purchaseFxRateFallbackKrw);
        deposit.assignPurchaseContext(purchasedAt, fxRate);
        return depositRepository.save(deposit);
    }
}
