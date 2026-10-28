package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Used only on the wallpaper decoder thread. No service, file watcher or network request. */
final class LocalWallpaperPolicy {
    static final String[] WEATHER = {"clear", "cloudy", "fog", "drizzle", "rain", "snow", "thunderstorm", "unknown", "any"};
    static final String[] TIMES = {"morning", "day", "evening", "night", "all"};
    static final int MAX_IMAGES = 256;
    private final File root;
    private Map<String,List<File>> index = Collections.emptyMap();
    private long scannedAt = Long.MIN_VALUE;
    private long revision;
    private volatile boolean dirty = true;

    LocalWallpaperPolicy(File root) { this.root = root; }
    void invalidate() { dirty = true; }
    static String weather(int code) {
        if (code == 0 || code == 1) return "clear";
        if (code == 2 || code == 3) return "cloudy";
        if (code == 45 || code == 48) return "fog";
        if (code == 51 || code == 53 || code == 55 || code == 56 || code == 57) return "drizzle";
        if (code == 61 || code == 63 || code == 65 || code == 66 || code == 67 || code == 80 || code == 81 || code == 82) return "rain";
        if (code == 71 || code == 73 || code == 75 || code == 77 || code == 85 || code == 86) return "snow";
        if (code == 95 || code == 96 || code == 97 || code == 99) return "thunderstorm";
        return "unknown";
    }
    static String time(int hour) { return hour >= 6 && hour < 10 ? "morning" : hour >= 10 && hour < 17 ? "day" : hour >= 17 && hour < 21 ? "evening" : "night"; }
    static boolean image(String name) { String n=name.toLowerCase(java.util.Locale.ROOT); return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"); }
    AutoWallpaper.Selection select(WeatherReading reading, long now) {
        if (root == null) return null;
        long marker = new File(root,".revision").lastModified();
        if (dirty || marker != revision || scannedAt == Long.MIN_VALUE || now-scannedAt >= 900_000L || now < scannedAt) rebuild(now,marker);
        Calendar clock=Calendar.getInstance();clock.setTimeInMillis(now);
        String condition=reading!=null && reading.isFresh(now) ? weather(reading.weatherCode) : "unknown";
        String period=time(clock.get(Calendar.HOUR_OF_DAY));
        for (String key:new String[]{condition+"/"+period,condition+"/all","any/"+period,"any/all"}) {
            List<File> photos=index.get(key);if(photos==null||photos.isEmpty())continue;
            int choice=(int)Math.floorMod(now/1_800_000L,(long)photos.size());
            File photo=photos.get(choice);
            return new AutoWallpaper.Selection(key+"/"+photo.getName(),photo);
        }
        return null;
    }
    private void rebuild(long now,long marker) {
        Map<String,List<File>> next=new HashMap<>();int remaining=MAX_IMAGES;
        for(String weather:WEATHER)for(String time:TIMES){
            if(remaining==0)break;
            String key=weather+"/"+time;List<File> photos=new ArrayList<>();
            remaining=collect(new File(root,key),photos,remaining);
            if(time.equals("all"))remaining=collect(new File(root,weather),photos,remaining);
            if(weather.equals("any")&&time.equals("all"))remaining=collect(root,photos,remaining);
            if(!photos.isEmpty()){photos.sort((a,b)->a.getName().compareTo(b.getName()));next.put(key,Collections.unmodifiableList(photos));}
        }
        index=next;revision=marker;scannedAt=now;dirty=false;
    }
    private int collect(File directory,List<File> result,int remaining) {
        if(remaining==0)return 0;
        try { String base=root.getCanonicalPath();String target=directory.getCanonicalPath();if(!target.equals(base)&&!target.startsWith(base+File.separator))return remaining; }
        catch(IOException|SecurityException unavailable){return remaining;}
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(directory.toPath())){
            int inspected=0;
            for(Path entry:entries){if(remaining==0||++inspected>1024)break;File file=entry.toFile();
                if(image(file.getName())&&Files.isRegularFile(entry,java.nio.file.LinkOption.NOFOLLOW_LINKS)&&file.length()>0&&file.length()<=20L*1024*1024){result.add(file);remaining--;}
            }
        }catch(IOException|SecurityException ignored){/* Missing buckets simply fall through. */}
        return remaining;
    }
}
