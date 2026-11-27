import http.cookiejar, urllib.request, urllib.parse, datetime, json, pathlib, re, zipfile, io, hashlib, csv, math
from html.parser import HTMLParser
out=pathlib.Path('audit-alternatives'); out.mkdir(exist_ok=True)
report={}
session=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def get(url, data=None, referer=None):
    headers={'User-Agent':'XAUUSD-PRO-99 historical research audit/1.0','Accept':'*/*'}
    if referer:
        headers.update({'Referer':referer,'Origin':'https://www.histdata.com','Content-Type':'application/x-www-form-urlencoded'})
    req=urllib.request.Request(url,data=data,headers=headers)
    with session.open(req,timeout=40) as r:
        if data is not None:
            report['histdata']['download_response']={'http':r.status,'url_path':urllib.parse.urlparse(r.geturl()).path,'content_type':r.headers.get('Content-Type'),'content_length':r.headers.get('Content-Length'),'attachment':bool(r.headers.get('Content-Disposition'))}
        b=r.read(40*1024*1024+1)
        if len(b)>40*1024*1024: raise ValueError('response too large')
        return r.geturl(),r.status,b
class Forms(HTMLParser):
    def __init__(self): super().__init__(); self.forms=[]; self.form=None
    def handle_starttag(self,t,a):
        a=dict(a)
        if t=='form': self.form={'action':a.get('action',''),'method':a.get('method','get').lower(),'fields':{}}; self.forms.append(self.form)
        if t=='input' and self.form is not None and a.get('name') and a.get('type','').lower()=='hidden': self.form['fields'][a['name']]=a.get('value','')
    def handle_endtag(self,t):
        if t=='form': self.form=None
def download_period(year,month=None):
    period=str(year) if month is None else f'{year}{month:02d}'
    url=f'https://www.histdata.com/download-free-forex-historical-data/?/ascii/1-minute-bar-quotes/xauusd/{year}'+('' if month is None else f'/{month}')
    final,status,b=get(url); p=Forms(); p.feed(b.decode(errors='replace'))
    for f in p.forms:
        target=urllib.parse.urljoin(final,f['action']); a=f['fields']
        if f['method']!='post' or urllib.parse.urlparse(target).hostname not in ('www.histdata.com','histdata.com') or urllib.parse.urlparse(target).path!='/get.php': continue
        if not {'tk','date','datemonth','platform','timeframe','fxpair'}.issubset(a): continue
        if not a['tk'] or a['fxpair'].upper()!='XAUUSD' or a['timeframe'].upper()!='M1' or a['platform'].upper()!='ASCII' or a['date']!=str(year): continue
        if month is not None and a['datemonth']!=period: continue
        if month is None and a['datemonth'] not in ('',str(year)): continue
        _,status,b=get(target,urllib.parse.urlencode(a).encode(),referer=final)
        if not zipfile.is_zipfile(io.BytesIO(b)): raise ValueError('Not ZIP')
        return b
    raise ValueError('Matching public form not found')
def audit():
    import time
    now=datetime.datetime.now(datetime.timezone.utc); tz=datetime.timezone(datetime.timedelta(hours=-5))
    periods=[(y,None) for y in range(now.year-7,now.year)]+[(now.year,m) for m in range(1,now.month)]
    sizes=(1,2,3,5,15,30,60,240,480,720,1440,10080)
    buckets={m:{} for m in sizes}; conflicts=set(); seen={}; stats=[]
    report['histdata']={'periods':stats}
    for year,month in periods:
        st={'year':year,'month':month,'invalid':0,'identical_duplicates':0,'conflicting_duplicates':0,'valid':0};stats.append(st)
        try:
            b=download_period(year,month);st['sha256']=hashlib.sha256(b).hexdigest()
            with zipfile.ZipFile(io.BytesIO(b)) as z:
                for n in z.namelist():
                    if not n.lower().endswith('.csv'): continue
                    if 'XAUUSD' not in n.upper() or z.getinfo(n).file_size>100*1024*1024: raise ValueError('Unexpected CSV')
                    with z.open(n) as raw:
                        for line in io.TextIOWrapper(raw,encoding='utf-8-sig'):
                            try:
                                t,o,h,l,c,*_=line.strip().split(';');dt=datetime.datetime.strptime(t,'%Y%m%d %H%M%S').replace(tzinfo=tz);ts=int(dt.timestamp());v=tuple(map(float,(o,h,l,c)))
                                if dt.year!=year or (month and dt.month!=month) or ts%60 or ts+60>now.timestamp() or not all(math.isfinite(x) and x>0 for x in v) or v[1]<max(v[0],v[3]) or v[2]>min(v[0],v[3]): raise ValueError()
                            except ValueError: st['invalid']+=1;continue
                            if ts in seen:
                                if seen[ts]==v: st['identical_duplicates']+=1
                                else: st['conflicting_duplicates']+=1;conflicts.add(ts)
                                continue
                            seen[ts]=v;st['valid']+=1
            if not st['valid']: raise ValueError('No valid rows')
        except Exception as e: st['error']=str(e)
        print(json.dumps(st),flush=True);(out/'report.json').write_text(json.dumps(report,indent=2));time.sleep(1)
    # UTC bucket convention matches CandleTimeframe; observed gaps are not assumed holidays.
    from collections import Counter
    days={}; weeks={}
    for ts in sorted(seen):
        day=ts//86400*86400; week=(ts+3*86400)//604800*604800-3*86400
        for group,key in ((days,day),(weeks,week)):
            g=group.setdefault(key,{'observed':0,'conflicts':0,'first':None,'last':None,'internal_missing':0})
            if ts in conflicts: g['conflicts']+=1;continue
            if g['last'] is not None:g['internal_missing']+=(ts-g['last'])//60-1
            if g['first'] is None:g['first']=ts
            g['last']=ts;g['observed']+=1
    session={}
    for name,groups,step in [('D1',days,86400),('W1',weeks,604800)]:
        closed={k:g for k,g in groups.items() if k+step<=now.timestamp() and g['observed']}
        session[name]={'closed_observed':len(closed),'conflict_free':sum(not g['conflicts'] for g in closed.values()),'with_internal_gaps':sum(g['internal_missing']>0 for g in closed.values()),'weekday_counts':dict(Counter(datetime.datetime.fromtimestamp(k,datetime.timezone.utc).strftime('%A') for k in closed)),'calendar_verified':False,'status':'UNVERIFIED_SESSION_CALENDAR'}
        (out/(name+'-sessions.json')).write_text(json.dumps(closed))
    report['histdata']['session_audit']=session
    expected=sum(st['valid'] for st in stats)-len(conflicts)
    actual=len(seen)-len(conflicts)
    report['histdata']['reconciliation']={'sum_period_unique_minus_conflicts':expected,'actual_unique':actual,'matches':expected==actual}
    for ts in sorted(seen):
        if ts in conflicts: continue
        o,h,l,c=seen[ts]
        for m in sizes:
            offset=3*86400 if m==10080 else 0;step=m*60;k=(ts+offset)//step*step-offset
            if k+step>now.timestamp():continue
            g=buckets[m].get(k)
            if g is None:buckets[m][k]=[1,o,h,l,c]
            else:g[0]+=1;g[2]=max(g[2],h);g[3]=min(g[3],l);g[4]=c
    result={}
    for m,groups in buckets.items():
        complete=sum(g[0]==m for g in groups.values())
        result[str(m)]={'complete':complete,'observed_nonempty':len(groups),'partial':len(groups)-complete,'session_calendar_verified':False}
    report['histdata'].update(unique_valid=len(seen)-len(conflicts),conflicting_timestamps=len(conflicts),counts=result,first_utc=min(seen,default=None),last_utc=max(seen,default=None))
    (out/'report.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
    checks={}
    for m,groups in buckets.items():
        q=result[str(m)]
        eligible=sum(1 for ts in seen if ts not in conflicts and ((ts+(3*86400 if m==10080 else 0))//(m*60)+1)*(m*60)-(3*86400 if m==10080 else 0)<=now.timestamp())
        checks[str(m)]={'bucket_rows':sum(g[0] for g in groups.values()),'eligible_rows':eligible,'matches':sum(g[0] for g in groups.values())==eligible and q['complete']+q['partial']==q['observed_nonempty']}
    report['histdata']['bucket_reconciliation']=checks
    (out/'report.json').write_text(json.dumps(report,indent=2));print(json.dumps({'session_audit':session,'reconciliation':report['histdata']['reconciliation'],'bucket_reconciliation':checks},indent=2))
    if expected!=actual or not all(q['matches'] for q in checks.values()):raise SystemExit(1)
    if any('error' in st or st['invalid'] for st in stats) or any(result[str(m)]['complete']<300 for m in sizes if m<1440) or any(result[str(m)]['observed_nonempty']<300 for m in (1440,10080)):raise SystemExit(1)
audit()
