package com.eventhub.framework.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("framework")
class ExchangeRecorderTest {

    @Test
    void secretsAreMaskedInRecordedExchanges() {
        String masked = ExchangeRecorder.mask("{\"email\":\"a@b.c\",\"password\":\"@Z123\",\"data\":{\"token\":\"eyJ.x.y\"}}");

        assertThat(masked)
                .contains("\"email\":\"a@b.c\"")
                .contains("\"password\":\"***\"")
                .contains("\"token\":\"***\"")
                .doesNotContain("@Z123")
                .doesNotContain("eyJ");
    }
}
