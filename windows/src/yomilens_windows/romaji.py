"""Port of the Android converter: explicit vowels and preserved punctuation."""
import re
import unicodedata

_ROWS = """
あ a い i う u え e お o
か ka き ki く ku け ke こ ko
が ga ぎ gi ぐ gu げ ge ご go
さ sa し shi す su せ se そ so
ざ za じ ji ず zu ぜ ze ぞ zo
た ta ち chi つ tsu て te と to
だ da ぢ ji づ zu で de ど do
な na に ni ぬ nu ね ne の no
は ha ひ hi ふ fu へ he ほ ho
ば ba び bi ぶ bu べ be ぼ bo
ぱ pa ぴ pi ぷ pu ぺ pe ぽ po
ま ma み mi む mu め me も mo
や ya ゆ yu よ yo ら ra り ri る ru れ re ろ ro わ wa を o ゐ i ゑ e ん n ゔ vu
ぁ a ぃ i ぅ u ぇ e ぉ o ゃ ya ゅ yu ょ yo ゎ wa ゕ ka ゖ ke
しぇ she じぇ je ちぇ che てぃ ti でぃ di とぅ tu どぅ du つぁ tsa つぃ tsi つぇ tse つぉ tso
ふぁ fa ふぃ fi ふぇ fe ふぉ fo ふゅ fyu うぃ wi うぇ we うぉ wo ゔぁ va ゔぃ vi ゔぇ ve ゔぉ vo ゔゅ vyu
くぁ kwa くぃ kwi くぇ kwe くぉ kwo ぐぁ gwa いぇ ye てゅ tyu でゅ dyu
""".split()
MAP = dict(zip(_ROWS[::2], _ROWS[1::2]))
for kana, base in zip("きぎしじちぢにひびぴみり", ("ky", "gy", "sh", "j", "ch", "j", "ny", "hy", "by", "py", "my", "ry")):
    for tail, vowel in zip("ゃゅょ", "auo"):
        MAP[kana + tail] = base + vowel


def hiragana(text: str) -> str:
    text = re.sub(r"[\uff66-\uff9f]+", lambda m: unicodedata.normalize("NFKC", m[0]), text)
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else
                   chr(ord(c) - 0xfee0) if "\uff10" <= c <= "\uff5a" and c.isalnum() else c
                   for c in text)


def convert(text: str) -> str:
    text, out, geminate, i = hiragana(text), "", False, 0
    while i < len(text):
        c = text[i]
        i += 1
        if c == "っ":
            if geminate:
                out += "' "
            geminate = True
            continue
        if c == "ー":
            vowel = "ー"
            for v in reversed(out):
                if v in "aeiou":
                    vowel = v
                    break
                if not v.isalpha():
                    break
            out += vowel
            continue
        value = MAP.get(text[i-1:i+1]) if i < len(text) else None
        if value is not None:
            i += 1
        else:
            value = MAP.get(c, c)
        if geminate:
            out += "t" if value.startswith("ch") else value[0] if value[0] in "bcdfghjklmpqrstvwxyz" else "’"
            geminate = False
        out += value
        if c == "ん" and i < len(text) and MAP.get(text[i], " ")[0] in "aeiouy":
            out += "'"
    return out + ("’" if geminate else "")
