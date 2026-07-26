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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Main menu for warehouse workflow. Navigate between receiving and unpacking.
 */
public class MenuScreen {

  private final BackendClient backend;
  private final MenuCallback callback;

  public interface MenuCallback {
    void showReceiving();
    void showUnpacking();
  }

  public MenuScreen(BackendClient backend, MenuCallback callback) {
    this.backend = backend;
    this.callback = callback;
  }

  public BorderPane build() {
    BorderPane root = new BorderPane();
    root.setStyle("-fx-padding: 40; -fx-font-family: 'System';");

    // Header
    Label heading = new Label("Bachat Bazar — Warehouse");
    heading.setFont(Font.font("System", FontWeight.BOLD, 32));
    heading.setStyle("-fx-text-fill: #1f2937;");

    Label subheading = new Label("Select operation");
    subheading.setFont(Font.font("System", 16));
    subheading.setStyle("-fx-text-fill: #6b7280;");

    VBox header = new VBox(10, heading, subheading);
    header.setAlignment(Pos.CENTER);
    root.setTop(header);
    BorderPane.setAlignment(header, Pos.CENTER);

    // Menu buttons
    VBox menu = new VBox(20);
    menu.setAlignment(Pos.CENTER);
    menu.setPadding(new Insets(60, 0, 0, 0));

    Button receivingBtn = new Button("📦 Receive Boxes");
    receivingBtn.setPrefWidth(300);
    receivingBtn.setPrefHeight(80);
    receivingBtn.setFont(Font.font("System", 20));
    receivingBtn.setStyle("-fx-padding: 20; -fx-cursor: hand;");
    receivingBtn.setOnAction(e -> callback.showReceiving());

    Button unpackingBtn = new Button("📋 Unpack Items");
    unpackingBtn.setPrefWidth(300);
    unpackingBtn.setPrefHeight(80);
    unpackingBtn.setFont(Font.font("System", 20));
    unpackingBtn.setStyle("-fx-padding: 20; -fx-cursor: hand;");
    unpackingBtn.setOnAction(e -> callback.showUnpacking());

    menu.getChildren().addAll(receivingBtn, unpackingBtn);

    root.setCenter(menu);

    return root;
  }
}
