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
package com.bahikhaata.backend.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A kind of work a not-quite-sellable item needs — clean, repair, rebuild — offered per department.
 *
 * <p>Data rather than an enum, the same choice made for {@code Category}, so the shop can change the
 * kinds of work it does without a release. Which kinds apply to which department is held in
 * {@code category_issue_type}; this is the master list of the kinds themselves.
 */
@Entity
@Table(name = "issue_type")
public class IssueType {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "label", nullable = false)
    private String label;

    protected IssueType() {}

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
