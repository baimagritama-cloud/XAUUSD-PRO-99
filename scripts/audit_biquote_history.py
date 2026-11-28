import datetime as dt
import json
import pathlib
import urllib.request
import urllib.parse
import math
from collections import Counter

UTC = dt.timezone.utc
BASE = "https://biquote.io/api/XAUUSD/ohlc"
intervals = {"1m": (60, 900), "5m": (300, 300), "15m": (900, 300),
             "30m": (1800, 300), "1h": (3600, 300), "4h": (14400, 900),
             "1d": (86400, 1500)}

def parse(t):
    return dt.datetime.fromisoformat(t.replace("Z", "+00:00")).astimezone(UTC)

def iso(t):
    return t.isoformat().replace("+00:00", "Z")

def fetch(interval, seconds, **params):
    url = BASE + "?" + urllib.parse.urlencode(dict(interval=interval, limit=1000, **params))
    with urllib.request.urlopen(urllib.request.Request(url, headers={"Accept":"application/json"}), timeout=25) as response:
        data = json.load(response)
    if data.get("symbol") != "XAUUSD" or data.get("interval") != interval:
        raise ValueError("Unexpected envelope")
    now = dt.datetime.now(UTC)
    valid = {}
    for b in data["bars"]:
        t = parse(b["openTime"])
        o,h,l,c = (float(b[k]) for k in ("open","high","low","close"))
        if b.get("isOpen") is not False or t + dt.timedelta(seconds=seconds) > now:
            continue
        if int(t.timestamp()) % seconds or not all(math.isfinite(v) and v > 0 for v in (o,h,l,c)) or h < max(o,c) or l > min(o,c):
            continue
        valid[t] = (o,h,l,c)
    return valid

report = {}
for interval,(seconds,target) in intervals.items():
    item = {"target_native_depth":target}
    report[interval] = item
    try:
        latest = fetch(interval,seconds)
        item["latest_count"] = len(latest)
        if not latest:
            continue
        oldest=min(latest)
        item["oldest"]=iso(oldest)
        item["newest"]=iso(max(latest))
        merged=dict(latest)
        end=oldest-dt.timedelta(seconds=1)
        for page in range(3):
            start=end-dt.timedelta(seconds=seconds*1000*2)
            older=fetch(interval,seconds,**{"from":iso(start),"to":iso(end)})
            accepted={t:v for t,v in older.items() if start <= t <= end}
            item.setdefault("range_probes",[]).append({"from":iso(start),"to":iso(end),"returned":len(older),"in_range":len(accepted),"new":len(set(accepted)-set(merged))})
            if not accepted or not (set(accepted)-set(merged)):
                break
            merged.update(accepted)
            end=min(accepted)-dt.timedelta(seconds=1)
        item["unique_closed"]=len(merged)
        item["depth_met"]=len(merged)>=target
        if interval=="1d":
            item["weekday_counts"]=dict(Counter(t.strftime("%A") for t in merged))
            weeks={}
            for t in merged:
                monday=t.date()-dt.timedelta(days=t.weekday())
                weeks.setdefault(str(monday),set()).add(t.weekday())
            item["observed_weekday_patterns"]=dict(Counter(",".join(map(str,sorted(v))) for v in weeks.values()))
            item["complete_mon_fri_weeks"]=sum(v==set(range(5)) for v in weeks.values())
    except Exception as e:
        item["error"]=str(e)
path=pathlib.Path("artifacts/history-depth.json")
path.parent.mkdir(parents=True,exist_ok=True)
path.write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
