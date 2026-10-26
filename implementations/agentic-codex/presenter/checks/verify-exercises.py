#!/usr/bin/env python3
"""End-to-end exercise acceptance in disposable copies, run sequentially on a disposable PostgreSQL DB."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('exporter',ROOT/'presenter/export.py'); exporter=importlib.util.module_from_spec(spec);spec.loader.exec_module(exporter)

def run(args,cwd,log):
    print('+', ' '.join(map(str,args)), 'in',cwd,flush=True)
    result=subprocess.run(list(map(str,args)),cwd=cwd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=240)
    log.write_text(result.stdout)
    if result.returncode: print(result.stdout);raise RuntimeError(f'Command failed: {args}, see {log}')
    print(result.stdout[-1200:],flush=True)

p=argparse.ArgumentParser();p.add_argument('--exercise',type=int,choices=range(1,6));p.add_argument('--browser-python');p.add_argument('--output',default=str(ROOT/'verification-output/exercise-checks'))
a=p.parse_args();output=Path(a.output);output.mkdir(parents=True,exist_ok=True)
for number in ([a.exercise] if a.exercise else range(1,6)):
    start=output/f'{number}-start';reference=output/f'{number}-reference'
    exporter.export(number,start);exporter.export(number,reference)
    run(['git','apply','--check',ROOT/f'presenter/reference-patches/exercise-{number}.patch'],reference,output/f'{number}-patch-check.log')
    run(['git','apply',ROOT/f'presenter/reference-patches/exercise-{number}.patch'],reference,output/f'{number}-patch.log')
    if number==2:
        target=start/'src/test/kotlin/conference/CapacityEvidenceTest.kt';shutil.copy2(ROOT/'presenter/checks/CapacityEvidenceTest.kt',target)
        run(['./gradlew','--offline','test','--tests','conference.CapacityEvidenceTest','bootJar'],start,output/'2-reproduction.log')
        target.unlink()
        evidence = start/'build/test-results/test/TEST-conference.CapacityEvidenceTest.xml'
        shutil.copy2(evidence,ROOT/'presenter/evidence/capacity-observation.xml')
    else:
        run(['./gradlew','--offline','bootJar'] + (['test'] if number in [1,3,5] else []),start,output/f'{number}-start-build.log')
    if number in [1,4]:run([sys.executable,ROOT/'presenter/checks/scenarios.py',number,start],ROOT,output/f'{number}-starting-condition.log')
    run(['./gradlew','--offline','test','bootJar'],reference,output/f'{number}-reference-build.log')
    if number in [1,4]:run([sys.executable,ROOT/'presenter/checks/scenarios.py',number,reference,'--reference'],ROOT,output/f'{number}-reference-scenario.log')
    if number in [2,3]:run([sys.executable,reference/'scripts/process-check.py',reference],ROOT,output/f'{number}-reference-process.log')
    if number==1 and a.browser_python:
        run([a.browser_python,ROOT/'scripts/browser-check.py',reference,'--available-only','--screenshots',output/'1-browser'],ROOT,output/'1-reference-browser.log')
    print(f'PASS: exercise {number} starting snapshot and reference checks',flush=True)
print('PASS: requested exercise JVM checks; exercise 5 Compose lifecycle is checked separately by verify-compose.py')
