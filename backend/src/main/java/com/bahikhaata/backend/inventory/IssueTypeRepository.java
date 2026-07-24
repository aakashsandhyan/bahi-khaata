/*
 * bahi-khaata — point of sale for Bachat Baazar
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
package com.bahikhaata.backend.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueTypeRepository extends JpaRepository<IssueType, String> {

    /** The kinds of work offered for a department, in label order — the menu a category presents. */
    @Query(
            value =
                    "SELECT it.code AS code, it.label AS label FROM issue_type it "
                            + "JOIN category_issue_type cit ON cit.issue_type_code = it.code "
                            + "WHERE cit.category_code = :category ORDER BY it.label",
            nativeQuery = true)
    List<IssueTypeView> findForCategory(@Param("category") String category);

    /** Projection for {@link #findForCategory}: a code and the label to show for it. */
    interface IssueTypeView {
        String getCode();

        String getLabel();
    }
}
