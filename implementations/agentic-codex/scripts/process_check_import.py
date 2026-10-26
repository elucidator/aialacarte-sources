"""Load the command module without executing its entrypoint."""
import importlib.util
from pathlib import Path
spec = importlib.util.spec_from_file_location('process_check', Path(__file__).with_name('process-check.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
Apps, api, parallel = module.Apps, module.api, module.parallel
