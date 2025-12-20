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
    private RecentlyUsedFiles recentlyUsedFiles;

    @BeforeEach
    void setUp() {
        mockViewerPropertiesManager = mock(ViewerPropertiesManager.class);
        mockPreferences = mock(Preferences.class);
        when(mockViewerPropertiesManager.getPreferences()).thenReturn(mockPreferences);
        ViewerPropertiesManager.setInstance(mockViewerPropertiesManager);
        recentlyUsedFiles = new RecentlyUsedFiles();
    }

    @Test
    void getRecentlyUsedFilePathsReturnsEmptyArrayWhenNoFilesExist() {
        when(mockPreferences.get(anyString(), anyString())).thenReturn("");

        RecentlyUsedFiles.RecentlyUsedFile[] files = new RecentlyUsedFiles().getRecentlyUsedFilePaths();

        assertNotNull(files);
        assertEquals(0, files.length);
    }

    @Test
    void getRecentlyUsedFilePathsReturnsEmptyArrayWhenOddFileListLength() {
        when(mockPreferences.get(anyString(), anyString()))
                .thenReturn("file1.txt|/path/to/file1|file|2.txt|/path/to/file2|");
        RecentlyUsedFiles recentlyUsedFilesSpy = spy(recentlyUsedFiles);
        doReturn(true).when(recentlyUsedFilesSpy).doesPathExist(anyString());

        RecentlyUsedFiles.RecentlyUsedFile[] files = recentlyUsedFilesSpy.getRecentlyUsedFilePaths();

        assertNotNull(files);
        assertEquals(0, files.length);
    }

    @Test
    void getRecentlyUsedFilePathsParsesValidRecentFiles() {
        when(mockPreferences.get(anyString(), anyString()))
                .thenReturn("file1.txt|/path/to/file1|file2.txt|/path/to/file2|");
        RecentlyUsedFiles recentlyUsedFilesSpy = spy(recentlyUsedFiles);
        doReturn(true).when(recentlyUsedFilesSpy).doesPathExist(anyString());

        RecentlyUsedFiles.RecentlyUsedFile[] files = recentlyUsedFilesSpy.getRecentlyUsedFilePaths();

        assertNotNull(files);
        assertEquals(2, files.length);
        assertEquals("file1.txt", files[0].getName());
        assertEquals("/path/to/file1", files[0].getPath());
        assertEquals("file2.txt", files[1].getName());
        assertEquals("/path/to/file2", files[1].getPath());
    }

    @Test
    void getRecentlyUsedFilePathsParsesValidEscapedRecentFiles() {
        when(mockPreferences.get(anyString(), anyString()))
                .thenReturn("file\\|1.txt|/path/to/file\\|1|file2.txt|/path/to/file2|");
        RecentlyUsedFiles recentlyUsedFilesSpy = spy(recentlyUsedFiles);
        doReturn(true).when(recentlyUsedFilesSpy).doesPathExist(anyString());

        RecentlyUsedFiles.RecentlyUsedFile[] files = recentlyUsedFilesSpy.getRecentlyUsedFilePaths();

        assertNotNull(files);
        assertEquals(2, files.length);
        assertEquals("file|1.txt", files[0].getName());
        assertEquals("/path/to/file|1", files[0].getPath());
        assertEquals("file2.txt", files[1].getName());
        assertEquals("/path/to/file2", files[1].getPath());
    }

    @Test
    void getRecentlyUsedFilePathsClearsInvalidData() {
        when(mockPreferences.get(anyString(), anyString())).thenReturn("file1.txt|/path/to/file1|file2.txt|");

        RecentlyUsedFiles recentlyUsedFilesSpy = spy(recentlyUsedFiles);
        doReturn(true).when(recentlyUsedFilesSpy).doesPathExist(anyString());

        recentlyUsedFilesSpy.getRecentlyUsedFilePaths();

        verify(mockPreferences).put(eq(ViewerPropertiesManager.PROPERTY_RECENTLY_OPENED_FILES), eq(""));
    }

    @Test
    void getRecentlyUsedFilePathsClearsInvalidFilePath() {
        when(mockPreferences.get("application.viewer.preference.recent.files", ""))
                .thenReturn("file1.txt|/path/to/file1.txt|file2.txt|/path/to/file2.txt|file3.txt|/path/to/file3.txt|");

        RecentlyUsedFiles recentlyUsedFilesSpy = spy(recentlyUsedFiles);
        doReturn(true).when(recentlyUsedFilesSpy).doesPathExist("/path/to/file1.txt");
        doReturn(true).when(recentlyUsedFilesSpy).doesPathExist("/path/to/file3.txt");

        RecentlyUsedFiles.RecentlyUsedFile[] files = recentlyUsedFilesSpy.getRecentlyUsedFilePaths();

        assertNotNull(files);
        assertEquals(2, files.length);
        assertEquals("file1.txt", files[0].getName());
        assertEquals("/path/to/file1.txt", files[0].getPath());
        assertEquals("file3.txt", files[1].getName());
        assertEquals("/path/to/file3.txt", files[1].getPath());
    }

    @Test
    void getRecentlyUsedFilePathsClearsInvalidFileName() {
        when(mockPreferences.get("application.viewer.preference.recent.files", ""))
                .thenReturn("file1.txt|/path/to/file1.txt|file|2.txt|/path/to/file|2.txt|file3.txt|/path/to/file3" +
                        ".txt|");

        RecentlyUsedFiles recentlyUsedFilesSpy = spy(recentlyUsedFiles);
        doReturn(true).when(recentlyUsedFilesSpy).doesPathExist("/path/to/file1.txt");
        doReturn(true).when(recentlyUsedFilesSpy).doesPathExist("/path/to/file3.txt");

        RecentlyUsedFiles.RecentlyUsedFile[] files = recentlyUsedFilesSpy.getRecentlyUsedFilePaths();

        assertNotNull(files);
        assertEquals(2, files.length);
        assertEquals("file1.txt", files[0].getName());
        assertEquals("/path/to/file1.txt", files[0].getPath());
        assertEquals("file3.txt", files[1].getName());
        assertEquals("/path/to/file3.txt", files[1].getPath());
    }

    @Test
    void addRecentlyUsedFilePathAddsNewFileToEmptyList() {
        when(mockPreferences.get(anyString(), anyString())).thenReturn("");
        when(mockPreferences.getInt(anyString(), anyInt())).thenReturn(8);

        recentlyUsedFiles.addRecentlyUsedFilePath(Paths.get("/path/to/newfile.txt"));

        verify(mockPreferences).put(eq(ViewerPropertiesManager.PROPERTY_RECENTLY_OPENED_FILES),
                eq("newfile.txt|/path/to/newfile.txt|"));
    }

    @Test
    void addRecentlyUsedFilePathAddsFileWithEscaptedPipe() {
        when(mockPreferences.get(anyString(), anyString()))
                .thenReturn("file\\|1.txt|/path/to/file\\|1|file2.txt|/path/to/file2|");
        when(mockPreferences.getInt(anyString(), anyInt())).thenReturn(8);

        recentlyUsedFiles.addRecentlyUsedFilePath(Paths.get("/path/to/new|file.txt"));

        verify(mockPreferences).put(eq(ViewerPropertiesManager.PROPERTY_RECENTLY_OPENED_FILES),
                eq("new\\|file.txt|/path/to/new\\|file.txt|file\\|1.txt|/path/to/file\\|1|file2.txt|/path/to/file2|"));
    }

    @Test
    void addRecentlyUsedFilePathMovesExistingFileToTop() {
        when(mockPreferences.get(anyString(), anyString()))
                .thenReturn("file1.txt|/path/to/file1.txt|file2.txt|/path/to/file2.txt|");
        when(mockPreferences.getInt(anyString(), anyInt())).thenReturn(8);

        recentlyUsedFiles.addRecentlyUsedFilePath(Paths.get("/path/to/file3.txt"));

        verify(mockPreferences).put(eq(ViewerPropertiesManager.PROPERTY_RECENTLY_OPENED_FILES),
                eq("file3.txt|/path/to/file3.txt|file1.txt|/path/to/file1.txt|file2.txt|/path/to/file2.txt|"));
    }

    @Test
    void addRecentlyUsedFilePathTrimsListWhenExceedingMaxSize() {
        when(mockPreferences.get(anyString(), anyString()))
                .thenReturn("file1.txt|/path/to/file1.txt|file2.txt|/path/to/file2.txt|file3.txt|/path/to/file3.txt|");
        when(mockPreferences.getInt(anyString(), anyInt())).thenReturn(2);

        recentlyUsedFiles.addRecentlyUsedFilePath(Paths.get("/path/to/newfile.txt"));

        verify(mockPreferences).put(eq(ViewerPropertiesManager.PROPERTY_RECENTLY_OPENED_FILES),
                eq("newfile.txt|/path/to/newfile.txt|file1.txt|/path/to/file1.txt|"));
    }
}

