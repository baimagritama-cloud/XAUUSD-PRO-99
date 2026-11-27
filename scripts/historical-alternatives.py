import urllib.request, urllib.parse, datetime, json, pathlib, re, zipfile, io, hashlib, csv, math
from html.parser import HTMLParser
out=pathlib.Path('audit-alternatives'); out.mkdir(exist_ok=True)
report={}
def get(url, data=None):
    req=urllib.request.Request(url,data=data,headers={'User-Agent':'XAUUSD-PRO-99 historical research audit/1.0'})
    with urllib.request.urlopen(req,timeout=40) as r:
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
def audit_hist():
    today=datetime.date.today().replace(day=1)-datetime.timedelta(days=1)
    url=f'https://www.histdata.com/download-free-forex-historical-data/?/ascii/1-minute-bar-quotes/xauusd/{today.year}/{today.month}'
    final,status,b=get(url); p=Forms(); p.feed(b.decode(errors='replace'))
    report['histdata']={'page':final,'http':status,'forms':len(p.forms)}
    for f in p.forms:
        target=urllib.parse.urljoin(final,f['action'])
        if f['method']!='post' or urllib.parse.urlparse(target).hostname not in ('www.histdata.com','histdata.com') or urllib.parse.urlparse(target).path!='/get.php': continue
        if not f['fields']: continue
        final,status,b=get(target,urllib.parse.urlencode(f['fields']).encode())
        if not zipfile.is_zipfile(io.BytesIO(b)): raise ValueError('Download is not ZIP')
        rows={}; invalid=0; duplicates=0
        with zipfile.ZipFile(io.BytesIO(b)) as z:
            for n in z.namelist():
                if not n.lower().endswith('.csv'): continue
                if 'XAUUSD' not in n.upper(): raise ValueError('Unexpected symbol filename')
                if z.getinfo(n).file_size>100*1024*1024: raise ValueError('CSV too large')
                for line in z.read(n).decode('utf-8-sig').splitlines():
                    try:
                        t,o,h,l,c,*_=line.split(';'); ts=int(datetime.datetime.strptime(t,'%Y%m%d %H%M%S').replace(tzinfo=datetime.timezone(datetime.timedelta(hours=-5))).timestamp())
                        o,h,l,c=map(float,(o,h,l,c))
                        if not all(math.isfinite(x) and x>0 for x in (o,h,l,c)) or h<max(o,c) or l>min(o,c) or ts%60 or ts+60>datetime.datetime.now(datetime.timezone.utc).timestamp(): raise ValueError()
                        duplicates+=ts in rows; rows[ts]=(o,h,l,c)
                    except ValueError: invalid+=1
        report['histdata'].update(download_http=status,sha256=hashlib.sha256(b).hexdigest(),valid_m1=len(rows),invalid=invalid,duplicates=duplicates,first_utc=min(rows,default=None),last_utc=max(rows,default=None))
        counts={}
        for mins in (1,2,3,5,15,30,60,240,480,720,1440):
            groups={}
            for ts in rows: groups[ts//(mins*60)]=groups.get(ts//(mins*60),0)+1
            counts[str(mins)]=sum(v==mins for v in groups.values())
        report['histdata']['complete_contiguous_bucket_counts']=counts
        return
    raise ValueError('No supported public download form; manual download may be required')
def probe(name,url):
    final,status,b=get(url); report[name]={'page':final,'http':status,'bytes':len(b),'data_download_verified':False}
for name,fn in [('histdata',audit_hist),('dukascopy_csv',lambda:probe('dukascopy_csv','https://www.dukascopy.com/swiss/english/marketwatch/historical/')),('evtradelabs',lambda:probe('evtradelabs','https://evtradelabs.com/data'))]:
    try: fn()
    except Exception as e: report.setdefault(name,{})['error']=str(e)
    (out/'report.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
if report.get('histdata',{}).get('valid_m1',0)<900: raise SystemExit(1)
