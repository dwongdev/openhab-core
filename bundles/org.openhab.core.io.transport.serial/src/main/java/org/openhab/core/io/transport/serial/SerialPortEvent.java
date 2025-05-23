/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.io.transport.serial;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Interface for a serial port event.
 *
 * @author Markus Rathgeb - Initial contribution
 */
@NonNullByDefault
public interface SerialPortEvent {
    int DATA_AVAILABLE = 1;
    int OUTPUT_BUFFER_EMPTY = 2;
    int CTS = 3;
    int DSR = 4;
    int RI = 5;
    int CD = 6;
    int OE = 7;
    int PE = 8;
    int FE = 9;
    int BI = 10;
    int PORT_DISCONNECTED = 11;

    /**
     * Get the type of the event.
     *
     * @return the event type
     */
    int getEventType();

    /**
     * Gets the new value of the state change that caused the SerialPortEvent to be propagated. For example, when the CD
     * bit changes, newValue reflects the new value of the CD bit.
     */
    boolean getNewValue();
}
