package com.bahikhaata.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Nested
    @DisplayName("Rounding under CGST section 170")
    class Rounding {

        @ParameterizedTest(name = "{0} paise rounds to {1} paise")
        @CsvSource({
            // Exact rupees are untouched.
            "12300, 12300",
            "0,     0",
            // Below fifty rounds down.
            "12301, 12300",
            "12349, 12300",
            // Exactly fifty rounds up. This is the boundary the Act specifies and
            // the one an off-by-one implementation gets wrong.
            "12350, 12400",
            // Above fifty rounds up.
            "12351, 12400",
            "12399, 12400",
        })
        void roundsHalfUp(long input, long expected) {
            assertThat(Money.ofPaise(input).roundToRupee()).isEqualTo(Money.ofPaise(expected));
        }

        @ParameterizedTest(name = "{0} paise rounds to {1} paise")
        @CsvSource({
            "-12349, -12300",
            // Away from zero, so a reversing entry mirrors the original exactly
            // rather than differing by a rupee.
            "-12350, -12400",
            "-12351, -12400",
        })
        void roundsNegativesAwayFromZero(long input, long expected) {
            assertThat(Money.ofPaise(input).roundToRupee()).isEqualTo(Money.ofPaise(expected));
        }

        @Test
        @DisplayName("Rounding once differs from rounding each line, which is why it happens once")
        void roundingOnceIsNotRoundingEachLine() {
            Money a = Money.ofPaise(1040); // 10.40
            Money b = Money.ofPaise(1040);
            Money c = Money.ofPaise(1040);

            Money roundedTotal = a.plus(b).plus(c).roundToRupee(); // 31.20 -> 31.00
            Money totalOfRounded =
                    a.roundToRupee().plus(b.roundToRupee()).plus(c.roundToRupee()); // 30.00

            assertThat(roundedTotal).isEqualTo(Money.ofPaise(3100));
            assertThat(totalOfRounded).isEqualTo(Money.ofPaise(3000));
            assertThat(roundedTotal).isNotEqualTo(totalOfRounded);
        }
    }

    @Nested
    @DisplayName("Parsing")
    class Parsing {

        @ParameterizedTest(name = "\"{0}\" is {1} paise")
        @CsvSource({
            "0,           0",
            "1,           100",
            "1.5,         150",
            "1.50,        150",
            "1.05,        105",
            "1234.56,     123456",
            "'₹1,234.56', 123456",
            "-5.25,       -525",
            "+5.25,       525",
        })
        void parsesRupeeAmounts(String text, long expectedPaise) {
            assertThat(Money.parse(text)).isEqualTo(Money.ofPaise(expectedPaise));
        }

        @ParameterizedTest(name = "\"{0}\" is rejected")
        @ValueSource(strings = {"", "   ", "abc", "1.", ".5", "1.234", "1.2.3", "12a", "--5"})
        void rejectsWhatIsNotARupeeAmount(String text) {
            assertThatThrownBy(() -> Money.parse(text))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Three decimal places are refused rather than silently truncated")
        void refusesFractionsOfAPaise() {
            // Quietly dropping the third digit would lose money one invoice at a time.
            assertThatThrownBy(() -> Money.parse("10.999"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void parseRoundTripsThroughPlainString() {
            Money original = Money.ofPaise(-123456);
            assertThat(Money.parse(original.toPlainString())).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("Formatting")
    class Formatting {

        @ParameterizedTest(name = "{0} paise formats as {1}")
        @CsvSource({
            "0,         ₹0.00",
            "100,       ₹1.00",
            "105,       ₹1.05",
            "99999,     '₹999.99'",
            "100000,    '₹1,000.00'",
            // Indian grouping: three digits, then pairs.
            "10000000,  '₹1,00,000.00'",
            "123456789, '₹12,34,567.89'",
            "-100,      -₹1.00",
        })
        void formatsWithIndianGrouping(long paise, String expected) {
            assertThat(Money.ofPaise(paise).format()).isEqualTo(expected);
        }

        @Test
        void plainStringIsUngroupedAndAlwaysTwoDecimals() {
            assertThat(Money.ofPaise(123456789).toPlainString()).isEqualTo("1234567.89");
            assertThat(Money.ofPaise(5).toPlainString()).isEqualTo("0.05");
            assertThat(Money.ofPaise(-5).toPlainString()).isEqualTo("-0.05");
        }
    }

    @Nested
    @DisplayName("Arithmetic")
    class Arithmetic {

        @Test
        void addsSubtractsAndMultiplies() {
            assertThat(Money.ofPaise(1050).plus(Money.ofPaise(250))).isEqualTo(Money.ofPaise(1300));
            assertThat(Money.ofPaise(1050).minus(Money.ofPaise(250))).isEqualTo(Money.ofPaise(800));
            assertThat(Money.ofPaise(1050).times(3)).isEqualTo(Money.ofPaise(3150));
        }

        @Test
        @DisplayName("Overflow throws instead of wrapping to a negative total")
        void overflowThrows() {
            Money huge = Money.ofPaise(Long.MAX_VALUE);

            assertThatThrownBy(() -> huge.plus(Money.ofPaise(1)))
                    .isInstanceOf(ArithmeticException.class);
            assertThatThrownBy(() -> huge.times(2)).isInstanceOf(ArithmeticException.class);
            assertThatThrownBy(() -> Money.ofRupees(Long.MAX_VALUE))
                    .isInstanceOf(ArithmeticException.class);
        }

        @Test
        void comparesAndReportsSign() {
            assertThat(Money.ofPaise(100)).isGreaterThan(Money.ofPaise(99));
            assertThat(Money.ofPaise(-1).isNegative()).isTrue();
            assertThat(Money.ZERO.isZero()).isTrue();
            assertThat(Money.ofPaise(-500).abs()).isEqualTo(Money.ofPaise(500));
        }
    }
}
