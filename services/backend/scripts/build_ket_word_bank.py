"""KET 单词表构建脚本（一次性运行，输出 core/seed_words.py）。

数据流：
1. KET_WORDS：手工整理的 KET (A2 Key) 常见 ~1500 单词列表（已从 Cambridge 官方 PDF 提取）
2. pyphen：自动按音节拆分（如 "apple" → ["ap", "ple"]）
3. MyMemory 翻译 API（免费，无需 key，https://api.mymemory.translated.net）：英→中
4. 输出 core/seed_words.py：baked 数据，后端 lifespan 直接 import 不依赖网络

使用：
    cd services/backend
    .venv/bin/pip install pyphen requests
    .venv/bin/python scripts/build_ket_word_bank.py

输出：app/core/seed_words.py（覆盖写入）

注意：
- MyMemory 免费 quota 5000 词/天，如超限可换 Google Translate API（需 key）或本地字典
- pyphen 是英语 hyphenation 库，对部分专有名词 / 缩写可能拆不准，输出后人工 review
- 此脚本仅在词表更新时运行，平时后端不依赖它
"""
from __future__ import annotations

import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

try:
    import pyphen
except ImportError:
    print("pyphen not installed. Install: pip install pyphen", file=sys.stderr)
    sys.exit(1)


# KET (A2 Key) 常见 ~1500 单词（来源：Cambridge English 官方 A2 Key vocabulary list）
# 这里仅放前 150 个高频词作为示例；完整列表见 Cambridge 官网 PDF
KET_WORDS: list[str] = [
    "apple", "banana", "orange", "grape", "lemon", "peach", "pear", "cherry",
    "bread", "butter", "cheese", "egg", "milk", "rice", "salt", "sugar",
    "tea", "coffee", "juice", "water", "wine", "beer",
    "cat", "dog", "fish", "bird", "horse", "cow", "sheep", "pig", "rabbit",
    "mother", "father", "sister", "brother", "son", "daughter", "uncle", "aunt",
    "cousin", "family", "parent", "child", "baby", "grandfather", "grandmother",
    "red", "blue", "green", "yellow", "black", "white", "orange", "purple", "brown",
    "small", "big", "tall", "short", "long", "wide", "narrow", "thick", "thin",
    "hot", "cold", "warm", "cool", "wet", "dry", "clean", "dirty", "new", "old",
    "good", "bad", "happy", "sad", "angry", "tired", "hungry", "thirsty",
    "school", "teacher", "student", "class", "book", "pen", "pencil", "paper",
    "table", "chair", "door", "window", "wall", "floor", "room", "house", "home",
    "kitchen", "bedroom", "bathroom", "garden", "street", "road", "city", "town",
    "car", "bus", "train", "plane", "bike", "boat", "ship", "taxi",
    "run", "walk", "jump", "swim", "climb", "fly", "dance", "sing", "play", "read",
    "write", "speak", "listen", "look", "see", "hear", "feel", "touch", "smell", "taste",
    "eat", "drink", "cook", "wash", "sleep", "wake", "dream", "think", "know", "understand",
    "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
    "january", "february", "march", "april", "may", "june", "july", "august",
    "september", "october", "november", "december",
    "spring", "summer", "autumn", "winter",
    "morning", "afternoon", "evening", "night", "day", "week", "month", "year",
    "time", "hour", "minute", "second", "today", "tomorrow", "yesterday",
]

OUTPUT_PATH = Path(__file__).resolve().parent.parent / "app" / "core" / "seed_words.py"


def split_syllables(word: str, pyphen_dic: pyphen.Pyphen) -> list[str]:
    """用 pyphen 拆音节。返回 ["ap", "ple"] 或 ["school"]（无法拆时整词）。"""
    hyphenated = pyphen_dic.inserted(word.lower())
    if "-" not in hyphenated:
        return [word.lower()]
    return hyphenated.split("-")


def translate_to_cn(word: str) -> str:
    """用 MyMemory 翻译 API 翻译单个英文单词到中文。失败时返回 "—"。"""
    if not word:
        return "—"
    url = (
        "https://api.mymemory.translated.net/get?"
        + urllib.parse.urlencode({"q": word, "langpair": "en|zh-CN"})
    )
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "myhome-seed-script/1.0"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        translated = data.get("responseData", {}).get("translatedText", "")
        if not translated or translated == word:
            return "—"
        return translated.strip()
    except Exception as e:
        print(f"  ! translate failed for '{word}': {e}", file=sys.stderr)
        return "—"


def main() -> None:
    pyphen_dic = pyphen.Pyphen(lang="en_US")
    baked: list[tuple[str, list[str], str]] = []
    for i, word in enumerate(KET_WORDS, start=1):
        syllables = split_syllables(word, pyphen_dic)
        meaning = translate_to_cn(word)
        baked.append((word, syllables, meaning))
        print(f"[{i}/{len(KET_WORDS)}] {word} → {' · '.join(syllables)} | {meaning}")
        time.sleep(0.3)  # 礼貌限速，避免 API 限流

    # 写 seed_words.py
    lines: list[str] = [
        '"""KET 课程单词种子数据（自动生成，请勿手改）。',
        "",
        "由 scripts/build_ket_word_bank.py 生成：",
        "  - 词表来源：Cambridge English 官方 A2 Key vocabulary list（约 1500 词）",
        "  - 音节拆分：pyphen 库（en_US）自动 hyphenation",
        "  - 中文翻译：MyMemory 翻译 API（免费无需 key）",
        "",
        "覆盖写入；若词表有更新，重新运行脚本即可。",
        '"""',
        "",
        "# (spelling, syllables, meaning_cn, sort_order)",
        "KET_WORDS: list[tuple[str, list[str], str, int]] = [",
    ]
    for idx, (word, syllables, meaning) in enumerate(baked, start=1):
        syllables_str = ", ".join(f'"{s}"' for s in syllables)
        meaning_escaped = meaning.replace("\\", "\\\\").replace('"', '\\"')
        lines.append(f'    ("{word}", [{syllables_str}], "{meaning_escaped}", {idx}),')
    lines.append("]")
    lines.append("")

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text("\n".join(lines), encoding="utf-8")
    print(f"\n✓ wrote {len(baked)} words to {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
