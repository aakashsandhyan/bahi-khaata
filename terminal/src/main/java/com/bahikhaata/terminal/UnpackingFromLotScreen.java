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
package com.bahikhaata.terminal;

import java.util.UUID;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Unpacking items from a lot. Quick Flow UI: lot → box → item scan → MRP.
 */
public class UnpackingFromLotScreen {

  private static final Font HEADING = Font.font("System", FontWeight.BOLD, 26);
  private static final Font LARGE = Font.font("System", 48);
  private static final Font NORMAL = Font.font("System", 14);

  private final BackendClient backend;
  private UUID currentLotId;
  private String currentBoxId;

  public UnpackingFromLotScreen(BackendClient backend) {
    this.backend = backend;
  }

  public BorderPane build() {
    BorderPane root = new BorderPane();
    root.setStyle("-fx-padding: 20; -fx-font-family: 'System';");

    // Header
    Label header = new Label("Unpacking");
    header.setFont(HEADING);
    header.setStyle("-fx-text-fill: #1f2937;");
    BorderPane.setAlignment(header, Pos.CENTER_LEFT);
    root.setTop(header);

    // Main: lot selector, box selector, item scan
    VBox center = new VBox(20);
    center.setPadding(new Insets(40, 20, 20, 20));
    center.setAlignment(Pos.TOP_CENTER);

    Label lotLabel = new Label("LOT-0231");
    lotLabel.setFont(NORMAL);
    lotLabel.setStyle("-fx-text-fill: #6b7280;");

    Label boxLabel = new Label("BOX-0231-001");
    boxLabel.setFont(NORMAL);
    boxLabel.setStyle("-fx-text-fill: #6b7280;");

    // Item scan input
    TextField itemInput = new TextField();
    itemInput.setPromptText("Scan item or barcode...");
    itemInput.setFont(Font.font("Courier", 20));
    itemInput.setStyle("-fx-padding: 16; -fx-font-size: 20;");
    itemInput.setPrefWidth(400);

    // MRP input (appears after item scan)
    TextField mrpInput = new TextField();
    mrpInput.setPromptText("Enter MRP...");
    mrpInput.setFont(Font.font("Courier", 16));
    mrpInput.setStyle("-fx-padding: 12; -fx-font-size: 16;");
    mrpInput.setPrefWidth(400);
    mrpInput.setVisible(false);

    // Feedback
    Label feedback = new Label();
    feedback.setFont(NORMAL);
    feedback.setStyle("-fx-text-fill: #10b981;");

    // Item scan handler
    itemInput.setOnAction(e -> {
      String itemCode = itemInput.getText().trim();
      if (!itemCode.isEmpty() && currentLotId != null && currentBoxId != null) {
        // In real implementation, would:
        // 1. POST /api/stock-movements or similar
        // 2. If product unknown, show MRP input
        // 3. Record item count
        feedback.setText("✓ Item scanned: " + itemCode);
        itemInput.clear();
        itemInput.requestFocus();
      }
    });

    center.getChildren().addAll(lotLabel, boxLabel, itemInput, mrpInput, feedback);

    // Buttons: Reject box, Next box, Done
    VBox bottom = new VBox(10);
    bottom.setPadding(new Insets(20));
    bottom.setStyle("-fx-alignment: center;");

    Button rejectBtn = new Button("Reject box");
    rejectBtn.setPrefWidth(200);
    rejectBtn.setStyle("-fx-padding: 10; -fx-font-size: 14;");

    Button nextBtn = new Button("Next box");
    nextBtn.setPrefWidth(200);
    nextBtn.setStyle("-fx-padding: 10; -fx-font-size: 14;");

    Button doneBtn = new Button("Done");
    doneBtn.setPrefWidth(200);
    doneBtn.setStyle("-fx-padding: 10; -fx-font-size: 14; -fx-text-fill: white; -fx-background-color: #6b7280;");

    bottom.getChildren().addAll(rejectBtn, nextBtn, doneBtn);

    root.setCenter(center);
    root.setBottom(bottom);

    itemInput.requestFocus();
    return root;
  }

  public void setLotId(UUID lotId) {
    this.currentLotId = lotId;
  }

  public void setBoxId(String boxId) {
    this.currentBoxId = boxId;
  }
}
