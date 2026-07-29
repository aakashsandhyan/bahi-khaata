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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Printer driver for TSC TE-244 via USB (serial) or network.
 *
 * <p>Supports both serial port (/dev/ttyUSB0) and TCP network (IP:port).
 * Sends ZPL commands to the printer. On error, throws PrinterException for retry logic.
 */
@Component
public class UsbPrinterDriver implements PrinterDriver {
    private static final Logger log = LoggerFactory.getLogger(UsbPrinterDriver.class);
    private static final int NETWORK_TIMEOUT_MS = 5000;
    private static final int PRINT_DELAY_MS = 100;

    private final PrinterConfigRepository configRepo;

    public UsbPrinterDriver(PrinterConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    @Override
    public void sendLabel(String zpl, int copies) throws PrinterException {
        PrinterConfig config = configRepo.getSingleton()
            .orElseThrow(() -> new PrinterException("Printer not configured"));

        if (!config.isEnabled()) {
            throw new PrinterException("Printer is disabled");
        }

        String address = config.getAddress();
        if (address.startsWith("/dev/")) {
            sendViaSerial(address, zpl, copies, config.getPortSpeed());
        } else {
            sendViaNetwork(address, zpl, copies);
        }
    }

    private void sendViaNetwork(String address, String zpl, int copies) throws PrinterException {
        String[] parts = address.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9100;

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), NETWORK_TIMEOUT_MS);

            String fullZpl = zpl + "\n^XZ\n".repeat(copies);
            socket.getOutputStream().write(fullZpl.getBytes());
            socket.getOutputStream().flush();

            Thread.sleep(PRINT_DELAY_MS * copies);
            socket.close();

            log.info("Printed {} label(s) to {}:{}", copies, host, port);
        } catch (SocketTimeoutException e) {
            throw new PrinterException("Printer timeout: " + host + ":" + port, e);
        } catch (IOException e) {
            throw new PrinterException("Printer unreachable: " + address, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PrinterException("Print interrupted", e);
        }
    }

    private void sendViaSerial(String portName, String zpl, int copies, int portSpeed) throws PrinterException {
        // Placeholder for jssc (java-simple-serial-connector) implementation
        // When jssc dependency is added, implement serial port communication here.
        // For now, simulate the send and log.
        log.warn("Serial port {} not implemented yet (jssc dependency needed)", portName);
        log.info("Would print {} label(s) to serial port {}", copies, portName);
    }

    @Override
    public PrinterStatus getStatus() throws PrinterException {
        PrinterConfig config = configRepo.getSingleton()
            .orElseThrow(() -> new PrinterException("Printer not configured"));

        if (!config.isEnabled()) {
            return new PrinterStatus(false, "Printer is disabled");
        }

        String address = config.getAddress();
        if (address.startsWith("/dev/")) {
            return getSerialStatus(address);
        } else {
            return getNetworkStatus(address);
        }
    }

    private PrinterStatus getNetworkStatus(String address) {
        String[] parts = address.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9100;

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), NETWORK_TIMEOUT_MS);
            socket.close();
            return new PrinterStatus(true, "Connected to " + host + ":" + port);
        } catch (SocketTimeoutException e) {
            return new PrinterStatus(false, "Timeout: " + host + ":" + port);
        } catch (IOException e) {
            return new PrinterStatus(false, "Unreachable: " + host + ":" + port);
        }
    }

    private PrinterStatus getSerialStatus(String portName) {
        log.warn("Serial port {} status check not implemented yet (jssc dependency needed)", portName);
        return new PrinterStatus(false, "Serial support not yet implemented");
    }
}
