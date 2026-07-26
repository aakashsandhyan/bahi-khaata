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
 * Scanning boxes as they arrive. Quick Flow UI: lot selector → carton scan → progress.
 */
public class ReceivingScreen {

  private static final Font HEADING = Font.font("System", FontWeight.BOLD, 26);
  private static final Font LARGE = Font.font("System", 48);
  private static final Font NORMAL = Font.font("System", 14);

  private final BackendClient backend;
  private UUID currentLotId;

  public ReceivingScreen(BackendClient backend) {
    this.backend = backend;
  }

  public BorderPane build() {
    BorderPane root = new BorderPane();
    root.setStyle("-fx-padding: 20; -fx-font-family: 'System';");

    // Header: lot name
    Label header = new Label("Receiving");
    header.setFont(HEADING);
    header.setStyle("-fx-text-fill: #1f2937;");
    BorderPane.setAlignment(header, Pos.CENTER_LEFT);
    root.setTop(header);

    // Main: carton scan input + progress
    VBox center = new VBox(20);
    center.setPadding(new Insets(40, 20, 20, 20));
    center.setAlignment(Pos.TOP_CENTER);

    // Progress display
    Label progressLabel = new Label("0 / 47 boxes");
    progressLabel.setFont(LARGE);
    progressLabel.setStyle("-fx-text-fill: #2563eb;");

    Label lotLabel = new Label("LOT-0231 Kitchen Mix");
    lotLabel.setFont(NORMAL);
    lotLabel.setStyle("-fx-text-fill: #6b7280;");

    // Carton scan input
    TextField scanInput = new TextField();
    scanInput.setPromptText("Scan carton ID...");
    scanInput.setFont(Font.font("Courier", 20));
    scanInput.setStyle("-fx-padding: 16; -fx-font-size: 20;");
    scanInput.setPrefWidth(400);
    scanInput.setOnAction(e -> {
      String cartonId = scanInput.getText().trim();
      if (!cartonId.isEmpty()) {
        // POST /api/lots/{lotId}/receive-box
        // For now, just log and clear
        System.out.println("Scanned: " + cartonId);
        scanInput.clear();
        scanInput.requestFocus();
      }
    });

    // Feedback label
    Label feedback = new Label();
    feedback.setFont(NORMAL);
    feedback.setStyle("-fx-text-fill: #10b981;");

    center.getChildren().addAll(lotLabel, progressLabel, scanInput, feedback);

    // Buttons: Not here / Damaged / Done
    VBox bottom = new VBox(10);
    bottom.setPadding(new Insets(20));
    bottom.setStyle("-fx-alignment: center;");

    Button notHereBtn = new Button("Not received");
    notHereBtn.setPrefWidth(200);
    notHereBtn.setStyle("-fx-padding: 10; -fx-font-size: 14;");

    Button damagedBtn = new Button("Damaged");
    damagedBtn.setPrefWidth(200);
    damagedBtn.setStyle("-fx-padding: 10; -fx-font-size: 14;");

    Button doneBtn = new Button("Done");
    doneBtn.setPrefWidth(200);
    doneBtn.setStyle("-fx-padding: 10; -fx-font-size: 14; -fx-text-fill: white; -fx-background-color: #6b7280;");

    bottom.getChildren().addAll(notHereBtn, damagedBtn, doneBtn);

    root.setCenter(center);
    root.setBottom(bottom);

    scanInput.requestFocus();
    return root;
  }

  public void setLotId(UUID lotId) {
    this.currentLotId = lotId;
  }
}
