import datetime as dt
import json, lzma, math, pathlib, statistics, struct, urllib.request, urllib.error, time
UTC=dt.timezone.utc
def get(url):
    with urllib.request.urlopen(urllib.request.Request(url,headers={"User-Agent":"XAUUSD-history-research/1.0"}),timeout=20) as r:
        return r.read(8_000_000)
def valid(v):
    o,h,l,c=v
    return all(math.isfinite(x) and x>0 for x in v) and h>=max(o,c) and l<=min(o,c)
def bars(url,start,step,end):
    raw=lzma.decompress(get(url))
    if len(raw)%24: raise ValueError("Invalid record length")
    result={}
    for sec,o,c,l,h,v in struct.iter_unpack(">IIIIIf",raw):
        t=start+dt.timedelta(seconds=sec)
        p=tuple(x/1000 for x in (o,h,l,c))
        if sec%step or t>=end or t+dt.timedelta(seconds=step)>dt.datetime.now(UTC) or not valid(p) or not math.isfinite(v) or v<=0: continue
        if t in result: raise ValueError("Duplicate candle")
        result[t]=p
    return result
def compare(a,b):
    overlap=sorted(a.keys() & b.keys())
    stats={"overlap":len(overlap),"biquote_count":len(a),"candidate_count":len(b)}
    for i,k in enumerate(("open","high","low","close")):
        errors=sorted(abs(a[t][i]-b[t][i]) for t in overlap)
        if errors: stats[k]={"median_abs_usd":statistics.median(errors),"p95_abs_usd":errors[math.ceil(.95*len(errors))-1],"max_abs_usd":max(errors)}
    return stats
report={"production_approved":False,"license_verified":False,"note":"Research metrics only; no raw quotes published. Candidate candle paths and scale are hypotheses under test.","intervals":{}}
base="https://datafeed.dukascopy.com/datafeed/XAUUSD/"
for interval,step in (("1m",60),("1h",3600)):
    item={}
    report["intervals"][interval]=item
    try:
        root=json.loads(get("https://biquote.io/api/XAUUSD/ohlc?interval="+interval+"&limit=1000"))
        if root.get("symbol")!="XAUUSD" or root.get("interval")!=interval: raise ValueError("Wrong envelope")
        a={}
        for b in root["bars"]:
            t=dt.datetime.fromisoformat(b["openTime"].replace("Z","+00:00"))
            p=tuple(float(b[k]) for k in ("open","high","low","close"))
            if b.get("isOpen") is False and valid(p) and int(t.timestamp())%step==0 and t+dt.timedelta(seconds=step)<=dt.datetime.now(UTC): a[t]=p
        if not a: raise ValueError("No valid BiQuote bars")
        for side in ("BID","ASK"):
            candidate={}
            errors=[]
            starts=sorted({t.replace(hour=0,minute=0,second=0,microsecond=0,**({"day":1} if step==3600 else {})) for t in a})
            for start in starts[-4:]:
                prefix=f"{start.year}/{start.month-1:02d}/"
                if step==60:
                    path=prefix+f"{start.day:02d}/{side}_candles_min_1.bi5"
                    end=start+dt.timedelta(days=1)
                else:
                    path=prefix+f"{side}_candles_hour_1.bi5"
                    end=(start+dt.timedelta(days=32)).replace(day=1)
                try: candidate.update(bars(base+path,start,step,end))
                except Exception as e: errors.append(str(e))
                time.sleep(1)
            item[side]={"metrics":compare(a,candidate),"errors":errors}
    except Exception as e: item["error"]=str(e)
p=pathlib.Path("artifacts/dukascopy-audit.json")
p.parent.mkdir(exist_ok=True)
p.write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
