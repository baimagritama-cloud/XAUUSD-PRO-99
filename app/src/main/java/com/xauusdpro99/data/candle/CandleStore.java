package com.xauusdpro99.data.candle;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Native BiQuote closed candles only; separate from trading history. */
public final class CandleStore extends SQLiteOpenHelper {
    private static final String TABLE = "biquote_closed";
    public CandleStore(Context context) {
        super(context.getApplicationContext(), "biquote_ohlc.db", null, 1);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE "+TABLE+" (tf TEXT NOT NULL, ts INTEGER NOT NULL, o REAL NOT NULL, h REAL NOT NULL, l REAL NOT NULL, c REAL NOT NULL, volume REAL NOT NULL, ticks INTEGER NOT NULL, received INTEGER NOT NULL, PRIMARY KEY(tf,ts))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        throw new IllegalStateException("Unsupported OHLC database migration");
    }
    public synchronized void saveNative(CandleTimeframe tf,List<Candle> bars,long now) {
        if (!tf.nativeSupported) throw new IllegalArgumentException("Native timeframe required");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            for(Candle c:bars) {
                if(c==null || c.timeframe!=tf || c.isOpen || !CandleEngine.isValid(c,now) || !c.isClosedAt(now)) continue;
                if(c.source==null || !c.source.startsWith("BiQuote")) continue;
                ContentValues v=new ContentValues();
                v.put("tf",tf.name());v.put("ts",c.openTimeMs);
                v.put("o",c.open);v.put("h",c.high);v.put("l",c.low);v.put("c",c.close);
                v.put("volume",c.volume);v.put("ticks",c.tickVolume);v.put("received",c.receivedAtMs);
                db.insertWithOnConflict(TABLE,null,v,SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    public synchronized List<Candle> loadNative(CandleTimeframe tf,int limit,long now) {
        if(!tf.nativeSupported || limit<1 || limit>10000) throw new IllegalArgumentException("Invalid history request");
        List<Candle> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query(TABLE,null,"tf=?",new String[]{tf.name()},null,null,"ts DESC",Integer.toString(limit))) {
            while(c.moveToNext()) {
                Candle bar=new Candle("XAUUSD",tf,c.getLong(c.getColumnIndexOrThrow("ts")),c.getDouble(c.getColumnIndexOrThrow("o")),c.getDouble(c.getColumnIndexOrThrow("h")),c.getDouble(c.getColumnIndexOrThrow("l")),c.getDouble(c.getColumnIndexOrThrow("c")),c.getDouble(c.getColumnIndexOrThrow("volume")),c.getLong(c.getColumnIndexOrThrow("ticks")),false,"BiQuote OHLC SQLite",c.getLong(c.getColumnIndexOrThrow("received")));
                if(CandleEngine.isValid(bar,now)) result.add(bar);
            }
        }
        Collections.reverse(result);
        return result;
    }
}
