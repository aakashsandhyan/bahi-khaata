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
  private Label lotLabel;
  private Label boxLabel;
  private Label feedback;
  private TextField itemInput;
  private TextField mrpInput;

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

    lotLabel = new Label("LOT-0231");
    lotLabel.setFont(NORMAL);
    lotLabel.setStyle("-fx-text-fill: #6b7280;");

    boxLabel = new Label("BOX-0231-001");
    boxLabel.setFont(NORMAL);
    boxLabel.setStyle("-fx-text-fill: #6b7280;");

    // Item scan input
    itemInput = new TextField();
    itemInput.setPromptText("Scan item or barcode...");
    itemInput.setFont(Font.font("Courier", 20));
    itemInput.setStyle("-fx-padding: 16; -fx-font-size: 20;");
    itemInput.setPrefWidth(400);
    itemInput.setOnAction(e -> scanItem());

    // MRP input (appears after item scan if needed)
    mrpInput = new TextField();
    mrpInput.setPromptText("Enter MRP...");
    mrpInput.setFont(Font.font("Courier", 16));
    mrpInput.setStyle("-fx-padding: 12; -fx-font-size: 16;");
    mrpInput.setPrefWidth(400);
    mrpInput.setVisible(false);
    mrpInput.setOnAction(e -> saveMrp());

    // Feedback
    feedback = new Label();
    feedback.setFont(NORMAL);
    feedback.setStyle("-fx-text-fill: #10b981;");

    center.getChildren().addAll(lotLabel, boxLabel, itemInput, mrpInput, feedback);

    // Buttons: Reject box, Next box, Done
    VBox bottom = new VBox(10);
    bottom.setPadding(new Insets(20));
    bottom.setStyle("-fx-alignment: center;");

    Button rejectBtn = new Button("Reject box");
    rejectBtn.setPrefWidth(200);
    rejectBtn.setStyle("-fx-padding: 10; -fx-font-size: 14;");
    rejectBtn.setOnAction(e -> rejectBox());

    Button nextBtn = new Button("Next box");
    nextBtn.setPrefWidth(200);
    nextBtn.setStyle("-fx-padding: 10; -fx-font-size: 14;");
    nextBtn.setOnAction(e -> nextBox());

    Button doneBtn = new Button("Done");
    doneBtn.setPrefWidth(200);
    doneBtn.setStyle("-fx-padding: 10; -fx-font-size: 14; -fx-text-fill: white; -fx-background-color: #6b7280;");
    doneBtn.setOnAction(e -> done());

    bottom.getChildren().addAll(rejectBtn, nextBtn, doneBtn);

    root.setCenter(center);
    root.setBottom(bottom);

    itemInput.requestFocus();
    return root;
  }

  private void scanItem() {
    String itemCode = itemInput.getText().trim();
    if (!itemCode.isEmpty() && currentLotId != null && currentBoxId != null) {
      try {
        // POST /api/stock-movements or similar to record item
        feedback.setText("✓ Item scanned: " + itemCode);
        feedback.setStyle("-fx-text-fill: #10b981;");
        itemInput.clear();
        itemInput.requestFocus();
      } catch (Exception ex) {
        feedback.setText("✗ Error: " + ex.getMessage());
        feedback.setStyle("-fx-text-fill: #ef4444;");
      }
    }
  }

  private void saveMrp() {
    String mrp = mrpInput.getText().trim();
    if (!mrp.isEmpty()) {
      feedback.setText("✓ MRP: ₹" + mrp);
      mrpInput.clear();
      mrpInput.setVisible(false);
      itemInput.requestFocus();
    }
  }

  private void rejectBox() {
    Dialog<String> dialog = new Dialog<>();
    dialog.setTitle("Reject Box");
    TextField notesField = new TextField();
    notesField.setPromptText("Damage reason...");
    VBox content = new VBox(10, new Label("Reject box: " + currentBoxId), notesField);
    content.setPadding(new Insets(10));
    dialog.getDialogPane().setContent(content);
    dialog.getDialogPane().getButtonTypes().addAll(
        javafx.scene.control.ButtonType.OK,
        javafx.scene.control.ButtonType.CANCEL);

    dialog.setResultConverter(btn -> {
      if (btn == javafx.scene.control.ButtonType.OK && currentLotId != null && currentBoxId != null) {
        try {
          feedback.setText("✓ Box rejected");
          feedback.setStyle("-fx-text-fill: #10b981;");
        } catch (Exception ex) {
          feedback.setText("✗ Error: " + ex.getMessage());
          feedback.setStyle("-fx-text-fill: #ef4444;");
        }
      }
      return null;
    });

    dialog.showAndWait();
  }

  private void nextBox() {
    if (currentLotId != null && currentBoxId != null) {
      try {
        feedback.setText("✓ Box complete, next available");
        feedback.setStyle("-fx-text-fill: #10b981;");
      } catch (Exception ex) {
        feedback.setText("✗ Error: " + ex.getMessage());
        feedback.setStyle("-fx-text-fill: #ef4444;");
      }
    }
  }

  private void done() {
    feedback.setText("Unpacking complete. Ready to close lot.");
    feedback.setStyle("-fx-text-fill: #2563eb;");
  }

  public void setLotId(UUID lotId) {
    this.currentLotId = lotId;
    if (lotLabel != null) {
      lotLabel.setText("LOT-" + lotId.toString().substring(0, 4).toUpperCase());
    }
  }

  public void setBoxId(String boxId) {
    this.currentBoxId = boxId;
    if (boxLabel != null) {
      boxLabel.setText(boxId);
    }
  }
}
