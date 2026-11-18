package com.xauusdpro99.notification;
import static org.junit.Assert.*;import org.junit.Test;
public class NotificationPolicyTest{
 @Test public void blocksStaleAndDisabled(){NotificationPolicy p=new NotificationPolicy();assertFalse(p.accept("BOS","H1",1000000,false,true,5));assertFalse(p.accept("BOS","H1",1000000,true,false,5));}
 @Test public void cooldownSuppressesDuplicates(){NotificationPolicy p=new NotificationPolicy();assertTrue(p.accept("BOS","H1",1000000,true,true,5));assertFalse(p.accept("BOS","H1",1299999,true,true,5));assertTrue(p.accept("BOS","H1",1300000,true,true,5));}
 @Test public void overnightQuietHours(){assertTrue(NotificationPolicy.isQuietHours(1380,1320,420));assertTrue(NotificationPolicy.isQuietHours(360,1320,420));assertFalse(NotificationPolicy.isQuietHours(720,1320,420));assertTrue(NotificationPolicy.isQuietHours(600,540,1020));assertFalse(NotificationPolicy.isQuietHours(1020,540,1020));}
}