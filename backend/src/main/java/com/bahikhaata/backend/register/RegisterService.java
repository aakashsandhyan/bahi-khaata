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

import com.bahikhaata.backend.checkout.SaleRepository;
import com.bahikhaata.contracts.Money;
import com.bahikhaata.contracts.RegisterCloseSummary;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The drawer lifecycle. A session is the accountable unit: it opens with a counted float, absorbs
 * cash movements and cash sales while open, and closes against a counted drawer. Expected cash at
 * close is float + cash sales + cash in − cash out; the difference to the count is the over/short,
 * pinned onto the session (spec: register-sessions).
 */
@Service
public class RegisterService {

    /** The shop's two physical drawers. The table doesn't care; the service names the fixture. */
    public static final java.util.List<String> REGISTERS = java.util.List.of("Register 1", "Register 2");

    private final RegisterSessionRepository sessions;
    private final RegisterCashMovementRepository movements;
    private final SaleRepository sales;

    public RegisterService(
            RegisterSessionRepository sessions,
            RegisterCashMovementRepository movements,
            SaleRepository sales) {
        this.sessions = sessions;
        this.movements = movements;
        this.sales = sales;
    }

    /** Opens a register: refused while a session is already open on it. */
    @Transactional
    public RegisterSession open(String registerName, String operatorName, long floatPaise) {
        requireKnownRegister(registerName);
        if (operatorName == null || operatorName.isBlank()) {
            throw new IllegalArgumentException("an operator name is required to open a register");
        }
        if (floatPaise < 0) {
            throw new IllegalArgumentException("the float cannot be negative");
        }
        sessions.findFirstByRegisterNameAndStatus(registerName, RegisterSession.OPEN)
                .ifPresent(s -> {
                    throw new IllegalArgumentException(registerName + " is already open");
                });
        return sessions.save(RegisterSession.open(
                registerName, operatorName.trim(), Money.ofPaise(floatPaise), Instant.now()));
    }

    /** Records cash in/out against the register's open session; refused when none is open. */
    @Transactional
    public RegisterCashMovement recordMovement(String registerName, String direction, long amountPaise, String note) {
        RegisterSession session = requireOpen(registerName);
        if (!RegisterCashMovement.IN.equals(direction) && !RegisterCashMovement.OUT.equals(direction)) {
            throw new IllegalArgumentException("direction must be IN or OUT");
        }
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("the amount must be positive");
        }
        return movements.save(new RegisterCashMovement(
                session.getId(), direction, Money.ofPaise(amountPaise), note));
    }

    /**
     * Closes the register's open session against the counted drawer, computing expected cash and
     * pinning the over/short. Returns the close summary the screen shows.
     */
    @Transactional
    public RegisterCloseSummary close(String registerName, long countedPaise) {
        RegisterSession session = requireOpen(registerName);
        if (countedPaise < 0) {
            throw new IllegalArgumentException("the counted amount cannot be negative");
        }
        long expected = expectedCashPaise(session);
        long overShort = countedPaise - expected;
        session.close(Money.ofPaise(countedPaise), overShort, Instant.now());
        sessions.save(session);
        return summarize(session, expected);
    }

    /** Expected drawer cash right now: float + cash sales + cash in − cash out. */
    @Transactional(readOnly = true)
    public long expectedCashPaise(RegisterSession session) {
        long cashSales = sales.cashTotalPaiseForSession(session.getId().toString());
        long in = 0;
        long out = 0;
        for (RegisterCashMovement m : movements.findBySessionIdOrderByCreatedAtAsc(session.getId())) {
            if (RegisterCashMovement.IN.equals(m.getDirection())) {
                in += m.getAmount().paise();
            } else {
                out += m.getAmount().paise();
            }
        }
        return session.getFloatAmount().paise() + cashSales + in - out;
    }

    /** The register's open session, or null — the state the screens render from. */
    @Transactional(readOnly = true)
    public RegisterSession openSession(String registerName) {
        requireKnownRegister(registerName);
        return sessions.findFirstByRegisterNameAndStatus(registerName, RegisterSession.OPEN).orElse(null);
    }

    /** The open session a modern-checkout sale must attach to — by id, verified open. */
    @Transactional(readOnly = true)
    public RegisterSession requireOpenById(UUID sessionId) {
        RegisterSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("no such register session"));
        if (!session.isOpen()) {
            throw new IllegalArgumentException("that register session is closed");
        }
        return session;
    }

    private RegisterSession requireOpen(String registerName) {
        requireKnownRegister(registerName);
        return sessions.findFirstByRegisterNameAndStatus(registerName, RegisterSession.OPEN)
                .orElseThrow(() -> new IllegalArgumentException(registerName + " has no open session"));
    }

    private void requireKnownRegister(String registerName) {
        if (!REGISTERS.contains(registerName)) {
            throw new IllegalArgumentException("unknown register: " + registerName);
        }
    }

    private RegisterCloseSummary summarize(RegisterSession s, long expectedPaise) {
        return new RegisterCloseSummary(
                s.getId(),
                s.getRegisterName(),
                s.getOperatorName(),
                s.getFloatAmount().paise(),
                sales.cashTotalPaiseForSession(s.getId().toString()),
                expectedPaise,
                s.getCounted().paise(),
                s.getOverShortPaise(),
                s.getOpenedAt(),
                s.getClosedAt());
    }
}
