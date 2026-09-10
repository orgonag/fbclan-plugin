"""Reconstruct a disposable database fixture from the supplied metadata exports."""
import csv,json,pathlib,sys
meta=json.loads(next(csv.DictReader(open(sys.argv[1],encoding='utf-8-sig')))['schema_export'])['sections']
func=list(csv.DictReader(open(sys.argv[2],encoding='utf-8-sig')))
def q(x):return '"'+x.replace('"','""')+'"'
out=['-- EXPORTED BASELINE: disposable test database only; never run over production.']
for r in meta['relations']:
 if r['kind']!='r':continue
 cols=[]
 for c in meta['columns']:
  if c['table']!=r['name']:continue
  s=q(c['name'])+' '+c['type']
  if c['identity']:s+=' GENERATED ALWAYS AS IDENTITY'
  elif c['default_expression']:s+=' DEFAULT '+c['default_expression']
  if c['not_null']:s+=' NOT NULL'
  cols.append(s)
 out.append('CREATE TABLE public.'+q(r['name'])+' (\n  '+',\n  '.join(cols)+'\n);')
# All tables exist before foreign keys.
for c in sorted(meta['constraints'],key=lambda c:c['definition'].startswith('FOREIGN KEY')):out.append('ALTER TABLE public.'+q(c['table'])+' ADD CONSTRAINT '+q(c['name'])+' '+c['definition']+';')
constraint_names={c['name'] for c in meta['constraints']}
for i in meta['indexes']:
 if i['name'] not in constraint_names:out.append(i['definition']+';')
for f in func:
 if f['function_name']!='rls_auto_enable':out.append(f['definition']+';')
for t in meta['triggers']:out.append(t['definition']+';')
for r in meta['relations']:
 if r['kind']=='v':out.append('CREATE VIEW public.'+q(r['name'])+' AS '+r['view_definition'])
for r in meta['relations']:
 if r['kind']=='r':out.append('ALTER TABLE public.'+q(r['name'])+' ENABLE ROW LEVEL SECURITY;')
for p in meta['policies']:
 if p['schemaname']!='public':continue
 s='CREATE POLICY '+q(p['policyname'])+' ON public.'+q(p['tablename'])+' FOR '+p['cmd']+' TO '+', '.join(q(x) for x in p['roles'])
 if p['qual']:s+=' USING ('+p['qual']+')'
 if p['with_check']:s+=' WITH CHECK ('+p['with_check']+')'
 out.append(s+';')
out.append('GRANT USAGE ON SCHEMA public TO anon, authenticated;\nGRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO anon,authenticated;\nREVOKE ALL ON public.personal_bests,public.member_stats FROM anon;')
pathlib.Path('supabase/tests/baseline.sql').write_text('\n\n'.join(out)+'\n')
