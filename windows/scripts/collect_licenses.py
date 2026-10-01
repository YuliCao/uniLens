"""Preserve actual dependency notices and license texts, not just license names."""
from importlib.metadata import distributions
from pathlib import Path
import json
import shutil
import urllib.request

root = Path(__file__).resolve().parents[1]/'THIRD_PARTY'
root.mkdir(exist_ok=True)
index = []
for dist in distributions():
    name = dist.metadata['Name']
    if name == 'yomilens-windows':
        continue
    copied = []
    for f in dist.files or []:
        if any(word in f.name.lower() for word in ('license', 'licence', 'copying', 'notice', 'copyright')) and dist.locate_file(f).is_file():
            # Preserve hierarchy to avoid flattening different notices over one another.
            target = root/name/Path(*[p for p in f.parts if p not in ('..', '.')])
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(dist.locate_file(f), target)
            copied.append(str(target.relative_to(root)))
    index.append(dict(name=name, version=dist.version,
                      license=dist.metadata.get('License-Expression') or dist.metadata.get('License'),
                      home=dist.metadata.get('Home-page'), files=copied))
external = {
    'Qt-LGPL-3.0.txt': 'https://raw.githubusercontent.com/qt/qtbase/v6.8.3/LICENSES/LGPL-3.0-only.txt',
    'Qt-GPL-3.0.txt': 'https://raw.githubusercontent.com/qt/qtbase/v6.8.3/LICENSES/GPL-3.0-only.txt',
    'RapidOCR-LICENSE.txt': 'https://raw.githubusercontent.com/RapidAI/RapidOCR/v3.9.2/LICENSE',
    'PaddleOCR-LICENSE.txt': 'https://raw.githubusercontent.com/PaddlePaddle/PaddleOCR/main/LICENSE',
}
for name, url in external.items():
    target = root/name
    if not target.exists():
        with urllib.request.urlopen(url, timeout=30) as response:
            target.write_bytes(response.read())
(root/'index.json').write_text(json.dumps(index, ensure_ascii=False, indent=2), encoding='utf-8')
(root/'README.txt').write_text('''YomiLens third-party notices

Qt / PySide6 are distributed as unmodified dynamically loaded libraries.
Qt LGPL v3 and GPL v3 license texts are included. Users may replace compatible
Qt/PySide DLLs in _internal and debug modifications to those libraries.
No additional restriction on that use is imposed by this application.
Application Python source is included in _internal/source for inspection.
Matching upstream sources:
https://download.qt.io/archive/qt/6.8/6.8.3/submodules/
https://code.qt.io/cgit/pyside/pyside-setup.git/tag/?h=v6.8.3

UniDic-lite code: MIT; included UniDic dictionary: BSD (LICENSE.unidic).
fugashi/MeCab and other notices are preserved in their package directories.
RapidOCR and the PaddleOCR-derived ONNX models: Apache 2.0.
Models and their exact hashes/URLs are in _internal/models/manifest.json.

index.json lists build-environment distributions; some development packages
listed there are not bundled in the executable. Package files contain their
full license and copyright notices. No third-party library source was modified.
''', encoding='utf-8')
print(f'Collected licenses for {len(index)} distributions')
