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
    periods=[(y,None) for y in range(now.year-9,now.year)]+[(now.year,m) for m in range(1,now.month)]
    sizes=(1,2,3,5,15,30,60,240,480,720,1440,10080)
    buckets={m:{} for m in sizes}; conflicts=set(); seen={}; stats=[]
    report['histdata']={'periods':stats}
    for year,month in periods:
        st={'year':year,'month':month,'invalid':0,'identical_duplicates':0,'conflicting_duplicates':0,'valid':0};stats.append(st)
        try:
            b=download_period(year,month);st['sha256']=hashlib.sha256(b).hexdigest()
            with zipfile.ZipFile(io.BytesIO(b)) as z:
                # Preserve provider-supplied gap metadata for inspection, never infer holidays.
                st['archive_members']=[{'name':n,'bytes':z.getinfo(n).file_size} for n in z.namelist()]
                st['gap_reports']=[]
                for idx,n in enumerate(z.namelist()):
                    info=z.getinfo(n)
                    if n.lower().endswith('.csv') or info.is_dir():continue
                    if info.file_size>2*1024*1024:continue
                    raw=z.read(n)
                    if b'\x00' in raw:continue
                    dest=out/('provider-metadata-'+str(year)+'-'+str(month or 0)+'-'+str(idx)+'.txt')
                    dest.write_bytes(raw)
                    st['gap_reports'].append({'member':n,'artifact':dest.name,'sha256':hashlib.sha256(raw).hexdigest(),'bytes':len(raw),'classification':'UNREVIEWED_PROVIDER_METADATA'})
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
    # Provider reports describe tick gaps, not verified exchange closures.
    from collections import Counter
    import bisect
    intervals=[]; classes=Counter(); malformed=0; duration_mismatch=0; duration_offsets=Counter(); unexplained_duration=0
    for path in sorted(out.glob('provider-metadata-*.txt')):
        for line in path.read_text(errors='replace').splitlines():
            if not line.strip().startswith('Gap of'):continue
            match=re.fullmatch(r'Gap of (\d+)s found between (\d{14}) and (\d{14})\.',line.strip())
            if not match:malformed+=1;continue
            try:
                seconds=int(match[1]); a,b=[datetime.datetime.strptime(x,'%Y%m%d%H%M%S').replace(tzinfo=tz) for x in match.groups()[1:]]
                if b<=a:raise ValueError()
            except ValueError:malformed+=1;continue
            duration=(b-a).total_seconds()
            offset=int(duration-seconds)
            duration_offsets[offset]+=1
            if abs(offset)>1:duration_mismatch+=1
            if offset not in (0,1,6,7):unexplained_duration+=1
            kind='UNCLASSIFIED'
            if a.weekday()==4 and b.weekday()==6 and duration>=86400:kind='POSSIBLE_WEEKEND'
            elif a.date()==b.date() and a.hour in (15,16,17) and 3300<=duration<=3900:kind='POSSIBLE_DAILY_PAUSE'
            elif duration<180:kind='SHORT_TICK_GAP'
            classes[kind]+=1
            intervals.append((int(a.timestamp())//60*60,int(b.timestamp())//60*60,kind))
    intervals.sort()
    starts=[x[0] for x in intervals]; prefix=[]; end=-1
    for a,b,kind in intervals:end=max(end,b);prefix.append(end)
    valid_times=sorted(ts for ts in seen if ts not in conflicts)
    gaps=Counter(); unmatched=[]; unmatched_classes=Counter(); conflict_times=sorted(conflicts)
    for a,b in zip(valid_times,valid_times[1:]):
        if b-a<=60:continue
        i=bisect.bisect_left(starts,b)-1
        gaps['observed']+=1
        left=datetime.datetime.fromtimestamp(a,tz); right=datetime.datetime.fromtimestamp(b,tz)
        if left.weekday()==4 and right.weekday()==6 and 36*3600<=b-a<=60*3600:
            gaps['possible_weekend_from_ohlc']+=1
        elif left.weekday()==4 and right.weekday()==0 and 36*3600<=b-a<=84*3600:
            gaps['possible_weekend_from_ohlc']+=1
        else:gaps['other_observed_gap']+=1
        if i>=0 and prefix[i]>a:gaps['overlaps_provider_report']+=1
        else:
            gaps['no_report_overlap']+=1
            missing=(b-a)//60-1
            quarantined=bisect.bisect_left(conflict_times,b)-bisect.bisect_right(conflict_times,a)
            if quarantined==missing:kind='QUARANTINED_CONFLICT_ONLY'
            elif quarantined:kind='MIXED_CONFLICT_AND_MISSING'
            elif left.weekday()==4 and right.weekday() in (6,0) and 36*3600<=b-a<=(60 if right.weekday()==6 else 84)*3600:kind='POSSIBLE_WEEKEND'
            elif left.date()==right.date() and left.hour in (15,16,17) and 3300<=b-a<=3900:kind='POSSIBLE_DAILY_PAUSE'
            else:kind='UNEXPLAINED_MISSING_MINUTES'
            unmatched_classes[kind]+=1
            unmatched.append({'previous_utc':a,'next_utc':b,'missing_minutes':missing,'quarantined_minutes':quarantined,'candidate':kind,'first_affected_day_utc':(a+60)//86400*86400,'last_affected_day_utc':(b-60)//86400*86400,'first_affected_week_utc':(a+60+259200)//604800*604800-259200,'last_affected_week_utc':(b-60+259200)//604800*604800-259200})
    (out/'unmatched-gaps.json').write_text(json.dumps(unmatched,indent=2))
    report['histdata']['unmatched_gap_audit']={'classes':dict(unmatched_classes),'total':len(unmatched),'missing_minutes':sum(x['missing_minutes'] for x in unmatched),'quarantined_minutes':sum(x['quarantined_minutes'] for x in unmatched),'calendar_verified':False}
    print(json.dumps(report['histdata']['unmatched_gap_audit']),flush=True)
    if len(unmatched)!=gaps['no_report_overlap'] or sum(unmatched_classes.values())!=len(unmatched):raise ValueError('Unmatched gap reconciliation failed')
    report['histdata']['gap_classification']={'classes':dict(classes),'malformed':malformed,'duration_mismatch':duration_mismatch,'duration_offsets_seconds':dict(duration_offsets),'outside_observed_offset_pattern':unexplained_duration,'ohlc_gaps':dict(gaps),'calendar_verified':False,'note':'Overlap is diagnostic only; no inferred closure is certified.'}
    print(json.dumps(report['histdata']['gap_classification']),flush=True)
    if not intervals or malformed or unexplained_duration:raise ValueError('Missing or malformed provider gap metadata')
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
    # Conservative session quarantine; a clean candidate is not a verified candle.
    quarantine={}
    for name,groups,step,offset in [('D1',days,86400,0),('W1',weeks,604800,259200)]:
        reasons={}
        def mark(k,reason):
            reasons.setdefault(k,set()).add(reason)
        for ts in conflicts:mark((ts+offset)//step*step-offset,'CONFLICTING_M1')
        for gap in unmatched:
            if gap['candidate'] not in ('UNEXPLAINED_MISSING_MINUTES','MIXED_CONFLICT_AND_MISSING'):continue
            a=gap['previous_utc']+60;b=gap['next_utc']-60
            first=(a+offset)//step*step-offset;last=(b+offset)//step*step-offset
            for k in range(first,last+1,step):mark(k,'UNEXPLAINED_GAP')
        closed={k:g for k,g in groups.items() if k+step<=now.timestamp() and g['observed']}
        records=[]
        for k,g in sorted(closed.items()):
            why=sorted(reasons.get(k,set()))
            records.append({'start_utc':k,'observed_minutes':g['observed'],'internal_missing':g['internal_missing'],'reasons':why,'status':'QUARANTINED' if why else 'PENDING_CALENDAR_VERIFICATION'})
        blocked=sum(x['status']=='QUARANTINED' for x in records)
        quarantine[name]={'observed_closed':len(records),'quarantined':blocked,'pending_calendar':len(records)-blocked,'verified':0,'affected_without_observed_candle':sum(k not in closed for k in reasons)}
        (out/(name+'-quarantine.json')).write_text(json.dumps(records,indent=2))
    # Strict UTC continuity diagnostic. No calendar exemptions are assumed.
    continuity={}
    for name,step in [('D1',86400),('W1',604800)]:
        records=json.loads((out/(name+'-quarantine.json')).read_text())
        records.sort(key=lambda x:x['start_utc'])
        longest=0; run=0; previous=None; breaks=[]; latest=0
        for rec in records:
            k=rec['start_utc']
            clean=rec['status']=='PENDING_CALENDAR_VERIFICATION'
            adjacent=previous is not None and k-previous==step
            if previous is not None and not adjacent:
                breaks.append({'previous_utc':previous,'next_utc':k,'missing_buckets':(k-previous)//step-1})
            run=(run+1 if adjacent else 1) if clean else 0
            longest=max(longest,run);previous=k
        latest=run
        continuity[name]={'observed_sessions':len(records),'longest_adjacent_candidate_run':longest,'latest_adjacent_candidate_run':latest,'missing_bucket_intervals':len(breaks),'latest_300_adjacent_candidate_pass':latest>=300,'calendar_verified':False,'verified_count':0,'note':'Strict UTC adjacency only; weekends and holidays not exempted. Latest refers to archive end, not live market.'}
        (out/(name+'-continuity-breaks.json')).write_text(json.dumps(breaks,indent=2))
    report['histdata']['continuity_audit']=continuity
    print(json.dumps({'continuity_audit':continuity}),flush=True)
    report['histdata']['session_quarantine']=quarantine
    report['histdata']['candidate_coverage_gate']={name:{'target':300,'pending_candidates':q['pending_calendar'],'shortfall':max(0,300-q['pending_calendar']),'candidate_count_pass':q['pending_calendar']>=300,'verified_count_pass':False} for name,q in quarantine.items()}
    print(json.dumps(report['histdata']['candidate_coverage_gate']),flush=True)
    unknown=[g for g in unmatched if g['candidate']=='UNEXPLAINED_MISSING_MINUTES']
    report['histdata']['unexplained_gap_detail']={'count':len(unknown),'missing_minutes':sum(g['missing_minutes'] for g in unknown),'by_duration':dict(Counter('1-5m' if g['missing_minutes']<=5 else '6-60m' if g['missing_minutes']<=60 else '61-1440m' if g['missing_minutes']<=1440 else 'over_1440m' for g in unknown))}
    print(json.dumps({'session_quarantine':quarantine,'unexplained_gap_detail':report['histdata']['unexplained_gap_detail']}),flush=True)
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
