from pathlib import Path
from PyInstaller.utils.hooks import collect_data_files, collect_dynamic_libs, collect_submodules

root = Path(SPECPATH)
datas = [(str(root/'models'), 'models'), (str(root/'THIRD_PARTY'), 'THIRD_PARTY'),
         (str(root/'README.md'), '.')]
datas += [(str(p), 'source/'+str(p.parent.relative_to(root/'src'))) for p in (root/'src').rglob('*.py')]
datas += collect_data_files('rapidocr', excludes=['models/*.onnx'])
datas += collect_data_files('unidic_lite', excludes=['**/*.pdf'])
datas += collect_data_files('fugashi')
binaries = collect_dynamic_libs('fugashi')
hidden = collect_submodules('fugashi') + ['PySide6.QtTest']
a = Analysis([str(root/'launcher.py')], pathex=[str(root/'src')], binaries=binaries,
             datas=datas, hiddenimports=hidden,
             excludes=['tkinter', 'matplotlib', 'pandas', 'scipy', 'torch', 'tensorflow',
                       'paddle', 'openvino', 'tensorrt', 'IPython', 'pytest'],
             noarchive=False)
pyz = PYZ(a.pure)
exe = EXE(pyz, a.scripts, [], exclude_binaries=True, name='YomiLens',
          debug=False, strip=False, upx=False, console=False, disable_windowed_traceback=False)
coll = COLLECT(exe, a.binaries, a.datas, strip=False, upx=False, name='YomiLens')
