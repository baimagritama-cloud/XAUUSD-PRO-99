package com.xauusdpro99.data.news;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTPS JSON calendar transport. Endpoint and credentials are supplied by the user/provider. */
public final class EconomicCalendarClient {
    public static final class Response {
        public final String body, source;
        public final long receivedAtMs;
        Response(String body,String source,long receivedAtMs){this.body=body;this.source=source;this.receivedAtMs=receivedAtMs;}
    }
    public static final String PRIMARY = "https://showmytrades.com/api/public/calendar/v1?currency=USD&impact=all&days=7";
    public static final String FALLBACK = "https://xoomar.com/api/markets/calendar?importance=high";
    private EconomicCalendarClient(){}
    public static boolean refresh(EconomicNewsEngine engine,String endpoint,String apiKey) throws Exception {
        String primary=endpoint==null||endpoint.trim().isEmpty()?PRIMARY:endpoint;
        Exception failure=null;
        for(String url: primary.equals(PRIMARY)?new String[]{PRIMARY,FALLBACK}:new String[]{primary}) {
            try { Response response=fetch(url,apiKey); if(engine.updateJson(response.body,response.source,response.receivedAtMs)) return true; }
            catch(Exception ex){failure=ex;}
        }
        if(failure!=null) throw failure;
        return false;
    }
    public static Response fetch(String endpoint,String apiKey) throws Exception {
        if(endpoint==null||!endpoint.trim().toLowerCase(java.util.Locale.US).startsWith("https://"))
            throw new IllegalArgumentException("Calendar endpoint wajib HTTPS");
        boolean publicProvider=PRIMARY.equals(endpoint)||FALLBACK.equals(endpoint);
        if(!publicProvider&&(apiKey==null||apiKey.trim().isEmpty()))
            throw new IllegalArgumentException("API key kalender belum disimpan");
        if(endpoint.matches("(?i).*([?&])c=.*"))
            throw new IllegalArgumentException("Jangan masukkan API key ke URL; gunakan kolom API key terenkripsi");
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
        c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(15000);
        c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","XAUUSD-PRO-99/1.0");
        c.setInstanceFollowRedirects(false);
        if(!publicProvider)c.setRequestProperty("Authorization",apiKey.trim());
        try{
            int code=c.getResponseCode();
            InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
            if(in==null)throw new IllegalStateException("HTTP "+code+" tanpa response body");
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;int total=0;
            while((n=in.read(buf))!=-1){total+=n;if(total>2_000_000)throw new IllegalStateException("Calendar response melebihi 2 MB");out.write(buf,0,n);}
            in.close();if(code<200||code>=300)throw new IllegalStateException("HTTP "+code);
            return new Response(new String(out.toByteArray(),StandardCharsets.UTF_8),endpoint,System.currentTimeMillis());
        }finally{c.disconnect();}
    }
}
