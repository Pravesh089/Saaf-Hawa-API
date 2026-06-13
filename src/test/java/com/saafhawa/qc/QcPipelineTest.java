package com.saafhawa.qc;

import com.saafhawa.catalog.Pollutant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Row-local QC flag rules (§4.4, FR-3.1). */
class QcPipelineTest {

    private QcPipeline pipeline;
    private Pollutant pm25;
    private Pollutant no2;

    @BeforeEach
    void setUp() {
        QcConfig config = mock(QcConfig.class);
        when(config.sentinelValues()).thenReturn(Set.of(999.0, 9999.0));
        when(config.version()).thenReturn("test");
        pipeline = new QcPipeline(config);

        pm25 = mock(Pollutant.class);
        when(pm25.isZeroImplausible()).thenReturn(true);
        when(pm25.getRangeMin()).thenReturn(0.0);
        when(pm25.getRangeMax()).thenReturn(2000.0);

        no2 = mock(Pollutant.class);
        when(no2.isZeroImplausible()).thenReturn(false);
        when(no2.getRangeMin()).thenReturn(0.0);
        when(no2.getRangeMax()).thenReturn(1000.0);
    }

    private Set<QcFlag> flags(Pollutant p, Double value) {
        return QcFlag.fromMask(pipeline.applyRowLocal(p, value));
    }

    @Test
    void flagsNegative() {
        assertThat(flags(no2, -5.0)).contains(QcFlag.NEGATIVE);
    }

    @Test
    void flagsZeroOnlyWhenImplausible() {
        assertThat(flags(pm25, 0.0)).contains(QcFlag.ZERO_SUSPECT);
        assertThat(flags(no2, 0.0)).doesNotContain(QcFlag.ZERO_SUSPECT);
    }

    @Test
    void flagsSentinel() {
        assertThat(flags(pm25, 999.0)).contains(QcFlag.SENTINEL);
    }

    @Test
    void sentinelTakesPrecedenceOverRange() {
        // A sentinel value should be flagged SENTINEL, not RANGE, even if out of range.
        Set<QcFlag> f = flags(no2, 9999.0);
        assertThat(f).contains(QcFlag.SENTINEL).doesNotContain(QcFlag.RANGE);
    }

    @Test
    void flagsRangeWhenAboveMax() {
        assertThat(flags(pm25, 5000.0)).contains(QcFlag.RANGE);
    }

    @Test
    void cleanValueHasNoFlags() {
        assertThat(pipeline.applyRowLocal(pm25, 182.0)).isZero();
    }
}
