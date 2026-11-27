package com.xauusdpro99.voice;
import android.content.Context;import android.content.SharedPreferences;import android.media.AudioManager;import android.media.ToneGenerator;import android.os.Handler;import android.os.Looper;import android.speech.tts.TextToSpeech;import android.speech.tts.UtteranceProgressListener;import android.speech.tts.Voice;import java.util.*;
/** Bell-first Indonesian TTS queue. Gender selection is best-effort because Android voice metadata is inconsistent. */
public final class VoiceEngine implements TextToSpeech.OnInitListener {
 public interface Listener{void onVoiceStatus(String status);}
 private final Context context;private final Listener listener;private final Handler handler=new Handler(Looper.getMainLooper());private final Queue<String> queue=new ArrayDeque<>();private final ToneGenerator bell;private final List<Voice> available=new ArrayList<>();
 private TextToSpeech tts;private volatile boolean ready,closed;private boolean speaking;private String status="TextToSpeech sedang diinisialisasi";
 public VoiceEngine(Context c,Listener l){context=c.getApplicationContext();listener=l;ToneGenerator tone=null;try{tone=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,85);}catch(RuntimeException ignored){}bell=tone;tts=new TextToSpeech(context,this);}
 @Override public void onInit(int result){if(closed)return;if(result!=TextToSpeech.SUCCESS||tts==null){update("TextToSpeech gagal diinisialisasi; periksa mesin TTS perangkat.");return;}
  Set<Voice> voices=tts.getVoices();if(voices!=null)available.addAll(voices);available.sort(Comparator.comparing(v->v.getName().toLowerCase(Locale.ROOT)));
  Voice female=null,idVoice=null;for(Voice v:available)if("id".equalsIgnoreCase(v.getLocale().getLanguage())){if(idVoice==null)idVoice=v;if(isFemale(v)){female=v;break;}}
  Voice chosen=female!=null?female:idVoice;String note;
  if(chosen!=null){int r=tts.setVoice(chosen);note=female!=null?"Suara perempuan Bahasa Indonesia dipilih: "+chosen.getName():"Suara perempuan tidak teridentifikasi; memakai suara Bahasa Indonesia tersedia: "+chosen.getName();if(r==TextToSpeech.ERROR)note="Gagal memilih suara Indonesia; memakai suara default.";}
  else{int r=tts.setLanguage(new Locale("id","ID"));note=(r==TextToSpeech.LANG_MISSING_DATA||r==TextToSpeech.LANG_NOT_SUPPORTED)?"Suara Bahasa Indonesia tidak tersedia; memakai suara default perangkat.":"Suara Indonesia tidak terdaftar sebagai Voice; memakai bahasa perangkat.";}
  tts.setSpeechRate(.94f);ready=true;update(note);
  tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String id){}public void onDone(String id){handler.post(()->{speaking=false;playNext();});}public void onError(String id){handler.post(()->{speaking=false;update("TTS gagal membaca alert; antrean dilanjutkan.");playNext();});}});
  playNext();
 }
 private static boolean isFemale(Voice v){String n=v.getName().toLowerCase(Locale.ROOT),f=String.valueOf(v.getFeatures()).toLowerCase(Locale.ROOT);return n.contains("female")||n.contains("woman")||n.contains("fem")||f.contains("female");}
 public List<String> getVoiceNames(){List<String>x=new ArrayList<>();for(Voice v:available)x.add(v.getName()+" ("+v.getLocale().toLanguageTag()+")");return x;}
 public void selectVoice(int index){if(!ready||tts==null||index<0||index>=available.size())return;Voice v=available.get(index);if(tts.setVoice(v)==TextToSpeech.ERROR)update("Tidak dapat memilih suara "+v.getName());else update("Suara dipilih: "+v.getName()+" ("+v.getLocale().toLanguageTag()+")");}
 public String getStatus(){return status;}
 public boolean isReady(){return ready&&!closed; }
 public void testVoice(){enqueue("Tes suara XAUUSD Pro sembilan sembilan. Ini adalah uji suara notifikasi Bahasa Indonesia.");}
 public void speakAlert(String type,String tf,double price,String reason,String confirmation,boolean fresh){if(!fresh||!ready||closed)return;String t=type==null?"":type.toUpperCase(Locale.ROOT);boolean trade=t.contains("BUY_CONFIRMED")||t.contains("SELL_CONFIRMED");if(trade&&!"CONFIRMED".equalsIgnoreCase(confirmation))return;
  StringBuilder m=new StringBuilder();if(t.contains("BUY_CONFIRMED"))m.append("Sinyal BUY terkonfirmasi. ");else if(t.contains("SELL_CONFIRMED"))m.append("Sinyal SELL terkonfirmasi. ");else if(t.contains("SUPPLY_TOUCHED"))m.append("Peringatan. Harga menyentuh area Supply. ");else if(t.contains("DEMAND_TOUCHED"))m.append("Peringatan. Harga menyentuh area Demand. ");else if(t.contains("BREAKOUT"))m.append("Peringatan. Breakout terdeteksi. ");else if(t.contains("RETEST"))m.append("Informasi. Retest terdeteksi. ");else if(t.contains("REJECTION"))m.append("Peringatan. Rejection terdeteksi. ");else if(t.contains("EMA"))m.append("Informasi EMA. ");else if(t.contains("BOS"))m.append("Break of Structure terdeteksi. ");else if(t.contains("CHOCH"))m.append("Change of Character terdeteksi. ");else if(t.contains("NEWS"))m.append("Peringatan berita ekonomi. ");else m.append("Peringatan pasar. ");
  m.append("XAUUSD, timeframe ").append(tf==null?"tidak diketahui":tf).append(". ");if(Double.isFinite(price)&&price>0)m.append("Harga ").append(String.format(Locale.US,"%.2f",price)).append(". ");if(reason!=null&&!reason.trim().isEmpty())m.append(reason).append(". ");if(!trade)m.append("Ini informasi pemantauan, bukan perintah entry.");enqueue(m.toString());
 }
 private void enqueue(String s){handler.post(()->{if(!closed){queue.offer(s);playNext();}});}
 private void playNext(){if(!ready||speaking||queue.isEmpty()||closed)return;String m=queue.poll();speaking=true;if(bell!=null)try{boolean started=bell.startTone(ToneGenerator.TONE_PROP_BEEP,380);android.util.Log.i("VoiceEngine","Bell requested; accepted="+started);}catch(RuntimeException ignored){}
  handler.postDelayed(()->{if(closed||tts==null){speaking=false;return;}android.util.Log.i("VoiceEngine","TTS requested after bell delay");if(tts.speak(m,TextToSpeech.QUEUE_FLUSH,null,"xauusd-"+System.nanoTime())==TextToSpeech.ERROR){speaking=false;update("TTS menolak pesan; periksa mesin dan data suara.");playNext();}else recordHistory(m);},480L);
 }
 private void recordHistory(String message){
  SharedPreferences prefs=context.getSharedPreferences("voice_history",Context.MODE_PRIVATE);
  String old=prefs.getString("entries","");
  String[] rows=old.isEmpty()?new String[0]:old.split("\\n");
  StringBuilder out=new StringBuilder();
  for(int i=Math.max(0,rows.length-49);i<rows.length;i++){if(out.length()>0)out.append('\n');out.append(rows[i]);}
  if(out.length()>0)out.append('\n');
  out.append(java.text.DateFormat.getDateTimeInstance().format(new Date())).append(" | ").append(message);
  prefs.edit().putString("entries",out.toString()).apply();
 }
 private void update(String s){status=s;if(listener!=null)listener.onVoiceStatus(s);}
 public void shutdown(){closed=true;handler.removeCallbacksAndMessages(null);queue.clear();if(bell!=null)bell.release();if(tts!=null){tts.stop();tts.shutdown();tts=null;}}
}