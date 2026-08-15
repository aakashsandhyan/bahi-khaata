/*
 * bahi-khaata — point of sale for Bachat Bazaar
 * Copyright (C) 2026 Aakash Sandhyan
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.bahikhaata.backend.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bahikhaata.backend.checkout.SaleRepository;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.RegisterCloseSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The drawer math and its guards. Expected cash = float + cash sales + cash in − cash out;
 * over/short = counted − expected, pinned at close. The guards: no double-open, no movement
 * without an open session, no reuse of a closed session.
 */
@ExtendWith(MockitoExtension.class)
class RegisterServiceTest {

    @Mock private RegisterSessionRepository sessions;
    @Mock private RegisterCashMovementRepository movements;
    @Mock private SaleRepository sales;

    private RegisterService service() {
        return new RegisterService(sessions, movements, sales);
    }

    private RegisterSession openSession(long floatPaise) {
        return RegisterSession.open("Register 1", "Shakti", Money.ofPaise(floatPaise), Instant.now());
    }

    @Test
    void openingARegisterCreatesAnOpenSessionWithTheFloat() {
        when(sessions.findFirstByRegisterNameAndStatus("Register 1", RegisterSession.OPEN))
                .thenReturn(Optional.empty());
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterSession s = service().open("Register 1", "Shakti", 200_000);

        assertThat(s.isOpen()).isTrue();
        assertThat(s.getFloatAmount().paise()).isEqualTo(200_000);
        assertThat(s.getOperatorName()).isEqualTo("Shakti");
    }

    @Test
    void aRegisterCannotBeDoubleOpened() {
        when(sessions.findFirstByRegisterNameAndStatus("Register 1", RegisterSession.OPEN))
                .thenReturn(Optional.of(openSession(0)));

        assertThatThrownBy(() -> service().open("Register 1", "Aakash", 100_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already open");
    }

    @Test
    void anUnknownRegisterIsRefused() {
        assertThatThrownBy(() -> service().open("Register 9", "Aakash", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown register");
    }

    @Test
    void aMovementWithoutAnOpenSessionIsRefused() {
        when(sessions.findFirstByRegisterNameAndStatus("Register 1", RegisterSession.OPEN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().recordMovement("Register 1", "OUT", 50_000, "supplier"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no open session");
    }

    @Test
    void closeComputesExpectedFromCashOnlyAndPinsOverShort() {
        RegisterSession session = openSession(200_000); // ₹2,000 float
        when(sessions.findFirstByRegisterNameAndStatus("Register 1", RegisterSession.OPEN))
                .thenReturn(Optional.of(session));
        // Cash sales sum comes from the repository query — UPI/CARD are already excluded there;
        // the service must use this figure and nothing else for the sales part.
        when(sales.cashTotalPaiseForSession(session.getId().toString())).thenReturn(150_000L);
        when(movements.findBySessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of(
                new RegisterCashMovement(session.getId(), "IN", Money.ofPaise(10_000), "change"),
                new RegisterCashMovement(session.getId(), "OUT", Money.ofPaise(50_000), "supplier")));
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // expected = 200,000 + 150,000 + 10,000 − 50,000 = 310,000; counted ₹3,220 → over +12,000
        RegisterCloseSummary summary = service().close("Register 1", 322_000);

        assertThat(summary.expectedPaise()).isEqualTo(310_000);
        assertThat(summary.overShortPaise()).isEqualTo(12_000);
        assertThat(session.isOpen()).isFalse();
        assertThat(session.getOverShortPaise()).isEqualTo(12_000);
        assertThat(session.getCounted().paise()).isEqualTo(322_000);
    }

    @Test
    void aDrawerShortPinsANegativeFigure() {
        RegisterSession session = openSession(100_000);
        when(sessions.findFirstByRegisterNameAndStatus("Register 1", RegisterSession.OPEN))
                .thenReturn(Optional.of(session));
        when(sales.cashTotalPaiseForSession(session.getId().toString())).thenReturn(0L);
        when(movements.findBySessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of());
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegisterCloseSummary summary = service().close("Register 1", 88_000);

        assertThat(summary.overShortPaise()).isEqualTo(-12_000);
    }

    @Test
    void aClosedSessionCannotCloseAgain() {
        RegisterSession session = openSession(0);
        session.close(Money.ofPaise(0), 0, Instant.now());

        assertThatThrownBy(() -> session.close(Money.ofPaise(0), 0, Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requireOpenByIdRefusesAClosedSession() {
        RegisterSession session = openSession(0);
        session.close(Money.ofPaise(0), 0, Instant.now());
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service().requireOpenById(session.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closed");
    }
}
