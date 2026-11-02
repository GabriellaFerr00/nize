"""Fails if tracked files contain a valid CPF or a Luhn-valid card number (fixtures, seeds, tests).

Synthetic data only: the Nize stores neither CPF nor card numbers (cards are labels).
Usage: python3 -I scripts/ci/check_pii.py   (run from the repository root)
"""
import re
import subprocess
import sys

SKIP = re.compile(r"(package-lock\.json|mvnw(\.cmd)?|\.(png|jpg|jpeg|gif|ico|svg|woff2?|jar))$")
CPF = re.compile(r"(?<!\d)(\d{3}\.?\d{3}\.?\d{3}-?\d{2})(?!\d)")
CARD = re.compile(r"(?<![\d.])((?:\d[ -]?){12,18}\d)(?![\d.])")


def cpf_valid(raw: str) -> bool:
    d = [int(c) for c in raw if c.isdigit()]
    if len(d) != 11 or len(set(d)) == 1:
        return False
    for size in (9, 10):
        total = sum(v * w for v, w in zip(d[:size], range(size + 1, 1, -1)))
        if (total * 10 % 11) % 10 != d[size]:
            return False
    return True


def luhn_valid(raw: str) -> bool:
    d = [int(c) for c in raw if c.isdigit()]
    # Real card numbers start with 2-6 (Mastercard, Amex, Visa, Elo, Hipercard...);
    # this skips large money values in cents such as 9000000000001.
    if not 13 <= len(d) <= 19 or len(set(d)) == 1 or d[0] not in (2, 3, 4, 5, 6):
        return False
    total = 0
    for i, v in enumerate(reversed(d)):
        if i % 2:
            v = v * 2 - 9 if v > 4 else v * 2
        total += v
    return total % 10 == 0


def main() -> int:
    files = subprocess.run(["git", "ls-files"], capture_output=True, text=True, check=True).stdout.split()
    findings = []
    for path in files:
        if SKIP.search(path):
            continue
        try:
            text = open(path, encoding="utf-8").read()
        except (UnicodeDecodeError, FileNotFoundError, IsADirectoryError):
            continue
        for n, line in enumerate(text.splitlines(), 1):
            for m in CPF.finditer(line):
                if cpf_valid(m.group(1)):
                    findings.append(f"{path}:{n}: looks like a valid CPF")
            for m in CARD.finditer(line):
                if luhn_valid(m.group(1)):
                    findings.append(f"{path}:{n}: looks like a card number (Luhn)")
    for f in findings:
        print(f"::error::{f}")
    print(f"PII check: {len(findings)} finding(s) in {len(files)} tracked files")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
