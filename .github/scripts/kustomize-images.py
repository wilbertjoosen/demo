#!/usr/bin/env python3
"""Upsert / read the `images:` block of a Kustomization file.

Used by .github/workflows/ci-cd.yml so QA image tags live only in
k8s/demo-qa/kustomization.yaml (testing-only) — k8s/demo/*.yaml stay byte-identical
on every branch, so they never produce a merge conflict.

  set <kustomization.yaml> <image-name> <new-tag>   # add or update one entry
  get <kustomization.yaml> <image-name>             # print its newTag (empty if absent)

Deliberately hand-rolled text editing rather than a YAML round-trip: it keeps the
rest of the file (comments, key order, the resources list) byte-for-byte intact,
which matters for a file humans also edit and review.
"""
import re
import sys


def _read(path):
    with open(path) as f:
        return f.read()


def get(path, name):
    txt = _read(path)
    m = re.search(
        r"^images:\n((?:[ \t]*-[ \t]*name:.*\n(?:[ \t]+\w+:.*\n)*)+)",
        txt,
        re.MULTILINE,
    )
    if not m:
        return ""
    block = m.group(1)
    for entry in re.finditer(
        r"-[ \t]*name:[ \t]*(\S+)\n((?:[ \t]+\w+:.*\n)*)", block
    ):
        if entry.group(1) == name:
            t = re.search(r"newTag:[ \t]*(\S+)", entry.group(2))
            return t.group(1) if t else ""
    return ""


def set_(path, name, tag):
    txt = _read(path)
    entry = f"  - name: {name}\n    newTag: {tag}\n"

    m = re.search(
        r"^images:\n((?:[ \t]*-[ \t]*name:.*\n(?:[ \t]+\w+:.*\n)*)+)",
        txt,
        re.MULTILINE,
    )
    if not m:
        # No images: block yet — append one at EOF.
        if not txt.endswith("\n"):
            txt += "\n"
        txt += "\nimages:\n" + entry
        with open(path, "w") as f:
            f.write(txt)
        return

    block = m.group(1)
    replaced, out = False, []
    for e in re.finditer(r"([ \t]*-[ \t]*name:[ \t]*(\S+)\n(?:[ \t]+\w+:.*\n)*)", block):
        if e.group(2) == name:
            out.append(entry)
            replaced = True
        else:
            out.append(e.group(1))
    if not replaced:
        out.append(entry)
    new_block = "".join(out)
    txt = txt[: m.start(1)] + new_block + txt[m.end(1):]
    with open(path, "w") as f:
        f.write(txt)


if __name__ == "__main__":
    op = sys.argv[1]
    if op == "get":
        print(get(sys.argv[2], sys.argv[3]))
    elif op == "set":
        set_(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        sys.exit(f"unknown op: {op}")
