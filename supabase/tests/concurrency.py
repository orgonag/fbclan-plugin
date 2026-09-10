"""Real PostgreSQL multi-connection tests (CI); DATABASE_URL must be disposable."""
import concurrent.futures, json, os, uuid
import psycopg
URL = os.environ['DATABASE_URL']
def rpc(action, actor, data, expected=None, operation=None):
    with psycopg.connect(URL, autocommit=True) as connection:
        return connection.execute('select public.fb_lfg(%s,%s,%s::jsonb,%s::uuid,%s)',
            (action, actor, json.dumps(data), str(operation or uuid.uuid4()), expected)).fetchone()[0]
p = rpc('create','RaceHost',{'activity':'COX','capacity':6,'host_role':'COX_FILL','required_roles':','.join(['COX_FILL']*6)})
assert p['status']=='ok',p
pid=p['id']
for i in range(5):
    result=rpc('apply',f'Race{i}',{'id':pid,'role':'COX_FILL'})
    assert result['status']=='ok',result
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
    results=list(pool.map(lambda i: rpc('accept','RaceHost',{'id':pid,'rsn':f'Race{i}'},6),range(5)))
assert sum(r['status']=='ok' for r in results)==1,results
pending=[i for i,r in enumerate(results) if r['status']=='conflict']
assert len(pending)==4,results
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    results=list(pool.map(lambda i: rpc('accept','RaceHost',{'id':pid,'rsn':f'Race{i}'}),pending))
assert all(r['status']=='ok' for r in results),results
with psycopg.connect(URL) as connection:
    assert connection.execute('select count(*) from public.lfg_parties where id=%s',(pid,)).fetchone()[0]==0
    assert connection.execute('select count(*),max(jsonb_array_length(members)) from public.lfg_formed_parties where party_id=%s',(pid,)).fetchone()==(1,6)
# Simultaneous retries of one create must return one identical receipt.
operation=uuid.uuid4()
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    results=list(pool.map(lambda _:rpc('create','RetryHost',{'activity':'YAMA','capacity':2},operation=operation),range(6)))
assert all(r==results[0] and r['status']=='ok' for r in results),results
print('PASS: concurrent version conflicts, atomic formation, same-operation retries')
