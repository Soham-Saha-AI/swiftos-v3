package com.gathin.tvhome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import static org.junit.Assert.*;
public class WallpaperStorageTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private File image(File directory, String name) throws Exception {
        File file = new File(directory, name); Files.write(file.toPath(), new byte[]{1}); return file;
    }
    @Test public void publicChoiceDoesNotCollideWithPrivateFileName() throws Exception {
        File directory = temporary.newFolder();
        File photo = image(directory, "clear.jpg");
        assertEquals("weather:clear.jpg", WallpaperStorage.choice(photo));
        assertEquals(photo.getCanonicalFile(), WallpaperStorage.resolve(directory, WallpaperStorage.choice(photo)));
        assertNull(WallpaperStorage.resolve(directory, "clear.jpg"));
    }
    @Test public void traversalHiddenFilesAndWrongTypesNeverResolve() throws Exception {
        File directory = temporary.newFolder();
        image(directory, ".private.jpg"); image(directory, "notes.txt");
        assertNull(WallpaperStorage.resolve(directory, "weather:../clear.jpg"));
        assertNull(WallpaperStorage.resolve(directory, "weather:..\\clear.jpg"));
        assertNull(WallpaperStorage.resolve(directory, "weather:/clear.jpg"));
        assertNull(WallpaperStorage.resolve(directory, "weather:.private.jpg"));
        assertNull(WallpaperStorage.resolve(directory, "weather:notes.txt"));
    }
    @Test public void userAddedPhotosAppearAndDeletionBecomesMissing() throws Exception {
        File directory = temporary.newFolder();
        File a = image(directory, "A.png"); image(directory, "B.webp");
        Files.write(new File(directory,"empty.jpg").toPath(), new byte[0]);
        List<File> images = WallpaperStorage.images(directory);
        assertEquals(2, images.size());
        assertEquals(a.getCanonicalFile(), images.get(0));
        assertTrue(a.delete());
        assertNull(WallpaperStorage.resolve(directory, "weather:A.png"));
        assertTrue(WallpaperStorage.images(new File(directory,"missing")).isEmpty());
    }
    @Test public void listingIsBoundedAndKeepsDeterministicOrder() throws Exception {
        File directory = temporary.newFolder();
        for (int i=0;i<110;i++) image(directory,String.format("%03d.jpg",i));
        List<File> files = WallpaperStorage.images(directory);
        assertEquals(100,files.size()); assertEquals("000.jpg",files.get(0).getName());
        assertEquals("099.jpg",files.get(99).getName());
    }
    @Test public void versionPermissionsStayLimitedToReadableImages() {
        assertArrayEquals(new String[]{"android.permission.READ_EXTERNAL_STORAGE"}, WallpaperStorage.permissionsForApi(28));
        assertArrayEquals(new String[]{"android.permission.READ_EXTERNAL_STORAGE"}, WallpaperStorage.permissionsForApi(32));
        assertArrayEquals(new String[]{"android.permission.READ_MEDIA_IMAGES"}, WallpaperStorage.permissionsForApi(33));
        assertArrayEquals(new String[]{"android.permission.READ_MEDIA_IMAGES","android.permission.READ_MEDIA_VISUAL_USER_SELECTED"}, WallpaperStorage.permissionsForApi(34));
    }
}
