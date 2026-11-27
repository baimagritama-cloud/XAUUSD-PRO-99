package com.xauusdpro99.data.news;
import com.xauusdpro99.data.candle.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class EconomicNewsReactionTest {
    private static final long R=1800000000000L;
    private static final long M=60000L;
    private final EconomicNewsEngine news=new EconomicNewsEngine();
    private EconomicNewsEngine.Event event(){
        assertTrue(news.updateJson("{\"events\":[{\"currency\":\"USD\",\"title\":\"CPI\",\"ts\":1800000000,\"impact\":\"high\"}]}",EconomicCalendarClient.PRIMARY,R));
        return news.getEvents().get(0);
    }
    private Candle bar(long t,double close,boolean open){
        return new Candle("XAUUSD",CandleTimeframe.M1,t,2000,Math.max(2000,close)+1,Math.min(2000,close)-1,0,0,open,"test fixture",R+3*M);
    }
    private CandleEngine.Snapshot snapshot(long now,Candle... bars){
        CandleEngine engine=new CandleEngine(CandleTimeframe.M1);
        engine.replaceFromServer(Arrays.asList(bars),R+3*M,"test fixture");
        return engine.snapshot(now);
    }
    @Test public void bullishPersistence(){
        EconomicNewsEngine.Reaction a=news.analyzeReaction(event(),snapshot(R+3*M,bar(R-M,2000,false),bar(R,2002,false),bar(R+M,2003,false),bar(R+2*M,2004,false)));
        assertEquals("ANALYZED",a.status);assertEquals("NAIK",a.initialDirection);assertEquals("BERTAHAN",a.persistence);
        assertEquals(2.0,a.change,0.001);assertEquals(6.0,a.reactionRange,0.001);
    }
    @Test public void bearishReversal(){
        EconomicNewsEngine.Reaction a=news.analyzeReaction(event(),snapshot(R+3*M,bar(R-M,2000,false),bar(R,1998,false),bar(R+M,1999,false),bar(R+2*M,2003,false)));
        assertEquals("ANALYZED",a.status);assertEquals("TURUN",a.initialDirection);assertEquals("BERBALIK",a.persistence);
    }
    @Test public void rejectGapOpenAndStale(){
        EconomicNewsEngine.Event e=event();
        assertNotEquals("ANALYZED",news.analyzeReaction(e,snapshot(R+3*M,bar(R-M,2000,false),bar(R,2002,false),bar(R+2*M,2004,false),bar(R+3*M,2005,true))).status);
        assertNotEquals("ANALYZED",news.analyzeReaction(e,snapshot(R+3*M,bar(R-M,2000,false),bar(R,2002,false),bar(R+M,2003,false),bar(R+3*M,2005,true))).status);
        assertNotEquals("ANALYZED",news.analyzeReaction(e,snapshot(R+6*M,bar(R-M,2000,false),bar(R,2002,false),bar(R+M,2003,false),bar(R+2*M,2004,false))).status);
    }
}
