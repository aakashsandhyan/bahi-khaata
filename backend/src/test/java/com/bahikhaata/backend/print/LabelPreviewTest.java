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
package com.bahikhaata.backend.print;

import com.bahikhaata.contracts.PrintLabelRequest;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Writes the composed label bitmaps to build/label-preview so the layout can be eyeballed as an
 * image before a physical label is spent — the composed bitmap IS what prints, pixel for pixel.
 */
class LabelPreviewTest {

    @Test
    void writePreviews() throws Exception {
        LabelTemplateService service = new LabelTemplateService();
        Method compose = LabelTemplateService.class.getDeclaredMethod(
                "composeColumn", PrintLabelRequest.class);
        compose.setAccessible(true);

        File dir = new File("build/label-preview");
        dir.mkdirs();

        BufferedImage withMrp = (BufferedImage) compose.invoke(service,
                new PrintLabelRequest("BBZ-100042", "Prestige Cooker 5L Test", 1499_00L, 449_00L));
        BufferedImage withoutMrp = (BufferedImage) compose.invoke(service,
                new PrintLabelRequest("BBZ-100043", "Milton Flask 1000ml Test", null, 299_00L));

        assertNotNull(withMrp);
        ImageIO.write(withMrp, "png", new File(dir, "with-mrp.png"));
        ImageIO.write(withoutMrp, "png", new File(dir, "without-mrp.png"));
    }
}
