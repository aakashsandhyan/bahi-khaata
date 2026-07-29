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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
import javax.print.attribute.standard.PrinterIsAcceptingJobs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Printer driver for a TSC label printer, reached one of three ways depending on how the address is
 * written: a network address ({@code host:port}), a Linux serial device ({@code /dev/ttyUSB0} — not
 * yet implemented, needs a serial library), or — the common case, including a USB-connected printer
 * on Windows — the name the operating system gave the printer when it was installed, e.g.
 * {@code "TSC TE244"}.
 *
 * <p>A USB label printer is not a raw port Java can open directly: Windows installs it as a named
 * print queue (as does CUPS on macOS/Linux), and only the OS talks to the USB device. Reaching it
 * means going through the OS print system ({@code javax.print}), built into the JDK — no serial
 * library, no native dependency. The label is sent as a raw byte stream, not a document, which is
 * what keeps the spooler from trying to typeset the ZPL as text instead of passing it straight to
 * the printer, where a printer language needs it to land unchanged.
 */
@Component
public class UsbPrinterDriver implements PrinterDriver {
    private static final Logger log = LoggerFactory.getLogger(UsbPrinterDriver.class);
    private static final int NETWORK_TIMEOUT_MS = 5000;

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

        // Each rendered label is already a complete ^XA...^XZ document, so N copies means N whole
        // documents, not N bare ^XZ commands appended after one — a ^XZ with no matching ^XA does
        // not print anything, it only sits there.
        String job = zpl.repeat(Math.max(1, copies));

        String address = config.getAddress();
        if (address.startsWith("/dev/")) {
            sendViaSerial(address, zpl, copies, config.getPortSpeed());
        } else if (address.contains(":")) {
            sendViaNetwork(address, job);
        } else {
            sendViaPrintService(address, job);
        }
    }

    private void sendViaNetwork(String address, String job) throws PrinterException {
        String[] parts = address.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9100;

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), NETWORK_TIMEOUT_MS);
            socket.getOutputStream().write(job.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            socket.close();
            log.info("Printed to {}:{}", host, port);
        } catch (SocketTimeoutException e) {
            throw new PrinterException("Printer timeout: " + host + ":" + port, e);
        } catch (IOException e) {
            throw new PrinterException("Printer unreachable: " + address, e);
        } catch (IllegalArgumentException e) {
            // InetSocketAddress rejects a port outside 0-65535.
            throw new PrinterException("Invalid port number: " + address, e);
        }
    }

    /**
     * Sends the label to an OS-registered print queue — a Windows printer name such as
     * {@code "TSC TE244"}, or the local name CUPS gives a USB printer on macOS/Linux. The raw
     * byte-array flavour ({@link DocFlavor.BYTE_ARRAY#AUTOSENSE}) is what keeps the spooler from
     * reformatting the bytes as a text document; on Windows this is the documented way to get
     * {@code javax.print} to hand the job to WinSpool with a raw datatype, so the ZPL reaches the
     * printer exactly as generated.
     */
    private void sendViaPrintService(String printerName, String job) throws PrinterException {
        PrintService service = findPrintService(printerName)
            .orElseThrow(() -> new PrinterException(
                "Printer not found: \"" + printerName + "\" is not an installed printer"));

        Doc doc = new SimpleDoc(job.getBytes(StandardCharsets.US_ASCII), DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        DocPrintJob printJob = service.createPrintJob();
        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();

        try {
            printJob.print(doc, attrs);
            log.info("Printed to \"{}\"", printerName);
        } catch (PrintException e) {
            throw new PrinterException(
                "Printer unreachable: \"" + printerName + "\" (" + e.getMessage() + ")", e);
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
        } else if (address.contains(":")) {
            return getNetworkStatus(address);
        } else {
            return getPrintServiceStatus(address);
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
        } catch (IllegalArgumentException e) {
            return new PrinterStatus(false, "Invalid port number: " + address);
        }
    }

    private PrinterStatus getPrintServiceStatus(String printerName) {
        return findPrintService(printerName)
            .map(service -> {
                Object accepting = service.getAttribute(PrinterIsAcceptingJobs.class);
                String note = PrinterIsAcceptingJobs.NOT_ACCEPTING_JOBS.equals(accepting)
                    ? " (not accepting jobs — check it is powered on and online)"
                    : "";
                return new PrinterStatus(true, "Installed as \"" + service.getName() + "\"" + note);
            })
            .orElseGet(() -> new PrinterStatus(
                false, "No installed printer named \"" + printerName + "\""));
    }

    private PrinterStatus getSerialStatus(String portName) {
        log.warn("Serial port {} status check not implemented yet (jssc dependency needed)", portName);
        return new PrinterStatus(false, "Serial support not yet implemented");
    }

    /** The OS-registered print queue matching this name, case-insensitively, if one is installed. */
    private static Optional<PrintService> findPrintService(String printerName) {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
            .filter(s -> s.getName().equalsIgnoreCase(printerName))
            .findFirst();
    }
}
