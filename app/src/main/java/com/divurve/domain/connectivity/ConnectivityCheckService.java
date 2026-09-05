package com.divurve.domain.connectivity;

import com.divurve.common.architecture.UseCase;
import com.divurve.domain.connectivity.entity.ConnectivityCheck;
import java.util.List;

/**
 * 프론트·DB 연동 확인용 유스케이스. 테스트 행을 저장하고 전체를 조회한다.
 * 계산 로직이 없는 순수 CRUD 슬라이스이므로 engine 모듈과 무관하다.
 */
@UseCase
public class ConnectivityCheckService {

    private final ConnectivityCheckRepository repository;

    public ConnectivityCheckService(ConnectivityCheckRepository repository) {
        this.repository = repository;
    }

    /** 새 테스트 행을 저장하고, DB 가 채운 id/created_at 이 실린 엔티티를 반환한다. */
    public ConnectivityCheck create(String message) {
        return repository.save(ConnectivityCheck.create(message));
    }

    /** 저장된 모든 테스트 행을 조회한다. */
    public List<ConnectivityCheck> findAll() {
        return repository.findAll();
    }
}
