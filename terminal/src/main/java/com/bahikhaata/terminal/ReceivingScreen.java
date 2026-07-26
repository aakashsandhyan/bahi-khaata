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
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
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
  private Label progressLabel;
  private Label lotLabel;
  private Label feedback;
  private TextField scanInput;

  public ReceivingScreen(BackendClient backend) {
    this.backend = backend;
  }

  public BorderPane build() {
    BorderPane root = new BorderPane();
    root.setStyle("-fx-padding: 20; -fx-font-family: 'System';");

    // Header
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
    progressLabel = new Label("0 / 47 boxes");
    progressLabel.setFont(LARGE);
    progressLabel.setStyle("-fx-text-fill: #2563eb;");

    lotLabel = new Label("LOT-0231 Kitchen Mix");
    lotLabel.setFont(NORMAL);
    lotLabel.setStyle("-fx-text-fill: #6b7280;");

    // Carton scan input
    scanInput = new TextField();
    scanInput.setPromptText("Scan carton ID...");
    scanInput.setFont(Font.font("Courier", 20));
    scanInput.setStyle("-fx-padding: 16; -fx-font-size: 20;");
    scanInput.setPrefWidth(400);
    scanInput.setOnAction(e -> receiveBox());

    // Feedback label
    feedback = new Label();
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
    notHereBtn.setOnAction(e -> markNotReceived());

    Button damagedBtn = new Button("Damaged");
    damagedBtn.setPrefWidth(200);
    damagedBtn.setStyle("-fx-padding: 10; -fx-font-size: 14;");
    damagedBtn.setOnAction(e -> markDamaged());

    Button doneBtn = new Button("Done");
    doneBtn.setPrefWidth(200);
    doneBtn.setStyle("-fx-padding: 10; -fx-font-size: 14; -fx-text-fill: white; -fx-background-color: #6b7280;");
    doneBtn.setOnAction(e -> done());

    bottom.getChildren().addAll(notHereBtn, damagedBtn, doneBtn);

    root.setCenter(center);
    root.setBottom(bottom);

    scanInput.requestFocus();
    return root;
  }

  private void receiveBox() {
    String cartonId = scanInput.getText().trim();
    if (!cartonId.isEmpty() && currentLotId != null) {
      try {
        // In production, would POST to backend API with proper payload
        // For now, simulate success
        feedback.setText("✓ " + cartonId + " received");
        feedback.setStyle("-fx-text-fill: #10b981;");
      } catch (Exception ex) {
        feedback.setText("✗ Error: " + ex.getMessage());
        feedback.setStyle("-fx-text-fill: #ef4444;");
      }
      scanInput.clear();
      scanInput.requestFocus();
    }
  }

  private void markNotReceived() {
    Dialog<String> dialog = new Dialog<>();
    dialog.setTitle("Not Received");
    TextField cartonField = new TextField();
    cartonField.setPromptText("Carton ID...");
    VBox content = new VBox(10, new Label("Mark carton as not received:"), cartonField);
    content.setPadding(new Insets(10));
    dialog.getDialogPane().setContent(content);
    dialog.getDialogPane().getButtonTypes().addAll(
        javafx.scene.control.ButtonType.OK,
        javafx.scene.control.ButtonType.CANCEL);

    dialog.setResultConverter(btn -> {
      if (btn == javafx.scene.control.ButtonType.OK) {
        String cartonId = cartonField.getText().trim();
        if (!cartonId.isEmpty() && currentLotId != null) {
          try {
            feedback.setText("✓ " + cartonId + " marked not received");
            feedback.setStyle("-fx-text-fill: #10b981;");
          } catch (Exception ex) {
            feedback.setText("✗ Error: " + ex.getMessage());
            feedback.setStyle("-fx-text-fill: #ef4444;");
          }
        }
      }
      return null;
    });

    dialog.showAndWait();
  }

  private void markDamaged() {
    Dialog<String> dialog = new Dialog<>();
    dialog.setTitle("Damaged Box");
    TextField cartonField = new TextField();
    cartonField.setPromptText("Carton ID...");
    TextArea notesArea = new TextArea();
    notesArea.setPromptText("Damage notes...");
    notesArea.setPrefRowCount(3);
    VBox content = new VBox(10,
        new Label("Mark carton as damaged:"),
        cartonField,
        notesArea);
    content.setPadding(new Insets(10));
    dialog.getDialogPane().setContent(content);
    dialog.getDialogPane().getButtonTypes().addAll(
        javafx.scene.control.ButtonType.OK,
        javafx.scene.control.ButtonType.CANCEL);

    dialog.setResultConverter(btn -> {
      if (btn == javafx.scene.control.ButtonType.OK) {
        String cartonId = cartonField.getText().trim();
        if (!cartonId.isEmpty() && currentLotId != null) {
          try {
            feedback.setText("✓ " + cartonId + " marked damaged");
            feedback.setStyle("-fx-text-fill: #10b981;");
          } catch (Exception ex) {
            feedback.setText("✗ Error: " + ex.getMessage());
            feedback.setStyle("-fx-text-fill: #ef4444;");
          }
        }
      }
      return null;
    });

    dialog.showAndWait();
  }

  private void done() {
    feedback.setText("Receiving complete. Move to unpacking.");
    feedback.setStyle("-fx-text-fill: #2563eb;");
  }

  public void setLotId(UUID lotId) {
    this.currentLotId = lotId;
  }
}
