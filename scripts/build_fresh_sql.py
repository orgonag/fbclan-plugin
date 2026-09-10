"""Generate the standalone clean-project installer from versioned schema sources."""
from pathlib import Path
baseline=Path('supabase/tests/baseline.sql').read_text()
migration=Path('supabase/migrations/001_v3.sql').read_text()
header='-- EMPTY SUPABASE PROJECT ONLY. Existing project: use migrations/001_v3.sql instead.\n'
# One transaction: legacy grants are never externally visible between setup and cutover.
Path('supabase/install_fresh.sql').write_text(header+'BEGIN;\n'+baseline+'\n'+migration.replace('BEGIN;\n','',1))
