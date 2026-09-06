package com.divurve.domain.master;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.NotFoundException;
import java.util.List;

/**
 * 마스터 데이터 조회 유스케이스 (이슈 #11). 지원 통화 표시 규칙과 은행별 환전 조건을 마스터 표에서 읽어 제공한다.
 * 이 값들은 M2 플래너 비용계산(FR-RT-11)의 선행 입력이 되며, 여기서는 조회만 한다 — 계산 로직은 없다.
 */
@UseCase
public class MasterDataService {

    /** 지원 통화 표시 규칙 목록을 반환한다. */
    public List<CurrencyMaster.Currency> listCurrencies() {
        return CurrencyMaster.all();
    }

    /**
     * 은행의 통화·채널별 환전 조건을 반환한다.
     *
     * @throws NotFoundException 마스터에 등록되지 않은 은행 코드인 경우
     */
    public FxTerms getFxTerms(String bankCode) {
        if (!BankFxTermsMaster.contains(bankCode)) {
            throw new NotFoundException("환전 조건을 제공하지 않는 은행 코드입니다: " + bankCode);
        }
        return new FxTerms(bankCode, BankFxTermsMaster.termsOf(bankCode));
    }

    /**
     * 은행별 환전 조건 조회 결과.
     *
     * @param bankCode 금융기관 표준코드
     * @param terms    통화·채널별 조건
     */
    public record FxTerms(String bankCode, List<BankFxTermsMaster.Term> terms) {
    }
}
