package com.divurve.domain.holding;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.holding.entity.KrwAsset;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원화 자산 CRUD 유스케이스 (API 명세 v2 §0.2 {@code /krw-assets}, FR-XR-01 · FR-XR-07).
 *
 * <p>원화 자산은 <b>외화 비중의 분모</b>다. v1 에는 이 입력 경로가 없어서 {@code total_asset_krw} 가
 * 외화자산과 같아지고 {@code fx_ratio} 가 항상 1.0 이었다.
 *
 * <p>소유자 필터(NFR-SE-03)로 데이터를 격리하며, 남의 자산 접근은 존재 여부를 숨기려 404 로 답한다.
 */
@UseCase
public class KrwAssetService {

    /** ERD ENUM {@code krw_asset_kind} 의 허용값. */
    public static final Set<String> ALLOWED_KINDS =
            Set.of("cash", "deposit", "domestic_equity", "other");

    private static final String FIELD_KIND = "kind";
    private static final String FIELD_AMOUNT_KRW = "amount_krw";

    private final KrwAssetRepository krwAssetRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public KrwAssetService(
            KrwAssetRepository krwAssetRepository,
            UserRepository userRepository,
            Clock clock) {
        this.krwAssetRepository = krwAssetRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** 소유자의 원화 자산 목록. 없으면 빈 목록이다(FR-CM-09). */
    @Transactional(readOnly = true)
    public List<KrwAsset> list(UUID ownerId) {
        return krwAssetRepository.findByOwner_Id(ownerId);
    }

    /** 소유자의 원화 자산 총액. 외화 비중의 분모로 쓰인다. */
    @Transactional(readOnly = true)
    public long totalKrw(UUID ownerId) {
        return krwAssetRepository.findByOwner_Id(ownerId).stream()
                .mapToLong(KrwAsset::getAmountKrw)
                .sum();
    }

    /** 새 원화 자산 등록. */
    @Transactional
    public KrwAsset create(UUID ownerId, String kind, String label, long amountKrw) {
        validate(kind, amountKrw);
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        return krwAssetRepository.save(
                KrwAsset.create(owner, kind, label, amountKrw, clock.instant()));
    }

    /** 종류·이름표·금액 수정. */
    @Transactional
    public KrwAsset update(UUID ownerId, UUID krwAssetId, String kind, String label, long amountKrw) {
        validate(kind, amountKrw);
        KrwAsset asset = loadOwned(ownerId, krwAssetId);
        asset.update(kind, label, amountKrw, clock.instant());
        return asset;
    }

    /** 소유자 소유의 원화 자산만 삭제한다. */
    @Transactional
    public void delete(UUID ownerId, UUID krwAssetId) {
        krwAssetRepository.delete(loadOwned(ownerId, krwAssetId));
    }

    private void validate(String kind, long amountKrw) {
        if (kind == null || !ALLOWED_KINDS.contains(kind)) {
            throw new InvalidRequestException(
                    "원화 자산 종류는 cash·deposit·domestic_equity·other 중 하나여야 합니다.", FIELD_KIND);
        }
        if (amountKrw < 0L) {
            throw new InvalidRequestException("원화 자산 금액은 0보다 작을 수 없습니다.", FIELD_AMOUNT_KRW);
        }
    }

    private KrwAsset loadOwned(UUID ownerId, UUID krwAssetId) {
        KrwAsset asset = krwAssetRepository.findById(krwAssetId)
                .orElseThrow(() -> new NotFoundException("원화 자산을 찾을 수 없습니다."));
        if (!asset.getOwner().getId().equals(ownerId)) {
            // 소유자 격리 위반 — 존재 여부를 노출하지 않기 위해 동일한 404 로 응답한다(NFR-SE-03).
            throw new NotFoundException("원화 자산을 찾을 수 없습니다.");
        }
        return asset;
    }
}
