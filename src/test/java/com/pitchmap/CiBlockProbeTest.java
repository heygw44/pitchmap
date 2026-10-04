package com.pitchmap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CiBlockProbeTest {

    @Test
    void alwaysFails() {
        assertThat(1).isEqualTo(2);
    }
}
