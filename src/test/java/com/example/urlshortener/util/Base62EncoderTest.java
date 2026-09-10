package com.example.urlshortener.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies Base62 values, round-trips, and invalid input boundaries. */
@Tag("unit")
class Base62EncoderTest {

    @Test
    void encode_zero_returnsExpectedBaseChar() {
        assertThat(Base62Encoder.encode(0)).isEqualTo("0");
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1",
        "9, 9",
        "10, A",
        "35, Z",
        "36, a",
        "61, z",
        "62, 10",
        "3844, 100",
        "12345, 3D7",
        "9223372036854775807, AzL8n0Y58m7"
    })
    void encode_positiveNumber_returnsExpectedString(long input, String expected) {
        assertThat(Base62Encoder.encode(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void encode_negativeNumber_throwsIllegalArgumentException(long value) {
        assertThatThrownBy(() -> Base62Encoder.encode(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "A, 10", "Z, 35", "a, 36", "z, 61", "10, 62",
        "3D7, 12345", "AzL8n0Y58m7, 9223372036854775807", "0001, 1"})
    void decode_validCode_returnsOriginalNumber(String code, long expected) {
        assertThat(Base62Encoder.decode(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("randomIds")
    void encodeThenDecode_randomValues_roundTrips(long value) {
        String encoded = Base62Encoder.encode(value);

        assertThat(Base62Encoder.decode(encoded)).isEqualTo(value);
        assertThat(encoded).matches("[0-9A-Za-z]+");
        assertThat(Base62Encoder.encode(value)).isEqualTo(encoded);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 61, 62, 63, 3843, 3844, 3845, Long.MAX_VALUE - 1, Long.MAX_VALUE})
    void encodeThenDecode_boundaryValues_roundTrips(long value) {
        assertThat(Base62Encoder.decode(Base62Encoder.encode(value))).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc!", "-1", "+1", "a_b", "a-b", " A", "A ", "é", "１２"})
    void decode_invalidCharacter_throwsIllegalArgumentException(String code) {
        assertThatThrownBy(() -> Base62Encoder.decode(code))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void decode_blankCode_throwsIllegalArgumentException(String code) {
        assertThatThrownBy(() -> Base62Encoder.decode(code))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AzL8n0Y58m8", "zzzzzzzzzzz", "100000000000000000000000"})
    void decode_valueExceedingLongMax_throwsIllegalArgumentException(String code) {
        assertThatThrownBy(() -> Base62Encoder.decode(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds Long.MAX_VALUE");
    }

    private static LongStream randomIds() {
        return new Random(62).longs(100).map(value -> value & Long.MAX_VALUE);
    }
}
