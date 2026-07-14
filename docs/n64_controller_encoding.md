# N64 Controller Input Encoding in Kaillera

This document outlines how Nintendo 64 (N64) controller inputs are encoded within Kaillera game data packets. It explains the packet layouts, header formats, and bitwise button/joystick structures for both standard and RAW data modes.

---

## 1. Overview of Packet Structure

A Kaillera game data packet consists of a **Header** followed by one or more **Controller Input Frames**. The format of the header determines whether the controller data is in Standard Mode or RAW Mode.

```
+------------------+-----------------------------+
|  Header Bytes    |  Controller Input Frame(s)  |
|  (Standard/RAW)  |  (Buttons & Joystick data)  |
+------------------+-----------------------------+
```

* **Byte 0 (Player Select):** Indicates player designation (typically `0x10`, `0x11`, etc.).
* **Byte 1 (Format Byte):** Identifies the format mode:
  * `0x20` indicates **Standard Mode**
  * `0x21` indicates **RAW Mode**

---

## 2. Encoding Formats

### A. Standard Mode (`0x20`)

In Standard Mode, the client uses a fixed 12-byte header. The first frame of controller input begins immediately at index 12 of the packet.

* **Header Size:** 12 bytes
* **First Frame Offset:** 12
* **Frame Size:** 4 bytes

```
Byte Offset:
 0      1                                  12
+------+------+---------------------------+-------------+
| PlayerSelect | 0x20 (Format) | ... (10 bytes padding) ... | Frame 1 ... |
+------+------+---------------------------+-------------+
```

### B. RAW Mode (`0x21`)

In RAW Mode (common in games communicating raw/unfiltered emulator packets like Smash 64), the client uses a shorter 7-byte header. The first frame of controller input begins immediately at index 7.

* **Header Size:** 7 bytes
* **First Frame Offset:** 7
* **Frame Size:** Variable (defined by the player's connection latency setting, e.g., 8 bytes or 24 bytes). However, the first frame is always positioned at index 7.

```
Byte Offset:
 0      1                          7
+------+------+-------------------+-------------+
| PlayerSelect | 0x21 (Format) | ... (5 bytes padding) ... | Frame 1 ... |
+------+------+-------------------+-------------+
```

---

## 3. Controller Input Frame Layout

Each controller input frame has a standard 4-byte layout that maps all N64 buttons and the analog joystick coordinates:

```
+-----------------------------------+
| Byte 0: Buttons High Byte         |
| Byte 1: Buttons Low Byte          |
| Byte 2: Joystick X Axis (Signed)  |
| Byte 3: Joystick Y Axis (Signed)  |
+-----------------------------------+
```

### A. Buttons High Byte (Byte 0)

| Bit | Hex Value | Button |
|:---:|:---------:|:------:|
| 7   | `0x80`    | A      |
| 6   | `0x40`    | B      |
| 5   | `0x20`    | Z      |
| 4   | `0x10`    | Start  |
| 3   | `0x08`    | D-Up   |
| 2   | `0x04`    | D-Down |
| 1   | `0x02`    | D-Left |
| 0   | `0x01`    | D-Right|

### B. Buttons Low Byte (Byte 1)

| Bit | Hex Value | Button |
|:---:|:---------:|:------:|
| 7   | `0x80`    | (Unused/Reserved) |
| 6   | `0x40`    | (Unused/Reserved) |
| 5   | `0x20`    | L      |
| 4   | `0x10`    | R      |
| 3   | `0x08`    | C-Up   |
| 2   | `0x04`    | C-Down |
| 1   | `0x02`    | C-Left |
| 0   | `0x01`    | C-Right|

### C. Joystick Coordinates (Bytes 2 & 3)

The analog joystick positions are mapped to Bytes 2 and 3 as signed 8-bit integers:

* **Byte 2 (Joystick X):** Represents the horizontal position.
  * Range: `-128` (Fully Left) to `127` (Fully Right). Neutral position is `0`.
* **Byte 3 (Joystick Y):** Represents the vertical position.
  * Range: `-128` (Fully Down) to `127` (Fully Up). Neutral position is `0`.
