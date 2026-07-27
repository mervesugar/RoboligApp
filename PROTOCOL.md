# PROTOCOL.md

# Robolig Communication Protocol

Version: 1.0

---

# Overview

This document defines the complete communication protocol between the Android Controller and the robot.

Communication path

```
Android App
    │
USB Serial
    │
Deneyap Mini v2
    │
NRF24
    │
Robot
```

The Deneyap Mini **must not process packets**.

It acts only as a transparent bridge.

Every byte received from USB is immediately transmitted through NRF24.

Every byte received from NRF24 is immediately forwarded over USB.

---

# Design Goals

The protocol is designed for

- Low latency
- High reliability
- Small packet size
- Fast parsing
- Simple implementation
- Error detection
- Future extensibility

---

# Communication Rates

| Communication | Frequency |
|--------------|-----------|
| Vehicle Control | 60 Hz |
| Arm Control | 30 Hz |
| PTZ Control | 30 Hz |
| Telemetry Request | 10 Hz |
| Heartbeat | Every 500 ms |

---

# Packet Size

Every packet is

```
32 bytes
```

No packet is ever larger.

No packet is ever smaller.

This keeps compatibility with NRF24.

---

# Packet Structure

| Byte | Name | Size |
|------|------|------|
|0|Header|1|
|1|Packet Type|1|
|2|Sequence Number|1|
|3|Flags|1|
|4-27|Payload|24|
|28-30|Timestamp|3|
|31|Checksum|1|

---

# Header

Always

```
0xAA
```

Reject every packet with an invalid header.

---

# Packet Types

| Type | Value |
|------|-------|
|Vehicle Control|0x01|
|Arm Control|0x02|
|PTZ Control|0x03|
|Telemetry Request|0x04|
|Telemetry Response|0x05|
|Emergency Stop|0x0E|
|Heartbeat|0x0F|

Unknown packet types must be ignored.

---

# Sequence Number

8-bit unsigned integer.

Increment for every outgoing packet.

```
0
1
2
...
255
0
```

Used to

- detect packet loss
- detect duplicates
- measure latency

---

# Flags

Bit layout

```
Bit 0

Emergency Stop

Bit 1

Precision Mode

Bit 2

Arm Locked

Bit 3

Vehicle Locked

Bit 4

Auto Mode

Bit 5

Reserved

Bit 6

Reserved

Bit 7

Reserved
```

---

# Timestamp

3-byte unsigned integer.

Milliseconds since application startup.

Wraparound is acceptable.

Used for

Latency

Synchronization

Debugging

---

# Checksum

Checksum is XOR.

Algorithm

```
checksum = byte0

XOR byte1

XOR byte2

...

XOR byte30
```

Checksum is stored in byte 31.

Reject invalid packets immediately.

---

# Vehicle Control Packet

Packet Type

```
0x01
```

Payload

| Byte | Description |
|------|-------------|
|4|Move X|
|5|Move Y|
|6|Rotation|
|7|Throttle|
|8|Brake|
|9|Boost|
|10-27|Reserved|

Values

```
-127

...

0

...

127
```

---

# Arm Control Packet

Packet Type

```
0x02
```

Payload

| Byte | Description |
|------|-------------|
|4|Shoulder|
|5|Elbow|
|6|Wrist Pitch|
|7|Wrist Roll|
|8|Gripper Rotation|
|9|Gripper|
|10-27|Reserved|

Servo values

```
0

...

180
```

Gripper

```
0

Closed

255

Open
```

---

# PTZ Packet

Packet Type

```
0x03
```

Payload

| Byte | Description |
|------|-------------|
|4|Pan|
|5|Tilt|
|6|Zoom|
|7-27|Reserved|

---

# Telemetry Request

Packet Type

```
0x04
```

Payload unused.

Robot immediately responds.

---

# Telemetry Response

Packet Type

```
0x05
```

Payload

| Byte | Description |
|------|-------------|
|4|Battery|
|5|Signal|
|6|Current Speed|
|7|Temperature|
|8|Current Mode|
|9|Error Code|
|10|Motor Current|
|11|Arm Current|
|12|CPU Load|
|13-27|Reserved|

---

# Emergency Stop

Packet Type

```
0x0E
```

Immediately

Stop motors

Disable arm motion

Disable autonomous tasks

Robot must acknowledge.

---

# Heartbeat

Packet Type

```
0x0F
```

Contains no payload.

Robot responds immediately.

---

# Robot Modes

| Mode | Value |
|------|-------|
|Drive|0|
|Gripper|1|
|Zipline|2|
|Auto|3|

---

# Connection State Machine

```
Disconnected

↓

USB Connected

↓

Serial Open

↓

Heartbeat Running

↓

Connected
```

Disconnect

↓

Reconnect

↓

Heartbeat

↓

Connected

---

# Watchdog

If no valid packet received within

```
2000 ms
```

Robot

Stops

Locks movement

Waits for reconnect

---

# Packet Validation

Incoming packets are checked in this order

Header

↓

Length

↓

Checksum

↓

Packet Type

↓

Sequence

↓

Payload

Only then

Update RobotState

---

# RobotState Update Flow

```
USB

↓

Packet Decoder

↓

Checksum

↓

Parser

↓

Repository

↓

RobotState

↓

ViewModel

↓

Compose UI
```

---

# Control Pipeline

```
Joystick

↓

ViewModel

↓

Repository

↓

Packet Builder

↓

USB Queue

↓

Serial

↓

NRF24

↓

Robot
```

---

# Queue Rules

Outgoing queue

FIFO

Incoming queue

FIFO

Emergency Stop

Highest priority

Heartbeat

Second priority

Telemetry

Lowest priority

---

# Error Codes

| Code | Description |
|------|-------------|
|0|No Error|
|1|Low Battery|
|2|Motor Fault|
|3|Arm Fault|
|4|Communication Fault|
|5|Emergency Stop|
|6|Watchdog Triggered|
|7|Unknown Error|

---

# Safety Rules

If USB disconnects

Immediately stop robot.

If heartbeat timeout

Immediately stop robot.

If checksum fails

Discard packet.

If packet type invalid

Discard packet.

If header invalid

Discard packet.

If packet size invalid

Discard packet.

---

# Future Reserved Packet Types

| Type | Purpose |
|------|---------|
|0x10|Firmware Update|
|0x11|Calibration|
|0x12|Debug|
|0x13|Configuration|
|0x14|Camera Control|
|0x15|Path Upload|
|0x16|Mission Download|
|0x17|Sensor Stream|

---

# Version Compatibility

Protocol Version

```
1.0
```

Future versions should remain backward compatible whenever possible.

Reserved bytes should never be repurposed without incrementing the protocol version.

---

# RFID Configuration Protocol

This section defines the dynamic RFID Configuration protocol (0x20 - 0x24) used to transmit the 5 fixed city UID records from the Android tablet to the robot.

## Packet Types

| Type Name | Value | Description |
|-----------|-------|-------------|
| RFID_CONFIG_BEGIN | 0x20 | Initiates an RFID configuration synchronization session |
| RFID_CONFIG_ITEM | 0x21 | Transmits a single city RFID UID record (Index 0..4) |
| RFID_CONFIG_COMMIT | 0x22 | Commits the 5 received RFID records and activates the table |
| RFID_CONFIG_ACK | 0x23 | Acknowledges successful receipt of BEGIN, ITEM, or COMMIT |
| RFID_CONFIG_NACK | 0x24 | Signals an error during configuration synchronization |

---

## Fixed City Codes

| City Code | City Name | Record Index |
|-----------|-----------|--------------|
| 1 | SINOP | 0 |
| 2 | NIGDE | 1 |
| 3 | TOKAT | 2 |
| 4 | AYDIN | 3 |
| 5 | ELAZIG | 4 |

---

## 1. RFID_CONFIG_BEGIN (0x20)

Payload structure (24 bytes):

| Payload Offset | Field Name | Size | Byte Order | Description |
|----------------|------------|------|------------|-------------|
| 0 | Protocol Version | 1 byte | - | Protocol version (0x01) |
| 1 | Session ID | 1 byte | - | Session identifier (1..255) |
| 2 | Table Version | 1 byte | - | Table version number (0x01) |
| 3 | Expected Record Count | 1 byte | - | Expected record count (fixed: 5) |
| 4-7 | Table Checksum | 4 bytes | Big-Endian | Deterministic CRC32 checksum |
| 8-23 | Reserved | 16 bytes | - | Reserved (0x00) |

---

## 2. RFID_CONFIG_ITEM (0x21)

Payload structure (24 bytes):

| Payload Offset | Field Name | Size | Byte Order | Description |
|----------------|------------|------|------------|-------------|
| 0 | Session ID | 1 byte | - | Active session identifier |
| 1 | Table Version | 1 byte | - | Table version number |
| 2 | Record Index | 1 byte | - | Record index (0 to 4) |
| 3 | City Code | 1 byte | - | City code (1=SINOP, 2=NIGDE, 3=TOKAT, 4=AYDIN, 5=ELAZIG) |
| 4 | UID Length | 1 byte | - | Raw UID length (4, 7, or 10 bytes) |
| 5-14 | Raw UID Bytes | 10 bytes | - | Raw UID bytes (padded with 0x00 if length < 10) |
| 15 | Enabled Flag | 1 byte | - | Enabled status (1 = enabled) |
| 16-23 | Reserved | 8 bytes | - | Reserved (0x00) |

---

## 3. RFID_CONFIG_COMMIT (0x22)

Payload structure (24 bytes):

| Payload Offset | Field Name | Size | Byte Order | Description |
|----------------|------------|------|------------|-------------|
| 0 | Session ID | 1 byte | - | Active session identifier |
| 1 | Table Version | 1 byte | - | Table version number |
| 2 | Record Count | 1 byte | - | Total record count (fixed: 5) |
| 3-6 | Table Checksum | 4 bytes | Big-Endian | Deterministic CRC32 checksum |
| 7-23 | Reserved | 17 bytes | - | Reserved (0x00) |

---

## 4. RFID_CONFIG_ACK (0x23)

Payload structure (24 bytes):

| Payload Offset | Field Name | Size | Byte Order | Description |
|----------------|------------|------|------------|-------------|
| 0 | Session ID | 1 byte | - | Active session identifier |
| 1 | Acknowledged Packet Type | 1 byte | - | Acknowledged packet type (0x20, 0x21, 0x22) |
| 2 | Record Index | 1 byte | - | Record index (0..4 for ITEM, 0xFF for BEGIN/COMMIT) |
| 3 | Status Code | 1 byte | - | Status code (0 = NONE) |
| 4 | Received Record Count | 1 byte | - | Record count received by robot |
| 5 | Active Table Version | 1 byte | - | Active table version on robot |
| 6-9 | Active Table Checksum | 4 bytes | Big-Endian | Active table CRC32 checksum |
| 10-23 | Reserved | 14 bytes | - | Reserved (0x00) |

---

## 5. RFID_CONFIG_NACK (0x24)

Payload structure (24 bytes):

| Payload Offset | Field Name | Size | Byte Order | Description |
|----------------|------------|------|------------|-------------|
| 0 | Session ID | 1 byte | - | Active session identifier |
| 1 | Acknowledged Packet Type | 1 byte | - | Packet type that failed |
| 2 | Record Index | 1 byte | - | Record index (0..4 for ITEM, 0xFF for BEGIN/COMMIT) |
| 3 | Error Code | 1 byte | - | Error code (1..11) |
| 4 | Received Record Count | 1 byte | - | Record count received by robot |
| 5 | Active Table Version | 1 byte | - | Active table version on robot |
| 6-9 | Active Table Checksum | 4 bytes | Big-Endian | Active table CRC32 checksum |
| 10-23 | Reserved | 14 bytes | - | Reserved (0x00) |

---

## NACK Error Codes

| Code | Name | Description |
|------|------|-------------|
| 0 | NONE | No Error |
| 1 | INVALID_UID | Invalid UID format or bytes |
| 2 | INVALID_UID_LENGTH | Unsupported UID length |
| 3 | DUPLICATE_UID | Duplicate UID detected in table |
| 4 | INVALID_CITY | Invalid city code |
| 5 | RECORD_COUNT_MISMATCH | Record count does not match expected (5) |
| 6 | SESSION_MISMATCH | Session ID mismatch |
| 7 | TABLE_CHECKSUM_MISMATCH | CRC32 checksum mismatch |
| 8 | STORAGE_ERROR | Robot internal storage error |
| 9 | BUSY | Robot is busy |
| 10 | UNSUPPORTED_VERSION | Protocol version unsupported |
| 11 | TIMEOUT | Synchronization timeout |

---

## Deterministic Table Checksum (CRC32)

Table checksum is calculated deterministically over the 5 records in fixed order:
1. Record Index 0: SINOP (City Code 1)
2. Record Index 1: NIGDE (City Code 2)
3. Record Index 2: TOKAT (City Code 3)
4. Record Index 3: AYDIN (City Code 4)
5. Record Index 4: ELAZIG (City Code 5)

For each record:
- Record Index (1 byte)
- City Code (1 byte)
- UID Length (1 byte)
- Raw UID Bytes (10 bytes, padded with 0x00)
- Enabled Flag (1 byte = 1)

Algorithm: Standard CRC32 (`java.util.zip.CRC32`).
Checksum representation in 4-byte payload: **Big-Endian (Network Byte Order)**.

---

## Synchronization State Machine Workflow

1. Tablet validates 5 records and generates Session ID, Table Version, and CRC32 Table Checksum.
2. Tablet sends `BEGIN` (0x20) and waits for `BEGIN ACK`.
3. Tablet sends `ITEM 0` (Sinop) and waits for `ITEM 0 ACK`.
4. Tablet sends `ITEM 1` (Niğde) and waits for `ITEM 1 ACK`.
5. Tablet sends `ITEM 2` (Tokat) and waits for `ITEM 2 ACK`.
6. Tablet sends `ITEM 3` (Aydın) and waits for `ITEM 3 ACK`.
7. Tablet sends `ITEM 4` (Elazığ) and waits for `ITEM 4 ACK`.
8. Tablet sends `COMMIT` (0x22) and waits for `COMMIT ACK`.
9. **ONLY AFTER COMMIT ACK** is received, the tablet transitions to `Success` and displays confirmation.

### Timeout and Retries

- **Timeout:** 1000 ms per attempt.
- **Max Retries:** 3 attempts max per packet.
- **Priority:** `PacketPriority.STANDARD` (Priority order = 2).
