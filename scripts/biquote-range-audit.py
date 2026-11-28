import datetime as dt, urllib.request, urllib.parse, json, math, pathlib, time, hashlib
out=pathlib.Path('range-audit');out.mkdir(exist_ok=True)
now=dt.datetime.now(dt.timezone.utc)
def iso(t):return dt.datetime.fromtimestamp(t,dt.timezone.utc).isoformat().replace('+00:00','Z')
def fetch(interval,minutes,lo=None,hi=None):
    q={'interval':interval,'limit':1000}
    if lo is not None:q['from']=iso(lo)
    if hi is not None:q['to']=iso(hi)
    url='https://biquote.io/api/XAUUSD/ohlc?'+urllib.parse.urlencode(q)
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'XAUUSD-PRO-99 audit/1.0'}),timeout=30) as r:
        raw=r.read(4000000);status=r.status
    obj=json.loads(raw)
    if obj.get('symbol')!='XAUUSD' or obj.get('interval')!=interval:raise ValueError('Unexpected envelope')
    rows={};invalid=0;duplicates=0;outside=0;running=0
    for b in obj['bars']:
        try:
            t=dt.datetime.fromisoformat(b['openTime'].replace('Z','+00:00'))
            if t.tzinfo is None:raise ValueError()
            ts=int(t.timestamp());v=tuple(float(b[k]) for k in ('open','high','low','close'))
            if ts%(minutes*60) or not all(math.isfinite(x) and x>0 for x in v) or v[1]<max(v[0],v[3]) or v[2]>min(v[0],v[3]) or type(b.get('isOpen')) is not bool:raise ValueError()
            if b['isOpen']:running+=1;continue
            if ts+minutes*60>now.timestamp():raise ValueError()
            if ts in rows:duplicates+=1;continue
            if (lo is not None and ts<lo) or (hi is not None and ts>hi):outside+=1;continue
            rows[ts]=v
        except (ValueError,TypeError,KeyError):invalid+=1
    return rows,dict(url=url,http=status,sha256=hashlib.sha256(raw).hexdigest(),closed=len(rows),running=running,invalid=invalid,duplicates=duplicates,outside_range=outside,first=min(rows,default=None),last=max(rows,default=None))
report={}
for interval,m in [('1m',1),('5m',5),('15m',15),('30m',30),('1h',60),('4h',240),('1d',1440)]:
    item={};report[interval]=item
    try:
        base,item['baseline']=fetch(interval,m)
        if not base:raise ValueError('Empty baseline')
        earliest=min(base);step=m*60;union=dict(base);conflicts=set()
        cases=[('control',earliest,min(max(base),earliest+step*20))]
        cases += [('older_'+str(i),earliest-step*1000*i,earliest-step*1000*(i-1)-step) for i in (1,2)]
        for name,lo,hi in cases:
            time.sleep(.3)
            try:
                rows,meta=fetch(interval,m,lo,hi)
                meta['new_older']=sum(t<earliest and t not in union for t in rows)
                meta['overlap_conflicts']=sum(t in union and union[t]!=v for t,v in rows.items())
                if meta['invalid'] or meta['duplicates'] or meta['outside_range']:meta['usable']=False
                else:
                    meta['usable']=True
                    for t,v in rows.items():
                        if t in union and union[t]!=v:conflicts.add(t)
                        else:union[t]=v
                item[name]=meta
            except Exception as e:item[name]={'error':str(e)}
        item['unique_valid_union']=len(set(union)-conflicts)
        item['older_valid_union']=sum(t<earliest and t not in conflicts for t in union)
        item['conflicts']=len(conflicts)
        item['quantity_300']=item['unique_valid_union']>=300
        item['continuity_verified']=False
    except Exception as e:item['error']=str(e)
    print(interval,json.dumps(item),flush=True)
    (out/'biquote-range-audit.json').write_text(json.dumps(report,indent=2))
if any('error' in x or any('error' in x.get(k,{}) for k in ('control','older_1','older_2')) for x in report.values()):raise SystemExit('Incomplete range audit')
