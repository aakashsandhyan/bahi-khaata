/*
 * bahi-khaata — point of sale for Bachat Bazar
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
package com.bahikhaata.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No floating point in the modules that handle money.
 *
 * <p>Task 1.6 requires no {@code double} or {@code float} in the money type or its
 * callers. Reading the code once to confirm that would be true today and unenforced
 * tomorrow, so it is a rule instead.
 *
 * <p>Scoped to {@code contracts} and {@code backend} rather than the whole project:
 * {@code terminal} is JavaFX, where coordinates, widths and opacities are legitimately
 * {@code double}. Banning it there would produce noise, and noise gets suppressed.
 *
 * <p>No {@code allowEmptyShould} exemption here: both packages carry real classes, so a
 * rule matching nothing means the packages were renamed and the check has silently
 * stopped running.
 */
class FloatingPointPolicyTest {

    private static final String CONTRACTS = "com.bahikhaata.contracts";
    private static final String BACKEND = "com.bahikhaata.backend";

    private static JavaClasses moneyHandlingClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(CONTRACTS, BACKEND);
    }

    @Test
    @DisplayName("No field in contracts or backend is a floating-point type")
    void noFloatingPointFields() {
        ArchRule rule = noFields()
                .should()
                .haveRawType(double.class)
                .orShould()
                .haveRawType(float.class)
                .orShould()
                .haveRawType(Double.class)
                .orShould()
                .haveRawType(Float.class)
                .because(
                        "money is an integer count of paise; a floating-point field is how an "
                                + "amount stops being exact and reaches an invoice wrong");

        rule.check(moneyHandlingClasses());
    }

    @Test
    @DisplayName("No method in contracts or backend returns a floating-point type")
    void noFloatingPointReturnTypes() {
        ArchRule rule = noMethods()
                .should()
                .haveRawReturnType(double.class)
                .orShould()
                .haveRawReturnType(float.class)
                .orShould()
                .haveRawReturnType(Double.class)
                .orShould()
                .haveRawReturnType(Float.class)
                .because(
                        "a computation returning a double has already lost exactness, whatever "
                                + "the caller does with it");

        rule.check(moneyHandlingClasses());
    }
}
