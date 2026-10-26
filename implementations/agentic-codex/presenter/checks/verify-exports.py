#!/usr/bin/env python3
"""Validate export secrecy, immutability, hashes, independence and clean reference application."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile

HERE=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('exporter',HERE/'export.py'); module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
with tempfile.TemporaryDirectory(prefix='conference-export-audit-') as temp:
    for number in range(1,6):
        root=Path(temp)/str(number); digest=module.export(number,root)
        manifest=json.loads((root/'MANIFEST.json').read_text())
        assert digest==manifest['workspaceHash']
        permitted=set(module.ALLOWLIST)|{'README.md','TASK.md','MANIFEST.json'}
        if number==4:permitted|={'review/base/ConfirmationDispatcher.kt','review/change.diff'}
        actual={str(p.relative_to(root)) for p in root.rglob('*') if p.is_file()}
        assert actual==permitted,(actual-permitted,permitted-actual)
        for name,expected in manifest['files'].items():assert hashlib.sha256((root/name).read_bytes()).hexdigest()==expected
        try:module.export(number,root)
        except ValueError:pass
        else:raise AssertionError('Non-empty destination accepted')
        subprocess.run(['git','apply','--check',str(HERE/f'reference-patches/exercise-{number}.patch')],cwd=root,check=True)
        subprocess.run(['git','apply',str(HERE/f'reference-patches/exercise-{number}.patch')],cwd=root,check=True)
        # Editing an exported copy must not mutate source or another exercise.
        (root/'src/main/kotlin/conference/Models.kt').write_text('independent workspace')
        assert 'data class Session' in (module.ROOT/'src/main/kotlin/conference/Models.kt').read_text()
print('PASS: five explicit allowlists, manifests, refusal of non-empty destinations, isolated source copies and clean reference patches')
