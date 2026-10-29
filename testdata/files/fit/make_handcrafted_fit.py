# SPDX-License-Identifier: AGPL-3.0-or-later
"""Regenerates handcrafted-mixed.fit, a FIT activity assembled byte by byte with the standard
library only (independently of lib/fit), to exercise decoder paths our own encoder never emits:

* protocol 2.0 header, developer_data_id / field_description messages and developer fields
* big-endian definition messages
* compressed-timestamp headers, including a 5-bit roll-over
* a field whose size is not a multiple of its base type, an unknown field and an unknown message
* invalid sentinel values and a redefined local message type

Usage: python make_handcrafted_fit.py [output-path]
"""
import struct
import sys
from pathlib import Path

FIT_EPOCH = 631065600
UNIX_START = 1778479200  # 2026-05-11T06:00:00Z
T0 = (UNIX_START - FIT_EPOCH) & ~0x1F | 30  # low 5 bits = 30 so compressed offsets roll over


def crc16_arc(data: bytes) -> int:
    """Bitwise CRC-16/ARC (poly 0xA001 reflected, init 0) - same CRC as FIT, different algorithm."""
    crc = 0
    for byte in data:
        crc ^= byte
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return crc


def semicircles(deg: float) -> int:
    return round(deg * 2**31 / 180)


def definition(local, global_num, fields, big_endian=False, dev_fields=()):
    """fields: (number, size, base_type); dev_fields: (number, size, developer_data_index)."""
    header = 0x40 | (0x20 if dev_fields else 0) | local
    out = bytes([header, 0, 1 if big_endian else 0])
    out += struct.pack(">H" if big_endian else "<H", global_num)
    out += bytes([len(fields)]) + b"".join(bytes(f) for f in fields)
    if dev_fields:
        out += bytes([len(dev_fields)]) + b"".join(bytes(f) for f in dev_fields)
    return out


def data(local, payload: bytes):
    return bytes([local]) + payload


def compressed(local, timestamp, payload: bytes):
    return bytes([0x80 | (local << 5) | (timestamp & 0x1F)]) + payload


def build() -> bytes:
    msgs = b""
    # file_id (local 0)
    msgs += definition(0, 0, [(0, 1, 0x00), (1, 2, 0x84), (2, 2, 0x84), (3, 4, 0x8C), (4, 4, 0x86), (8, 12, 0x07)])
    msgs += data(0, struct.pack("<BHHII", 4, 1, 3121, 123456789, T0) + b"Hand Watch\0\0")
    # developer_data_id (local 1): developer_data_index=0, application_id = 16 bytes
    msgs += definition(1, 207, [(3, 1, 0x02), (1, 16, 0x0D)])
    msgs += data(1, bytes([0]) + bytes(range(16)))
    # field_description (local 1 redefined): index, field number, base type, name, units
    msgs += definition(1, 206, [(0, 1, 0x02), (1, 1, 0x02), (2, 1, 0x02), (3, 16, 0x07), (8, 8, 0x07)])
    msgs += data(1, bytes([0, 0, 0x84]) + b"doughnuts".ljust(16, b"\0") + b"count".ljust(8, b"\0"))
    # event timer start (local 4)
    msgs += definition(4, 21, [(253, 4, 0x86), (0, 1, 0x00), (1, 1, 0x00)])
    msgs += data(4, struct.pack("<IBB", T0, 0, 0))
    # record, BIG-endian, with a 2-byte developer field (local 2)
    msgs += definition(2, 20, [(253, 4, 0x86), (0, 4, 0x85), (1, 4, 0x85), (78, 4, 0x86), (3, 1, 0x02),
                               (73, 4, 0x86), (7, 2, 0x84)], big_endian=True, dev_fields=[(0, 2, 0)])
    msgs += data(2, struct.pack(">IiiIBIH", T0, semicircles(46.5), semicircles(6.6), (372 + 500) * 5, 140,
                                2500, 180) + struct.pack(">H", 7))
    # record without timestamp for compressed headers (local 3, little-endian); field 5 distance,
    # 2 altitude, 6 speed, unknown field 250, and field 251 declared uint16 with size 3 (-> bytes).
    msgs += definition(3, 20, [(0, 4, 0x85), (1, 4, 0x85), (3, 1, 0x02), (5, 4, 0x86), (2, 2, 0x84),
                               (6, 2, 0x84), (250, 1, 0x02), (251, 3, 0x84)])
    rows = [
        (T0 + 1, 46.50001, 6.60002, 141, 250, 373.2, 2.6),
        (T0 + 2, 46.50002, 6.60004, 0xFF, 520, 373.4, 2.7),  # invalid heart rate; offset rolls over
        (T0 + 5, 46.50003, 6.60006, 143, 1275, 374.0, 2.75),
    ]
    for ts, lat, lon, hr, dist_cm, alt, speed in rows:
        payload = struct.pack("<iiBIHHB", semicircles(lat), semicircles(lon), hr, dist_cm,
                              round((alt + 500) * 5), round(speed * 1000), 42) + b"\x01\x02\x03"
        msgs += compressed(3, ts, payload)
    # unknown manufacturer-specific message 0xFF00 (local 5)
    msgs += definition(5, 0xFF00, [(0, 2, 0x84)])
    msgs += data(5, struct.pack("<H", 0xBEEF))
    # event timer stop_all, reusing local 4
    msgs += data(4, struct.pack("<IBB", T0 + 5, 0, 4))
    # lap (local 6): timestamp, start_time, elapsed, timer, distance, avg hr, trigger
    msgs += definition(6, 19, [(253, 4, 0x86), (2, 4, 0x86), (7, 4, 0x86), (8, 4, 0x86), (9, 4, 0x86),
                               (15, 1, 0x02), (24, 1, 0x00)])
    msgs += data(6, struct.pack("<IIIIIBB", T0 + 5, T0, 5000, 5000, 1275, 141, 7))
    # session (local 7): timestamp, start_time, sport, sub_sport, elapsed, timer, distance
    msgs += definition(7, 18, [(253, 4, 0x86), (2, 4, 0x86), (5, 1, 0x00), (6, 1, 0x00), (7, 4, 0x86),
                               (8, 4, 0x86), (9, 4, 0x86)])
    msgs += data(7, struct.pack("<IIBBIII", T0 + 5, T0, 1, 3, 5000, 5000, 1275))
    # activity (local 8): timestamp, timer, num_sessions, type, local_timestamp (UTC+2)
    msgs += definition(8, 34, [(253, 4, 0x86), (0, 4, 0x86), (1, 2, 0x84), (2, 1, 0x00), (5, 4, 0x86)])
    msgs += data(8, struct.pack("<IIHBI", T0 + 5, 5000, 1, 0, T0 + 5 + 7200))

    header = struct.pack("<BBHI4s", 14, 0x20, 2140, len(msgs), b".FIT")
    header += struct.pack("<H", crc16_arc(header))
    body = header + msgs
    return body + struct.pack("<H", crc16_arc(body))


if __name__ == "__main__":
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).with_name("handcrafted-mixed.fit")
    out.write_bytes(build())
    print(f"wrote {out} (T0={T0})")
