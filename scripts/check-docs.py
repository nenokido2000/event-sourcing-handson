#!/usr/bin/env python3
"""docs/ の機械的に検出できる不整合をチェックする（ガード #4 / docs/plan.md）。

前提: 「決定的に検出できるものだけを見る」。意味の矛盾（決定Aと記述Bが噛み合わない等）は
対象外で、そちらは es-domain-reviewer とレビューの仕事。

チェック内容:
  1. ADR整合    … decisions.md の `## Hn` 見出しと一覧表の行が集合として一致するか
  2. アンカー   … `](file.md#anchor)` のリンク先見出しが実在するか
  3. 用語の波及 … ubiquitous-language.md で定義したポリシー番号が波及先文書に現れるか／
                  どこにも定義されていないポリシー番号を参照していないか

使い方: python3 scripts/check-docs.py    （終了コード 0=OK / 1=違反あり）
"""

import os
import re
import sys
from collections import Counter

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

# チェック対象。docs/ 配下・specs/ 配下（受入仕様も decisions.md を広く参照する）と、
# 決定を参照するルート文書。
TARGET_FILES = ["README.md", "CLAUDE.md"]
TARGET_DIRS = ["docs", "specs"]
TARGET_EXTS = (".md", ".spec")

DECISIONS = "docs/decisions.md"
GLOSSARY = "docs/ubiquitous-language.md"
# 新しいポリシーを定義したら、必ずここに列挙した文書にも現れているべき（波及漏れの検出）。
POLICY_MUST_APPEAR_IN = ["docs/context-map.md", "docs/tactical-design.md"]

errors = []


def rel(path):
    return os.path.relpath(path, ROOT)


def read(relpath):
    with open(os.path.join(ROOT, relpath), encoding="utf-8") as f:
        return f.read()


def collect_markdown():
    paths = []
    for name in TARGET_FILES:
        if os.path.exists(os.path.join(ROOT, name)):
            paths.append(name)
    for d in TARGET_DIRS:
        for dirpath, _, filenames in os.walk(os.path.join(ROOT, d)):
            for fn in sorted(filenames):
                if fn.endswith(TARGET_EXTS):
                    paths.append(rel(os.path.join(dirpath, fn)))
    return sorted(set(paths))


def scannable_lines(relpath):
    """(行番号, 本文) を返す。コードブロックは飛ばし、インラインコードは伏せる。

    リンクや採番の「書き方の説明」を文書に書くと、その例示自体が検出対象に見えてしまうため
    （例: `](file.md#anchor)` という説明）、コード表記の中身はチェックしない。
    """
    out = []
    in_fence = False
    for lineno, line in enumerate(read(relpath).splitlines(), start=1):
        if re.match(r"^\s*(```|~~~)", line):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        out.append((lineno, re.sub(r"`[^`]*`", "``", line)))
    return out


def slugify(heading):
    """GitHub のアンカー生成に合わせる: 装飾を落とす → 小文字化 → 記号除去 → 空白をハイフンへ。"""
    text = heading
    text = re.sub(r"`([^`]*)`", r"\1", text)
    text = re.sub(r"\*\*([^*]*)\*\*", r"\1", text)
    text = re.sub(r"\*([^*]*)\*", r"\1", text)
    text = re.sub(r"~~([^~]*)~~", r"\1", text)
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = text.strip().lower()
    kept = [c for c in text if c.isalnum() or c in " -_"]
    return "".join(kept).replace(" ", "-")


def anchors_of(relpath):
    """その文書が持つアンカーの集合（重複見出しは GitHub と同じく -1, -2 を付ける）。"""
    seen = Counter()
    result = set()
    for line in read(relpath).splitlines():
        m = re.match(r"^(#{1,6})\s+(.*?)\s*$", line)
        if not m:
            continue
        base = slugify(m.group(2))
        if not base:
            continue
        n = seen[base]
        seen[base] += 1
        result.add(base if n == 0 else "%s-%d" % (base, n))
    return result


# ---------------------------------------------------------------- 1. ADR整合
def check_adr_numbering():
    text = read(DECISIONS)

    headings = []
    for m in re.finditer(r"^## H(\d+)\s", text, re.MULTILINE):
        headings.append(int(m.group(1)))

    rows = []
    for m in re.finditer(r"^\|\s*\[H(\d+)\]\(#", text, re.MULTILINE):
        rows.append(int(m.group(1)))

    for label, nums in (("見出し", headings), ("一覧表", rows)):
        dupes = [n for n, c in Counter(nums).items() if c > 1]
        if dupes:
            errors.append(
                "%s: %s に H番号の重複があります: %s"
                % (DECISIONS, label, ", ".join("H%d" % n for n in sorted(dupes)))
            )

    only_heading = sorted(set(headings) - set(rows))
    only_row = sorted(set(rows) - set(headings))
    if only_heading:
        errors.append(
            "%s: 本文にあるが一覧表に無い ADR があります: %s（一覧表に行を足してください）"
            % (DECISIONS, ", ".join("H%d" % n for n in only_heading))
        )
    if only_row:
        errors.append(
            "%s: 一覧表にあるが本文に無い ADR があります: %s"
            % (DECISIONS, ", ".join("H%d" % n for n in only_row))
        )

    if headings:
        missing = sorted(set(range(1, max(headings) + 1)) - set(headings))
        if missing:
            errors.append(
                "%s: H番号が欠番です: %s（番号は振り直さない方針なので、欠番は消し忘れの疑い）"
                % (DECISIONS, ", ".join("H%d" % n for n in missing))
            )


# ------------------------------------------------------------- 2. アンカー
LINK_RE = re.compile(r"\]\(([^)\s]*?)#([^)\s]+)\)")


def check_anchors(paths):
    cache = {}
    for path in paths:
        base_dir = os.path.dirname(path)
        for lineno, line in scannable_lines(path):
            for m in LINK_RE.finditer(line):
                target, anchor = m.group(1), m.group(2)
                if target.startswith("http"):
                    continue
                dest = path if target == "" else os.path.normpath(os.path.join(base_dir, target))
                if not dest.endswith(".md"):
                    continue
                if not os.path.exists(os.path.join(ROOT, dest)):
                    errors.append("%s:%d リンク先の文書がありません: %s" % (path, lineno, dest))
                    continue
                if dest not in cache:
                    cache[dest] = anchors_of(dest)
                if anchor not in cache[dest]:
                    errors.append(
                        "%s:%d アンカーが解決しません: %s#%s（見出しの変更漏れの疑い）"
                        % (path, lineno, target or os.path.basename(path), anchor)
                    )


# --------------------------------------------------------- 3. 用語の波及
def check_policy_coverage(paths):
    glossary = read(GLOSSARY)
    defined = set(int(n) for n in re.findall(r"（P(\d+)）", glossary))
    if not defined:
        errors.append("%s: ポリシー定義（（Pn）表記）が見つかりません" % GLOSSARY)
        return

    for relpath in POLICY_MUST_APPEAR_IN:
        text = read(relpath)
        found = set(int(n) for n in re.findall(r"\bP(\d+)\b", text))
        missing = sorted(defined - found)
        if missing:
            errors.append(
                "%s: %s で定義済みのポリシーが記載されていません: %s（波及漏れ）"
                % (relpath, GLOSSARY, ", ".join("P%d" % n for n in missing))
            )

    for path in paths:
        for lineno, line in scannable_lines(path):
            for n in set(int(x) for x in re.findall(r"\bP(\d+)\b", line)):
                if n not in defined:
                    errors.append(
                        "%s:%d 未定義のポリシー番号を参照しています: P%d（%s に定義を足してください）"
                        % (path, lineno, n, GLOSSARY)
                    )


def main():
    paths = collect_markdown()
    check_adr_numbering()
    check_anchors(paths)
    check_policy_coverage(paths)

    if not errors:
        print("✅ check-docs: %d 文書、不整合なし" % len(paths))
        return 0

    print("❌ check-docs: %d 件の不整合" % len(errors), file=sys.stderr)
    for e in errors:
        print("   - %s" % e, file=sys.stderr)
    print("", file=sys.stderr)
    print("   ルール: .claude/rules/doc-consistency.md", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
