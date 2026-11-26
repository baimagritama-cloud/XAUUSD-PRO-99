package com.xauusdpro99.data.signal;
import org.junit.Test;import java.util.*;import com.xauusdpro99.data.candle.CandleTimeframe;
import static org.junit.Assert.*;
public class SignalScoringEngineTest {
 @Test public void exactQualityThresholds(){assertEquals(SignalScoringEngine.Quality.NO_TRADE,SignalScoringEngine.qualityForTest(56));assertEquals(SignalScoringEngine.Quality.EARLY_LOWER_QUALITY,SignalScoringEngine.qualityForTest(57));assertEquals(SignalScoringEngine.Quality.MODERATE_QUALITY,SignalScoringEngine.qualityForTest(70));assertEquals(SignalScoringEngine.Quality.HIGH_QUALITY,SignalScoringEngine.qualityForTest(85));}
 @Test public void weightsSumTo100(){assertEquals(100,SignalScoringEngine.WEIGHT_PATTERN+SignalScoringEngine.WEIGHT_LOCATION+SignalScoringEngine.WEIGHT_INDICATORS+SignalScoringEngine.WEIGHT_STRUCTURE+SignalScoringEngine.WEIGHT_ALIGNMENT+SignalScoringEngine.WEIGHT_ENTRY);}
 @Test public void missingTimeframesCannotEmitBuyOrSell(){long n=1800000000000L;SignalScoringEngine.Input in=new SignalScoringEngine.Input(Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),true,true,n,n,2000,2000.2,.2,.8);SignalScoringEngine.Result r=new SignalScoringEngine().evaluateDirection(in,SignalScoringEngine.Direction.BUY);assertNotEquals(SignalScoringEngine.Status.BUY,r.status);assertNotEquals(SignalScoringEngine.Status.SELL,r.status);assertFalse(r.rejectedReasons.isEmpty());}

 @Test public void everyScoreMapsToRequiredQualityBand(){
  for(int score=0;score<=100;score++){
   SignalScoringEngine.Quality expected=score<57?SignalScoringEngine.Quality.NO_TRADE:score<70?SignalScoringEngine.Quality.EARLY_LOWER_QUALITY:score<85?SignalScoringEngine.Quality.MODERATE_QUALITY:SignalScoringEngine.Quality.HIGH_QUALITY;
   assertEquals("score="+score,expected,SignalScoringEngine.qualityForTest(score));
  }
 }
 @Test public void missingDataAndInvalidQuotesFailClosedForBothDirections(){
  long n=1800000000000L;
  for(boolean live:new boolean[]{true,false}) for(long age:new long[]{0,15001}) for(double ask:new double[]{2000.2,2001.0,1999.0,Double.NaN}){
   SignalScoringEngine.Input in=new SignalScoringEngine.Input(Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),live,true,n-age,n,2000,ask,ask-2000,.8);
   for(SignalScoringEngine.Result result:new SignalScoringEngine().evaluate(in)){
    assertEquals(0,result.totalScore);
    assertEquals(SignalScoringEngine.Status.NO_TRADE,result.status);
    assertFalse(result.rejectedReasons.isEmpty());
   }
  }
 }
}
