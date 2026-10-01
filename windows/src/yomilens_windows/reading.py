from collections import OrderedDict
from dataclasses import dataclass
import re
from .romaji import convert, hiragana

JAPANESE = re.compile(r"[\u3040-\u30ff\u3400-\u9fff\uff66-\uff9f]")
HAN = re.compile(r"[\u3400-\u9fff]")


@dataclass(frozen=True)
class Reading:
    original: str
    kana: str
    romaji: str
    uncertain: bool = False


def append_word(out: str, word: str) -> str:
    return out + (" " if out and word and out[-1].isalnum() and word[0].isalnum() else "") + word


class ReadingEngine:
    def __init__(self):
        import fugashi
        import unidic_lite
        self.tagger = fugashi.Tagger(f'-d "{unidic_lite.DICDIR}"')
        self.cache = OrderedDict()
        self.overrides = {}
        self.keys = []

    def set_overrides(self, text: str):
        overrides = {}
        for line in text.splitlines():
            if not line.strip():
                continue
            if "=" not in line:
                raise ValueError("词典每行必须是 原文=假名")
            key, value = (p.strip() for p in line.split("=", 1))
            if not key or not value or not re.fullmatch(r"[ぁ-ゖァ-ヶー\uff66-\uff9f ]+", value):
                raise ValueError("词典读音只接受假名、长音符和空格")
            overrides[key] = value
        if len(overrides) > 500:
            raise ValueError("词典最多 500 条")
        self.overrides = overrides
        self.keys = sorted(overrides, key=lambda k: (-len(k), k))
        self.cache.clear()

    def read(self, text: str) -> Reading:
        if text in self.cache:
            self.cache.move_to_end(text)
            return self.cache[text]
        parts, start, i = [], 0, 0
        while i < len(text):
            key = next((k for k in self.keys if text.startswith(k, i)), None)
            if key is None:
                i += 1
                continue
            if i > start:
                parts.append(self._plain(text[start:i]))
            k = hiragana(self.overrides[key])
            parts.append(Reading(key, k, convert(k)))
            i += len(key)
            start = i
        if start < len(text):
            parts.append(self._plain(text[start:]))
        romaji = ""
        for part in parts:
            romaji = append_word(romaji, part.romaji)
        result = Reading(text, "".join(p.kana for p in parts), romaji, any(p.uncertain for p in parts))
        self.cache[text] = result
        if len(self.cache) > 512:
            self.cache.popitem(last=False)
        return result

    def _plain(self, text: str) -> Reading:
        kana, romaji, group, pos, uncertain = "", "", "", 0, False
        for token in self.tagger(text):
            surface, f = token.surface, token.feature
            at = text.find(surface, pos)
            if at > pos:
                romaji = append_word(romaji, convert(group)) + text[pos:at]
                group = ""
                kana += text[pos:at]
            if not JAPANESE.search(surface):
                romaji = append_word(romaji, convert(group)) + surface
                group = ""
                kana += surface
            else:
                # kana is the inflected surface reading (not kanaBase/lemma).
                k = getattr(f, "kana", None)
                if token.is_unk or not k or k == "*":
                    k = surface
                    uncertain |= bool(HAN.search(surface))
                kana += hiragana(k)
                phonetic = {"は": "ワ", "へ": "エ", "を": "オ"}.get(surface, k) if f.pos1 == "助詞" else k
                join = f.pos1 in ("助動詞", "接尾辞") or f.pos2 == "接続助詞" or group.endswith(("ッ", "っ"))
                if not join:
                    romaji = append_word(romaji, convert(group))
                    group = ""
                group += phonetic
            pos = at + len(surface)
        romaji = append_word(romaji, convert(group)) + text[pos:]
        kana += text[pos:]
        return Reading(text, kana, romaji, uncertain)
