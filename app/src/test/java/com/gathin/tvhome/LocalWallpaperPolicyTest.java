package com.gathin.tvhome;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import static org.junit.Assert.*;
public class LocalWallpaperPolicyTest {
 @Rule public TemporaryFolder temp=new TemporaryFolder();
 @Test public void everyDocumentedWeatherCodeHasAFamily(){for(int c:new int[]{0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,97,99})assertNotEquals("unknown",LocalWallpaperPolicy.weather(c));assertEquals("unknown",LocalWallpaperPolicy.weather(999));assertEquals("snow",LocalWallpaperPolicy.weather(86));assertEquals("fog",LocalWallpaperPolicy.weather(48));}
 @Test public void timeBoundariesUseLocalClock(){assertEquals("night",LocalWallpaperPolicy.time(5));assertEquals("morning",LocalWallpaperPolicy.time(6));assertEquals("day",LocalWallpaperPolicy.time(10));assertEquals("evening",LocalWallpaperPolicy.time(17));assertEquals("night",LocalWallpaperPolicy.time(21));}
 @Test public void specificWeatherTimeWinsAndUnknownFallsBackWithoutGuessingWeather()throws Exception{File root=temp.newFolder();Calendar date=Calendar.getInstance();date.set(2026,9,27,12,0);long now=date.getTimeInMillis();photo(root,"rain/day/a.jpg");photo(root,"any/day/default.jpg");LocalWallpaperPolicy p=new LocalWallpaperPolicy(root);WeatherReading rain=WeatherReading.create("Test",20,63,true,now,now,now);assertEquals("a.jpg",p.select(rain,now).file.getName());assertEquals("default.jpg",p.select(null,now).file.getName());}
 @Test public void scanIsBoundedAndRevisionCanInvalidateTheCache()throws Exception{File root=temp.newFolder();for(int i=0;i<300;i++)photo(root,"any/all/"+i+".jpg");LocalWallpaperPolicy p=new LocalWallpaperPolicy(root);long now=System.currentTimeMillis();assertNotNull(p.select(null,now));p.invalidate();assertNotNull(p.select(null,now+1));assertFalse(LocalWallpaperPolicy.image("movie.mp4"));assertFalse(LocalWallpaperPolicy.image("photo.jpg.pending"));assertTrue(LocalWallpaperPolicy.image("PHOTO.PNG"));}
 private void photo(File root,String path)throws Exception{File f=new File(root,path);assertTrue(f.getParentFile().isDirectory()||f.getParentFile().mkdirs());try(FileOutputStream out=new FileOutputStream(f)){out.write(1);}}
}
