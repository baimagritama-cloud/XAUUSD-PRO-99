package com.xauusdpro99.data.news;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.Key;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts the provider API key with an AES key held by Android Keystore. */
public final class CalendarCredentialStore {
    private static final String ALIAS="xauusd_pro99_calendar_key";
    private static final String PREFS="economic_news_credentials";
    private CalendarCredentialStore(){}
    public static void save(Context context,String apiKey) throws Exception {
        if(apiKey==null||apiKey.trim().isEmpty())return;
        Key key=getOrCreateKey();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key);
        byte[] encrypted=cipher.doFinal(apiKey.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("data",Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply();
    }
    public static String load(Context context) {
        try {
            SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            String ivText=p.getString("iv",null),dataText=p.getString("data",null);
            if(ivText==null||dataText==null)return "";
            KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
            Key key=ks.getKey(ALIAS,null);
            if(key==null)return "";
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(ivText,Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(dataText,Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
        } catch(Exception e){return "";}
    }
    private static Key getOrCreateKey() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        Key existing=ks.getKey(ALIAS,null);if(existing!=null)return existing;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
}
