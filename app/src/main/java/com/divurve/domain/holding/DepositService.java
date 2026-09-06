package com.divurve.domain.holding;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.InvalidRequestException;
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
 * 수정·삭제는 API 명세 v2 §0.2 가 추가한 {@code PUT/DELETE /deposits/:id}(FR-XR-07)를 받는다.
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

    /** 예금 잔액 수정. 예치 시점 환율 근거는 유지된다 (FR-XR-07). */
    @Transactional
    public Deposit update(UUID ownerId, UUID depositId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("예금 잔액은 0보다 작을 수 없습니다.", "amount");
        }
        Deposit deposit = loadOwned(ownerId, depositId);
        deposit.updateAmount(amount);
        return deposit;
    }

    /** 소유자 소유의 예금만 삭제한다 (FR-XR-07). */
    @Transactional
    public void delete(UUID ownerId, UUID depositId) {
        depositRepository.delete(loadOwned(ownerId, depositId));
    }

    private Deposit loadOwned(UUID ownerId, UUID depositId) {
        Deposit deposit = depositRepository.findById(depositId)
                .orElseThrow(() -> new NotFoundException("외화 예금을 찾을 수 없습니다."));
        if (!deposit.getOwner().getId().equals(ownerId)) {
            // 소유자 격리 위반 — 존재 여부를 노출하지 않기 위해 동일한 404 로 응답한다(NFR-SE-03).
            throw new NotFoundException("외화 예금을 찾을 수 없습니다.");
        }
        return deposit;
    }
}
