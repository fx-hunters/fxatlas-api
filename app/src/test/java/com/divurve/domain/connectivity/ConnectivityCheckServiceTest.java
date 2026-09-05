package com.divurve.domain.connectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.domain.connectivity.entity.ConnectivityCheck;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ConnectivityCheckServiceTest {

    private ConnectivityCheckRepository repository;
    private ConnectivityCheckService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ConnectivityCheckRepository.class);
        service = new ConnectivityCheckService(repository);
    }

    @Test
    void create_는_message_로_행을_저장하고_저장된_엔티티를_반환한다() {
        ConnectivityCheck saved = ConnectivityCheck.create("ping");
        when(repository.save(any(ConnectivityCheck.class))).thenReturn(saved);

        ConnectivityCheck result = service.create("ping");

        assertThat(result).isSameAs(saved);
        ArgumentCaptor<ConnectivityCheck> captor = ArgumentCaptor.forClass(ConnectivityCheck.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).isEqualTo("ping");
    }

    @Test
    void findAll_은_리포지토리의_전체_조회_결과를_그대로_반환한다() {
        List<ConnectivityCheck> rows = List.of(ConnectivityCheck.create("a"), ConnectivityCheck.create("b"));
        when(repository.findAll()).thenReturn(rows);

        List<ConnectivityCheck> result = service.findAll();

        assertThat(result).isSameAs(rows);
    }
}
