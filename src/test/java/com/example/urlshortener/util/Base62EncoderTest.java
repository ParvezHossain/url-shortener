package com.example.urlshortener.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class Base62EncoderTest {

    @Test
    void encode_zero_returnsExpectedBaseChar() {
        assertThat(Base62Encoder.encode(0)).isEqualTo("0");
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1",
        "61, z",
        "62, 10",
        "12345, 3D7"
    })
    void encode_positiveNumber_returnsExpectedString(long input, String expected) {
        assertThat(Base62Encoder.encode(input)).isEqualTo(expected);
    }

    @Test
    void encode_negativeNumber_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Base62Encoder.encode(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_validCode_returnsOriginalNumber() {
        assertThat(Base62Encoder.decode("3D7")).isEqualTo(12345L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 61, 62, 1000, 999999, Long.MAX_VALUE / 2})
    void encodeThenDecode_randomValues_roundTrips(long value) {
        String encoded = Base62Encoder.encode(value);
        assertThat(Base62Encoder.decode(encoded)).isEqualTo(value);
    }

    @Test
    void decode_invalidCharacter_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Base62Encoder.decode("abc!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_blankCode_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Base62Encoder.decode(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
