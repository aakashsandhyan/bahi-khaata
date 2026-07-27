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
package com.bahikhaata.backend.print;

import com.bahikhaata.contracts.PrintLabelRequest;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import org.springframework.stereotype.Service;

/**
 * Renders ZPL label templates for barcode printing.
 *
 * <p>Uses FreeMarker to substitute product data into a ZPL template,
 * producing printer-ready commands for TSC TE-244.
 */
@Service
public class LabelTemplateService {
    private final Configuration freeMarker;

    public LabelTemplateService(Configuration freeMarker) {
        this.freeMarker = freeMarker;
    }

    public String renderLabel(PrintLabelRequest request) throws PrinterDriver.PrinterException {
        try {
            Template template = freeMarker.getTemplate("label.zpl");
            StringWriter out = new StringWriter();
            template.process(request.toMap(), out);
            return out.toString();
        } catch (IOException e) {
            throw new PrinterDriver.PrinterException("Template not found: " + e.getMessage(), e);
        } catch (TemplateException e) {
            throw new PrinterDriver.PrinterException("Template render failed: " + e.getMessage(), e);
        }
    }
}
