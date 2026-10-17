#!/usr/bin/env python3
"""生成済みの金星構造物NBTを読み、外壁と屋根段差の閉鎖状態を検査する。

``generateVenusAssets`` の実行後、生成物のディレクトリを指定する。
    python tools/validate_structure_shells.py build/generated/venusAssets/data/venusmod/structure
"""

from __future__ import annotations

import gzip
import struct
import sys
from pathlib import Path


def read_utf(stream):
    length = struct.unpack(">H", stream.read(2))[0]
    return stream.read(length).decode("utf-8")


def read_payload(stream, tag_type):
    if tag_type == 1:
        return struct.unpack(">b", stream.read(1))[0]
    if tag_type == 2:
        return struct.unpack(">h", stream.read(2))[0]
    if tag_type == 3:
        return struct.unpack(">i", stream.read(4))[0]
    if tag_type == 4:
        return struct.unpack(">q", stream.read(8))[0]
    if tag_type == 5:
        return struct.unpack(">f", stream.read(4))[0]
    if tag_type == 6:
        return struct.unpack(">d", stream.read(8))[0]
    if tag_type == 7:
        length = struct.unpack(">i", stream.read(4))[0]
        return stream.read(length)
    if tag_type == 8:
        return read_utf(stream)
    if tag_type == 9:
        child_type, length = struct.unpack(">Bi", stream.read(5))
        return [read_payload(stream, child_type) for _ in range(length)]
    if tag_type == 10:
        result = {}
        while True:
            child_type = struct.unpack(">B", stream.read(1))[0]
            if child_type == 0:
                return result
            name = read_utf(stream)
            result[name] = read_payload(stream, child_type)
    if tag_type == 11:
        length = struct.unpack(">i", stream.read(4))[0]
        return list(struct.unpack(f">{length}i", stream.read(4 * length)))
    if tag_type == 12:
        length = struct.unpack(">i", stream.read(4))[0]
        return list(struct.unpack(f">{length}q", stream.read(8 * length)))
    raise ValueError(f"Unsupported NBT tag type: {tag_type}")


def load_structure(path):
    with gzip.open(path, "rb") as stream:
        root_type = struct.unpack(">B", stream.read(1))[0]
        if root_type != 10:
            raise ValueError(f"{path} is not a root compound")
        read_utf(stream)  # ルートタグ名。
        root = read_payload(stream, root_type)

    palette = [entry["Name"] for entry in root["palette"]]
    blocks = {}
    for block in root["blocks"]:
        x, y, z = block["pos"]
        blocks[(x, y, z)] = palette[block["state"]]
    return root["size"], blocks


class StructureCheck:
    def __init__(self, directory, name):
        self.name = name
        path = directory / f"{name}.nbt"
        self.size, self.blocks = load_structure(path)
        self.checked = 0

    def block(self, pos):
        return self.blocks.get(pos, "<missing>")

    def solid(self, positions, label):
        failures = []
        for pos in positions:
            block = self.block(pos)
            self.checked += 1
            if block == "<missing>" or block == "minecraft:air":
                failures.append((pos, block))
        if failures:
            sample = ", ".join(f"{pos}={block}" for pos, block in failures[:8])
            raise AssertionError(f"{self.name}: {label} has {len(failures)} holes: {sample}")

    def exact(self, expected, label):
        failures = []
        for pos, wanted in expected.items():
            actual = self.block(pos)
            self.checked += 1
            if actual != wanted:
                failures.append((pos, actual, wanted))
        if failures:
            sample = ", ".join(f"{pos}={actual} (expected {wanted})" for pos, actual, wanted in failures[:8])
            raise AssertionError(f"{self.name}: {label} changed: {sample}")


def positions(xs, ys, zs):
    return ((x, y, z) for x in xs for y in ys for z in zs)


def stairs(x0, x1, z_max, state):
    for z in range(z_max + 1):
        for x in range(x0, x1 + 1):
            yield (x, z, z), state


def check_citadel(directory):
    check = StructureCheck(directory, "venus_citadel")
    check.solid(positions((0, 30), range(4, 10), range(4, 51)), "lower side walls")
    check.solid(positions((0, 30), range(10, 13), range(33, 51)), "raised rear side walls")
    check.solid(positions(range(31), range(10, 12), (33,)), "roof step riser")

    openings = {(x, y, 4): "minecraft:air" for x in range(13, 18) for y in range(4, 8)}
    openings.update({(15, y, 32): "minecraft:iron_door" for y in (4, 5)})
    check.exact(openings, "front entrance and internal door")
    check.exact(dict(stairs(13, 17, 3, "minecraft:polished_blackstone_brick_stairs")), "entry stairs")
    return check


def check_general_keep(directory):
    check = StructureCheck(directory, "venus_general_keep")
    check.solid(positions((0, 30), range(4, 12), range(4, 51)), "lower side walls")
    check.solid(positions((0, 30), (12,), range(36, 50)), "raised rear side walls")
    check.solid(positions(range(31), (12,), (35,)), "roof step riser")

    openings = {(x, y, 4): "minecraft:air" for x in range(13, 18) for y in range(4, 8)}
    openings.update({(x, y, 34): "minecraft:air" for x in range(14, 17) for y in range(4, 8)})
    check.exact(openings, "front entrance and altar-chamber passage")
    check.exact(dict(stairs(13, 17, 3, "minecraft:deepslate_brick_stairs")), "entry stairs")
    return check


def check_reactor(directory):
    check = StructureCheck(directory, "aphrodite_reactor")
    check.solid(positions((0, 30), range(4, 14), range(4, 51)), "lower side walls")
    check.solid(positions((0, 30), (14,), range(33, 50)), "raised rear side walls")
    check.solid(positions(range(31), (14,), (32,)), "roof step riser")

    openings = {(x, y, 4): "minecraft:air" for x in range(13, 18) for y in range(4, 9)}
    openings.update({(x, y, 31): "minecraft:air" for x in range(13, 18) for y in range(4, 9)})
    check.exact(openings, "front entrance and reactor passage")
    check.exact(dict(stairs(13, 17, 3, "minecraft:polished_blackstone_brick_stairs")), "entry stairs")
    return check


def main():
    directory = Path(sys.argv[1]) if len(sys.argv) == 2 else Path(
        "build/generated/venusAssets/data/venusmod/structure"
    )
    checks = [
        check_citadel(directory),
        check_general_keep(directory),
        check_reactor(directory),
    ]
    for check in checks:
        print(f"{check.name}: {check.checked} generated block positions verified")
    print("All existing Venus structure shells and preserved entrances are complete.")


if __name__ == "__main__":
    main()
