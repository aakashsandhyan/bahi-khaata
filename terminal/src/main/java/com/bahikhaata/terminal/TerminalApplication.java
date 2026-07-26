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

import com.bahikhaata.contracts.HealthResponse;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Skeleton checkout terminal. Proves the terminal can start and reach the backend.
 *
 * <p>Deliberately not the real startup behaviour. The specs require the terminal to poll
 * health, show a plain-language waiting state, and refuse to present checkout until the
 * backend answers — that lands in task 7.14 with the rest of the checkout flow. This is a
 * single call reporting what it found.
 */
public class TerminalApplication extends Application {

    private final BackendClient backend = BackendClient.fromSystemProperties();

    @Override
    public void start(Stage stage) {
        Label heading = new Label(Branding.NAME_DEVANAGARI);
        heading.setFont(Branding.devanagari(48));

        Label detail = new Label("Contacting backend…");

        VBox root = new VBox(12, heading, detail);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        stage.setScene(new Scene(root, 900, 640));
        // Title uses the romanized name: a window manager's title bar is not a guaranteed
        // Devanagari surface, and the title is chrome rather than the brand itself.
        stage.setTitle(Branding.NAME_ROMANIZED + " — terminal");
        stage.show();

        checkBackend(detail);
    }

    /**
     * The call runs off the JavaFX application thread. Doing it inline would freeze the
     * window for the duration of the timeout, which is exactly the frozen-window failure
     * the design says a cashier must never see.
     */
    private void checkBackend(Label detail) {
        Task<HealthResponse> check =
                new Task<>() {
                    @Override
                    protected HealthResponse call() {
                        return backend.health();
                    }
                };

        check.setOnSucceeded(
                event -> {
                    // Only once the backend has answered. The unpacking screen reads on every
                    // scan, and opening it against a backend that is not there would greet the
                    // operator with a failure they can do nothing about.
                    showUnpacking(detail.getScene());
                });

        check.setOnFailed(
                event -> detail.setText("Backend unavailable: " + check.getException().getMessage()));

        Thread worker = new Thread(check, "backend-health-check");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Swaps the splash for the unpacking screen.
     *
     * <p>Unpacking is the whole of the terminal today: 3,583 units are expected and none can be
     * sold until someone has opened the cartons and read an MRP off each pack. Checkout comes
     * later, and there is nothing to check out until this is done.
     */
    private void showUnpacking(javafx.scene.Scene scene) {
        MenuScreen.MenuCallback callback = new MenuScreen.MenuCallback() {
            @Override
            public void showReceiving() {
                ReceivingScreen receiving = new ReceivingScreen(backend);
                scene.setRoot(receiving.build());
            }

            @Override
            public void showUnpacking() {
                UnpackingFromLotScreen unpacking = new UnpackingFromLotScreen(backend);
                scene.setRoot(unpacking.build());
            }
        };

        MenuScreen menu = new MenuScreen(backend, callback);
        scene.setRoot(menu.build());
    }

    @Override
    public void stop() {
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
