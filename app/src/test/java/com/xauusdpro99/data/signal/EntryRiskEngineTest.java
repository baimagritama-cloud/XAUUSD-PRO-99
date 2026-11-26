package com.xauusdpro99.data.signal;
import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class EntryRiskEngineTest {
 @Test public void waitOrNoTradeCannotProduceValidPlan(){long n=1800000000000L;
  SignalScoringEngine.Input empty=new SignalScoringEngine.Input(Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),true,true,n,n,2000,2000.2,.2,.8);
  SignalScoringEngine.Result s=new SignalScoringEngine().evaluateDirection(empty,SignalScoringEngine.Direction.BUY);
  EntryRiskEngine.Input in=new EntryRiskEngine.Input(s,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),2000,2000.2,.2,2000.1,n,n,true,true);
  EntryRiskEngine.Result r=new EntryRiskEngine().calculate(in);assertFalse(r.valid);assertFalse(r.rejected.isEmpty());
 }
 @Test public void noRiskPlanCanBeCreatedFromMissingData(){assertTrue(Double.isNaN(new SignalScoringEngine().evaluateDirection(new SignalScoringEngine.Input(Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),false,false,0,0,Double.NaN,Double.NaN,Double.NaN,.8),SignalScoringEngine.Direction.SELL).entry));}

 @Test public void missingTechnicalLevelsNeverFabricateTargetsForEitherDirection(){
  long n=1800000000000L;
  for(SignalScoringEngine.Direction d:SignalScoringEngine.Direction.values()){
   SignalScoringEngine.Input empty=new SignalScoringEngine.Input(Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),true,true,n,n,2000,2000.2,.2,.8);
   SignalScoringEngine.Result s=new SignalScoringEngine().evaluateDirection(empty,d);
   EntryRiskEngine.Result result=new EntryRiskEngine().calculate(new EntryRiskEngine.Input(s,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),2000,2000.2,.2,2000.1,n,n,true,true));
   assertFalse(result.valid);
   assertEquals(d==SignalScoringEngine.Direction.BUY?2000.2:2000,result.entry,1e-8);
   assertTrue(result.targets.isEmpty());
   assertTrue(Double.isNaN(result.sl));
   assertTrue(Double.isNaN(result.rr));
   assertTrue(Double.isNaN(result.atr));
  }
 }

 @Test public void technicalTargetsAreOrderedDistinctAndMeetMinimumRR(){
  for(SignalScoringEngine.Direction d:SignalScoringEngine.Direction.values()){
   double sign=d==SignalScoringEngine.Direction.BUY?1:-1;
   List<EntryRiskEngine.Level> levels=new ArrayList<>();
   for(double distance:new double[]{8,1,3,3.01,5,10})
    levels.add(new EntryRiskEngine.Level(2000+sign*distance,"M5","fixture swing"));
   List<EntryRiskEngine.Target> targets=EntryRiskEngine.selectTargets(levels,d,2000,2,.2);
   assertEquals(3,targets.size());
   double[] expected={3,5,8};
   for(int i=0;i<3;i++){
    assertEquals("TP"+(i+1),targets.get(i).label);
    assertEquals(2000+sign*expected[i],targets.get(i).price,1e-8);
    assertEquals(expected[i]/2,targets.get(i).rr,1e-8);
   }
  }
 }
 @Test public void insufficientTechnicalTargetsAreNotInvented(){
  List<EntryRiskEngine.Level> levels=Arrays.asList(new EntryRiskEngine.Level(2001,"M5","fixture"),new EntryRiskEngine.Level(2003,"M5","fixture"));
  List<EntryRiskEngine.Target> targets=EntryRiskEngine.selectTargets(levels,SignalScoringEngine.Direction.BUY,2000,2,.2);
  assertEquals(1,targets.size());
  assertEquals(1.5,targets.get(0).rr,1e-8);
 }
}
