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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ESC/POS receipt driver. The transport is chosen by {@link ReceiptPrinterConfig#getTransport()}:
 * {@code LAN} sends the bytes over a raw socket to {@code host:port} (default port 9100), {@code
 * USB} hands them to the OS print queue named by the address as a raw byte document — the same two
 * paths the label driver uses, but for bytes rather than TSPL text.
 */
@Service
public class EscPosReceiptDriver implements ReceiptPrinterDriver {

    private static final Logger log = LoggerFactory.getLogger(EscPosReceiptDriver.class);
    private static final int TIMEOUT_MS = 5000;
    private static final int DEFAULT_RAW_PORT = 9100;

    private final ReceiptPrinterConfigRepository configRepo;

    EscPosReceiptDriver(ReceiptPrinterConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    @Override
    public void printReceipt(byte[] escpos) throws PrinterDriver.PrinterException {
        ReceiptPrinterConfig config = configRepo.findById(ReceiptPrinterConfig.SINGLETON_ID)
                .orElseThrow(() -> new PrinterDriver.PrinterException("Receipt printer not configured"));
        if (!config.isEnabled()) {
            throw new PrinterDriver.PrinterException("Receipt printer is disabled");
        }
        String address = config.getAddress();
        if (address == null || address.isBlank()) {
            throw new PrinterDriver.PrinterException("Receipt printer address is not set");
        }
        if ("USB".equalsIgnoreCase(config.getTransport())) {
            sendViaPrintService(address, escpos);
        } else {
            sendViaNetwork(address, escpos);
        }
    }

    @Override
    public PrinterDriver.PrinterStatus test() {
        try {
            printReceipt(testSlip());
            return new PrinterDriver.PrinterStatus(true, "Receipt printer OK");
        } catch (PrinterDriver.PrinterException e) {
            return new PrinterDriver.PrinterStatus(false, e.getMessage());
        }
    }

    /** A minimal ESC/POS slip: reset, a line, feed, partial cut. */
    private static byte[] testSlip() {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(0x1B);
        out.write(0x40); // ESC @
        byte[] msg = "Receipt printer OK\n".getBytes(StandardCharsets.US_ASCII);
        out.write(msg, 0, msg.length);
        out.write(0x0A);
        out.write(0x0A);
        out.write(0x0A);
        out.write(0x1D);
        out.write(0x56);
        out.write(0x42);
        out.write(0x00); // GS V B 0 — partial cut
        return out.toByteArray();
    }

    private void sendViaNetwork(String address, byte[] bytes) throws PrinterDriver.PrinterException {
        String[] parts = address.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : DEFAULT_RAW_PORT;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            OutputStream os = socket.getOutputStream();
            os.write(bytes);
            os.flush();
            log.info("Printed receipt to {}:{}", host, port);
        } catch (SocketTimeoutException e) {
            throw new PrinterDriver.PrinterException("Receipt printer timeout: " + host + ":" + port, e);
        } catch (IOException e) {
            throw new PrinterDriver.PrinterException("Receipt printer unreachable: " + address, e);
        } catch (IllegalArgumentException e) {
            throw new PrinterDriver.PrinterException("Invalid receipt printer address: " + address, e);
        }
    }

    private void sendViaPrintService(String printerName, byte[] bytes)
            throws PrinterDriver.PrinterException {
        PrintService service = findPrintService(printerName)
                .orElseThrow(() -> new PrinterDriver.PrinterException(
                        "Receipt printer not found: \"" + printerName + "\" is not an installed printer"));
        Doc doc = new SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        DocPrintJob job = service.createPrintJob();
        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
        try {
            job.print(doc, attrs);
            log.info("Printed receipt to \"{}\"", printerName);
        } catch (PrintException e) {
            throw new PrinterDriver.PrinterException(
                    "Receipt printer unreachable: \"" + printerName + "\" (" + e.getMessage() + ")", e);
        }
    }

    private Optional<PrintService> findPrintService(String name) {
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (service.getName().equalsIgnoreCase(name)) {
                return Optional.of(service);
            }
        }
        return Optional.empty();
    }
}
