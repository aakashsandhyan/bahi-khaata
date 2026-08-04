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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The driver reads the printer address as one of three shapes — network, Linux serial (stubbed), or
 * an OS-registered printer name. These tests cover what is deterministic on any machine: the network
 * path (a real loopback socket, no hardware needed) and the printer-name path's not-found case (a
 * name that certainly is not installed anywhere this runs). Sending an actual label to a real
 * installed printer is a manual check on the machine that has one — see the deploy runbook.
 */
@ExtendWith(MockitoExtension.class)
class UsbPrinterDriverTest {

    @Mock private PrinterConfigRepository configRepo;

    @Test
    void copiesRepeatsTheWholeLabelNotABareXz() throws Exception {
        // Regression test: copies used to append bare "^XZ" commands after one label, which does
        // not print another copy — a ^XZ with no matching ^XA does nothing. It must send the whole
        // ^XA...^XZ document N times.
        try (ServerSocket server = new ServerSocket(0)) {
            PrinterConfig config = enabledConfig("127.0.0.1:" + server.getLocalPort());
            when(configRepo.getSingleton()).thenReturn(java.util.Optional.of(config));
            UsbPrinterDriver driver = new UsbPrinterDriver(configRepo);

            CompletableFuture<byte[]> received = acceptOnce(server);
            driver.sendLabel("^XA^FDhello^FS^XZ\n", 3);

            String sent = new String(received.get(), StandardCharsets.US_ASCII);
            assertEquals(3, countOccurrences(sent, "^XA"), "expected 3 whole documents, got: " + sent);
            assertEquals(3, countOccurrences(sent, "^XZ"));
        }
    }

    @Test
    void concurrentSendsAreSerializedAndPacedApart() throws Exception {
        // Regression test: the poller, flush, and bulk paths all share one physical printer, and
        // concurrent sendLabel calls used to hit the wire together — rows landed back-to-back,
        // outran the TE-244's feed, and printed with clipped tops. The driver must be the single
        // gate: one send at a time, with a minimum breather between sends.
        try (ServerSocket server = new ServerSocket(0)) {
            PrinterConfig config = enabledConfig("127.0.0.1:" + server.getLocalPort());
            when(configRepo.getSingleton()).thenReturn(java.util.Optional.of(config));
            UsbPrinterDriver driver = new UsbPrinterDriver(configRepo);

            CompletableFuture<Long> firstArrival = acceptOnceTimed(server);
            CompletableFuture<Long> secondArrival = firstArrival.thenCompose(t -> acceptOnceTimed(server));

            CompletableFuture<Void> a = CompletableFuture.runAsync(() -> sendQuietly(driver));
            CompletableFuture<Void> b = CompletableFuture.runAsync(() -> sendQuietly(driver));
            CompletableFuture.allOf(a, b).get();

            long gapMs = (secondArrival.get() - firstArrival.get()) / 1_000_000;
            assertTrue(gapMs >= UsbPrinterDriver.SEND_GAP_MS - 50,
                "sends arrived " + gapMs + "ms apart; expected at least " + UsbPrinterDriver.SEND_GAP_MS);
        }
    }

    @Test
    void disabledPrinterRefusesToSend() {
        PrinterConfig config = enabledConfig("127.0.0.1:9100");
        config.setEnabled(false);
        when(configRepo.getSingleton()).thenReturn(java.util.Optional.of(config));
        UsbPrinterDriver driver = new UsbPrinterDriver(configRepo);

        var e = assertThrows(PrinterDriver.PrinterException.class, () -> driver.sendLabel("^XA^XZ", 1));
        assertTrue(e.getMessage().toLowerCase().contains("disabled"));
    }

    @Test
    void unconfiguredPrinterRefusesToSend() {
        when(configRepo.getSingleton()).thenReturn(java.util.Optional.empty());
        UsbPrinterDriver driver = new UsbPrinterDriver(configRepo);

        assertThrows(PrinterDriver.PrinterException.class, () -> driver.sendLabel("^XA^XZ", 1));
    }

    @Test
    void printerNameNotInstalledIsRefusedWithAClearMessage() {
        // No machine running this test has a printer literally named this.
        PrinterConfig config = enabledConfig("Definitely-Not-An-Installed-Printer-42");
        when(configRepo.getSingleton()).thenReturn(java.util.Optional.of(config));
        UsbPrinterDriver driver = new UsbPrinterDriver(configRepo);

        var e = assertThrows(PrinterDriver.PrinterException.class, () -> driver.sendLabel("^XA^XZ", 1));
        assertTrue(e.getMessage().contains("not found") || e.getMessage().toLowerCase().contains("not an installed"));
    }

    @Test
    void statusForAMissingPrinterNameIsNotConnected() throws Exception {
        PrinterConfig config = enabledConfig("Definitely-Not-An-Installed-Printer-42");
        when(configRepo.getSingleton()).thenReturn(java.util.Optional.of(config));
        UsbPrinterDriver driver = new UsbPrinterDriver(configRepo);

        PrinterDriver.PrinterStatus status = driver.getStatus();
        assertFalse(status.connected());
    }

    @Test
    void invalidPortNumberIsRefusedCleanly() {
        PrinterConfig config = enabledConfig("127.0.0.1:99999"); // out of the 0-65535 range
        when(configRepo.getSingleton()).thenReturn(java.util.Optional.of(config));
        UsbPrinterDriver driver = new UsbPrinterDriver(configRepo);

        var e = assertThrows(PrinterDriver.PrinterException.class, () -> driver.sendLabel("^XA^XZ", 1));
        assertTrue(e.getMessage().toLowerCase().contains("port"));
    }

    private static PrinterConfig enabledConfig(String address) {
        return new PrinterConfig(PrinterConfig.SINGLETON_ID, address, 9600, "4x6", 1, true);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) != -1) {
            count++;
            from += needle.length();
        }
        return count;
    }

    /** Accepts exactly one connection on the given server socket and captures everything it sends. */
    private static CompletableFuture<byte[]> acceptOnce(ServerSocket server) {
        return CompletableFuture.supplyAsync(() -> {
            try (Socket client = server.accept()) {
                return client.getInputStream().readAllBytes();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** Accepts one connection, drains it, and returns the nanoTime the connection arrived. */
    private static CompletableFuture<Long> acceptOnceTimed(ServerSocket server) {
        return CompletableFuture.supplyAsync(() -> {
            try (Socket client = server.accept()) {
                long arrived = System.nanoTime();
                client.getInputStream().readAllBytes();
                return arrived;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void sendQuietly(UsbPrinterDriver driver) {
        try {
            driver.sendLabel("^XA^FDrow^FS^XZ\n", 1);
        } catch (PrinterDriver.PrinterException e) {
            throw new RuntimeException(e);
        }
    }
}
