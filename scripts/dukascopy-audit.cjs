const fs=require('node:fs');
const {getHistoricalRates}=require('dukascopy-node');
const DAY=86400000, end=Math.floor(Date.now()/DAY)*DAY;
const durations={m1:60000,m2:120000,m3:180000,m5:300000,m15:900000,m30:1800000,h1:3600000,h4:14400000,h8:28800000,h12:43200000,d1:DAY,w1:7*DAY};
const report={instrument:'xauusd',side:'bid',end:new Date(end).toISOString(),results:{},errors:[]};
fs.mkdirSync('audit-dukascopy',{recursive:true});
function record(tf,rows,extra={}){
 const n=durations[tf]; const gaps=rows.slice(1).filter((x,i)=>x.timestamp-rows[i].timestamp>n).length;
 report.results[tf]={count:rows.length,first:rows[0]?.timestamp,last:rows.at(-1)?.timestamp,gaps,meets300:rows.length>=300,...extra};
 console.log(tf,JSON.stringify(report.results[tf]));
}
function aggregate(rows,tf,source,weekly=false){
 const n=durations[tf], step=durations[source], offset=weekly?3*DAY:0;
 const groups=new Map();
 for(const x of rows){const key=Math.floor((x.timestamp+offset)/n)*n-offset;if(!groups.has(key))groups.set(key,[]);groups.get(key).push(x);}
 const out=[];let incomplete=0;
 for(const [timestamp,g] of groups){
 if(timestamp+n>end)continue;
 if(!weekly&&(g.length!==n/step||g.some((x,i)=>x.timestamp!==timestamp+i*step))){incomplete++;continue;}
 if(weekly&&g.length<5){incomplete++;continue;}
 out.push({timestamp,open:g[0].open,high:Math.max(...g.map(x=>x.high)),low:Math.min(...g.map(x=>x.low)),close:g.at(-1).close});
 }
 record(tf,out,{source,incomplete,sessionValidation:weekly?'PROVISIONAL: weekly session calendar not verified':'complete contiguous source bars'});
}
(async()=>{
 for(const [tf,days] of Object.entries({m1:5,m5:7,m15:14,m30:25,h1:45,h4:240,d1:2600})){
 try{
 const raw=await getHistoricalRates({instrument:'xauusd',dates:{from:end-days*DAY,to:end},timeframe:tf,priceType:'bid',format:'json',requestIntervalMs:500});
 if(!Array.isArray(raw))throw Error('Unexpected response');
 const seen=new Map();let invalid=0,duplicates=0;
 for(const x of raw){
 if(![x.timestamp,x.open,x.high,x.low,x.close].every(Number.isFinite)||x.timestamp%durations[tf]!==0||x.timestamp+durations[tf]>end||x.open<=0||x.low<=0||x.close<=0||x.high<Math.max(x.open,x.close)||x.low>Math.min(x.open,x.close)){invalid++;continue;}
 if(seen.has(x.timestamp))duplicates++;seen.set(x.timestamp,x);
 }
 const rows=[...seen.values()].sort((a,b)=>a.timestamp-b.timestamp);
 record(tf,rows,{raw:raw.length,invalid,duplicates});
 if(tf==='m1'){aggregate(rows,'m2',tf);aggregate(rows,'m3',tf);}
 if(tf==='h4'){aggregate(rows,'h8',tf);aggregate(rows,'h12',tf);}
 if(tf==='d1')aggregate(rows,'w1',tf,true);
 }catch(e){report.errors.push({tf,error:String(e)});console.error(tf,String(e));}
 fs.writeFileSync('audit-dukascopy/report.json',JSON.stringify(report,null,2));
 }
 if(report.errors.length||Object.keys(report.results).length!==12||Object.values(report.results).some(x=>!x.meets300||x.invalid>0))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1});
