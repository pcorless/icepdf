package org.icepdf.ri.common.utility;

import org.icepdf.ri.util.ViewerPropertiesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class RecentlyUsedFilesTest {

    private ViewerPropertiesManager mockViewerPropertiesManager;
    private Preferences mockPreferences;

    @BeforeEach
    void setUp() {
        mockViewerPropertiesManager = mock(ViewerPropertiesManager.class);
        mockPreferences = mock(Preferences.class);
        when(mockViewerPropertiesManager.getPreferences()).thenReturn(mockPreferences);
        ViewerPropertiesManager.setInstance(mockViewerPropertiesManager);
    }

    @Test
    void getRecentlyUsedFilePathsReturnsEmptyArrayWhenNoFilesExist() {
        when(mockPreferences.get(anyString(), anyString())).thenReturn("");

        RecentlyUsedFiles.RecentlyUsedFile[] files = RecentlyUsedFiles.getRecentlyUsedFilePaths();

        assertNotNull(files);
        assertEquals(0, files.length);
    }

    @Test
    void getRecentlyUsedFilePathsParsesValidRecentFiles() {
        when(mockPreferences.get(anyString(), anyString()))
                .thenReturn("file1.txt|/path/to/file1|file2.txt|/path/to/file2|");

        RecentlyUsedFiles.RecentlyUsedFile[] files = RecentlyUsedFiles.getRecentlyUsedFilePaths();

        assertNotNull(files);
        assertEquals(2, files.length);
        assertEquals("file1.txt", files[0].getName());
        assertEquals("/path/to/file1", files[0].getPath());
        assertEquals("file2.txt", files[1].getName());
        assertEquals("/path/to/file2", files[1].getPath());
    }

    @Test
    void getRecentlyUsedFilePathsClearsInvalidData() {
        when(mockPreferences.get(anyString(), anyString())).thenReturn("file1.txt|/path/to/file1|file2.txt");

        RecentlyUsedFiles.getRecentlyUsedFilePaths();

        verify(mockPreferences).put(eq(ViewerPropertiesManager.PROPERTY_RECENTLY_OPENED_FILES), eq(""));
    }

    @Test
    void addRecentlyUsedFilePathAddsNewFileToEmptyList() {
        when(mockPreferences.get(anyString(), anyString())).thenReturn("");
        when(mockPreferences.getInt(anyString(), anyInt())).thenReturn(8);

        RecentlyUsedFiles.addRecentlyUsedFilePath(Paths.get("/path/to/newfile.txt"));

        verify(mockPreferences).put(eq(ViewerPropertiesManager.PROPERTY_RECENTLY_OPENED_FILES),
                eq("newfile.txt|/path/to/newfile.txt|"));
    }

    @Test
    void addRecentlyUsedFilePathMovesExistingFileToTop() {
        when(mockPreferences.get(anyString(), anyString()))
                .thenReturn("file1.txt|/path/to/file1.txt|file2.txt|/path/to/file2.txt|");
        when(mockPreferences.getInt(anyString(), anyInt())).thenReturn(8);

        RecentlyUsedFiles.addRecentlyUsedFilePath(Paths.get("/path/to/file3.txt"));

        verify(mockPreferences).put(eq(ViewerPropertiesManager.PROPERTY_RECENTLY_OPENED_FILES),
                eq("file3.txt|/path/to/file3.txt|file1.txt|/path/to/file1.txt|file2.txt|/path/to/file2.txt|"));
    }

    @Test
    void addRecentlyUsedFilePathTrimsListWhenExceedingMaxSize() {
        when(mockPreferences.get(anyString(), anyString()))
                .thenReturn("file1.txt|/path/to/file1.txt|file2.txt|/path/to/file2.txt|file3.txt|/path/to/file3.txt|");
        when(mockPreferences.getInt(anyString(), anyInt())).thenReturn(2);

        RecentlyUsedFiles.addRecentlyUsedFilePath(Paths.get("/path/to/newfile.txt"));

        verify(mockPreferences).put(eq(ViewerPropertiesManager.PROPERTY_RECENTLY_OPENED_FILES),
                eq("newfile.txt|/path/to/newfile.txt|file1.txt|/path/to/file1.txt|"));
    }
}

