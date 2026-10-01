import pytest
from yomilens_windows.romaji import convert
from yomilens_windows.reading import ReadingEngine
from yomilens_windows.geometry import logical_to_physical, physical_box_to_local


@pytest.mark.parametrize("text,expected", [
    ("ニホンゴ", "nihongo"), ("とうきょう", "toukyou"), ("しゃしん", "shashin"),
    ("がっこう", "gakkou"), ("まっちゃ", "matcha"), ("いっしょ", "issho"),
    ("しんよう", "shin'you"), ("かんい", "kan'i"), ("こんにち", "konnichi"),
    ("フィールドワーク", "fiirudowaaku"), ("シェジェチェ", "shejeche"), ("ヴァ", "va"),
    ("ＡＢＣ１２３, ｹﾞｰﾑ!", "ABC123, geemu!"), ("日本", "日本"), ("", ""),
    ("あっ", "a’"), ("（ｺｰﾋｰ）！", "（koohii）！")])
def test_romaji_android_corpus(text, expected):
    assert convert(text) == expected


@pytest.fixture
def engine():
    return ReadingEngine()


def test_inflections_and_particles(engine):
    assert "kuwawatta" in engine.read("新しい仲間が加わった！").romaji
    assert "matte" in engine.read("待ってください。").romaji
    r = engine.read("私は東京へ行きます。")
    assert "wa toukyou e" in r.romaji
    assert "ikimasu" in r.romaji
    assert not r.uncertain


def test_override_longest_and_cache(engine):
    engine.set_overrides("八重=やえ\n八重神子=やえみこ")
    assert engine.read("八重神子に会いました。").romaji.startswith("yaemiko ni")
    assert "aimashita" in engine.read("八重神子に会いました。").romaji
    engine.set_overrides("八重神子=やえしんし")
    assert engine.read("八重神子").romaji == "yaeshinshi"


def test_preserves_symbols_and_unknown(engine):
    symbols = "「」（）【】『』、。！？…"
    r = engine.read("「日本語」（東京）【学校】『友達』、。！？…")
    assert "".join(c for c in r.romaji if c in symbols) == symbols
    assert "".join(c for c in r.kana if c in symbols) == symbols
    assert engine.read("龯").uncertain
    assert engine.read("龯").romaji == "龯"
    assert "HP 100" == engine.read("HP 100").romaji


def test_bad_override_is_atomic(engine):
    engine.set_overrides("東京=とうきょう")
    with pytest.raises(ValueError):
        engine.set_overrides("東京=Tokyo")
    assert engine.read("東京").romaji == "toukyou"


@pytest.mark.parametrize("scale", [1, 1.25, 1.5, 2])
def test_coordinates_with_negative_monitor_origin(scale):
    r = logical_to_physical((-1820, 100, 400, 200), (-1920, 0), (-2880, -300), scale)
    assert r.left == -2880 + round(100*scale)
    assert r.top == -300 + round(100*scale)
    assert physical_box_to_local([[0, 0], [r.width, 0], [r.width, r.height], [0, r.height]], scale) == (0, 0, 400, 200)
