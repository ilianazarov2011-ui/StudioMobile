#!/usr/bin/env python3
"""Generate the vector logo: tilted square with a phone-shaped hole."""
import math
import sys

CX = CY = 54.0
ANGLE = math.radians(12)


def rot(x, y):
    dx, dy = x - CX, y - CY
    c, s = math.cos(ANGLE), math.sin(ANGLE)
    return CX + dx * c - dy * s, CY + dx * s + dy * c


def rrect(cx, cy, w, h, r):
    x0, y0, x1, y1 = cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2
    def p(x, y):
        a, b = rot(x, y)
        return f"{a:.2f},{b:.2f}"
    return (f"M{p(x0 + r, y0)} L{p(x1 - r, y0)} Q{p(x1, y0)} {p(x1, y0 + r)} "
            f"L{p(x1, y1 - r)} Q{p(x1, y1)} {p(x1 - r, y1)} "
            f"L{p(x0 + r, y1)} Q{p(x0, y1)} {p(x0, y1 - r)} "
            f"L{p(x0, y0 + r)} Q{p(x0, y0)} {p(x0 + r, y0)} Z")


outer = rrect(CX, CY, 60, 60, 7)
phone = rrect(CX, CY, 24, 40, 5.5)
speaker = rrect(CX, CY - 14.5, 8, 2.2, 1.1)
bar = rrect(CX, CY + 15, 9, 2, 1)

print(outer)
print(phone)
print(speaker)
print(bar)
