"""Drive the debug app on a running emulator through every screen with sample
data and save screenshots to docs/screens.

Usage: python android/capture_screens.py
"""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from datetime import date, timedelta
from pathlib import Path

ADB = str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe")
PKG = "com.anish1050.ledger"
OUT = Path(__file__).resolve().parent.parent / "docs" / "screens"
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args: str, binary: bool = False):
    result = subprocess.run([ADB, *args], capture_output=True, check=False)
    return result.stdout if binary else result.stdout.decode("utf-8", "replace")


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    root = ET.fromstring(adb("shell", "cat", "/sdcard/ui.xml"))
    for n in root.iter("node"):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds", "[0,0][0,0]")))
        yield n.get("text", ""), ((x1 + x2) // 2, (y1 + y2) // 2)


def find(text: str, timeout: float = 30):
    deadline = time.time() + timeout
    while time.time() < deadline:
        for t, xy in nodes():
            if t.lower() == text.lower():
                return xy
        time.sleep(0.8)
    raise TimeoutError(f"not on screen: {text!r}")


def tap(text: str, then: str | None = None):
    """Tap a label; if `then` is given, retry until that label appears (the
    first frames after launch can swallow taps on a slow emulator)."""
    for _ in range(4):
        x, y = find(text)
        adb("shell", "input", "tap", str(x), str(y))
        if then is None:
            return
        try:
            find(then, timeout=8)
            return
        except TimeoutError:
            continue
    raise TimeoutError(f"{text!r} never led to {then!r}")


def shot(name: str):
    time.sleep(1.2)
    (OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    print("captured", name)


def keys(amount: str):
    for digit in amount:
        tap(digit)


def add(amount: str, emoji: str, day: date, income: bool = False):
    tap("+", then="✓")
    if income:
        tap("income")
    tap(str(day.day))
    tap(emoji)
    keys(amount)
    tap("✓")
    find("this month")


def main():
    adb("shell", "pm", "clear", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    find("Skip, use offline", timeout=60)
    shot("01-onboarding")

    tap("Skip, use offline", then="Set your budget")
    for label, value in (("Weekly", "2500"), ("Monthly", "10000"), ("Yearly", "120000")):
        tap(label)
        adb("shell", "input", "text", value)
    shot("02-budgets")
    tap("Next", then="Capture automatically")
    shot("03-permissions")
    tap("Done", then="this month")

    today = date.today()
    y1, y2 = today - timedelta(days=1), today - timedelta(days=2)
    add("1240", "🍔", today)
    add("450", "🚕", today)
    add("2850", "🛒", y1)
    add("649", "📺", y1)
    add("3999", "🛍️", y2)
    add("1200", "🎬", y2)
    add("85000", "💡", y2, income=True)
    # salary sits off-screen in the category row; relabel the one income row.
    adb("shell", f"run-as {PKG} sqlite3 databases/ledger.db \"update transactions set category_id='00000000-0000-4000-8000-000000000112', merchant='salary' where direction='credit'\"")
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    find("this month", timeout=60)
    shot("04-home")

    tap("+", then="✓")
    tap("🍔")
    keys("249")
    shot("05-add")
    adb("shell", "input", "keyevent", "4")
    find("this month")

    width, height = map(int, re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size")).groups())
    nav_y = height * 94 // 100
    adb("shell", "input", "tap", str(width * 61 // 100), str(nav_y))
    find("total")
    shot("06-report")
    tap("income")
    shot("07-report-income")
    adb("shell", "input", "tap", str(width * 84 // 100), str(nav_y))
    find("settings")
    shot("08-settings")


if __name__ == "__main__":
    main()
