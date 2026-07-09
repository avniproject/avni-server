package org.avni.server.importer.batch.model;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class BundleZipTest {
    @Test
    public void getFile() {
        HashMap<String, byte[]> bundleEntries = new HashMap<>();
        bundleEntries.put("someParentFolder/programs.json", new byte[1]);
        bundleEntries.put("encounterTypes.json", new byte[1]);
        bundleEntries.put("__MACOSX/JSSCP/._reportCard.json", new byte[1]);
        bundleEntries.put("extensions/__MACOSX/._ssCard.html", new byte[1]);
        BundleZip bundleZip = new BundleZip(bundleEntries);
        assertNotNull(bundleZip.getFile("programs.json"));
        assertNotNull(bundleZip.getFile("encounterTypes.json"));
        assertNull(bundleZip.getFile("foo.json"));
        assertNull(bundleZip.getFile("someParentFolder"));
        assertNull(bundleZip.getFile("reportCard.json"));
    }

    @Test
    public void getForms() {
        HashMap<String, byte[]> bundleEntries = new HashMap<>();
        bundleEntries.put("someParentFolder/programs.json", new byte[1]);
        bundleEntries.put("someParentFolder/forms/st1.json", new byte[8]);
        BundleZip bundleZip = new BundleZip(bundleEntries);
        Map<String, byte[]> forms = bundleZip.getFileNameAndDataInFolder(BundleFolder.FORMS.getFolderName());
        assertEquals(1, forms.size());
        assertTrue(forms.containsKey("st1.json"));
        assertEquals(8, forms.get("st1.json").length);
    }

    @Test
    public void getExtensionNames() {
        HashMap<String, byte[]> bundleEntries = new HashMap<>();
        bundleEntries.put("someParentFolder/extensions/foo.html", new byte[1]);
        bundleEntries.put("extensions/bar.html", new byte[1]);
        bundleEntries.put("extensions/__MACOSX/._ssCard.html", new byte[1]);
        BundleZip bundleZip = new BundleZip(bundleEntries);
        List<String> extensionNames = bundleZip.getExtensionNames();
        assertEquals(2, extensionNames.size());
        assertTrue(extensionNames.stream().anyMatch(s -> s.equals("extensions/foo.html")));
        assertTrue(extensionNames.stream().anyMatch(s -> s.equals("extensions/bar.html")));
    }

    @Test
    public void getsConceptMediaFilesSortedByConceptThenIndex() {
        HashMap<String, byte[]> entries = new HashMap<>();
        // deliberately inserted out of order; HashMap iteration is unordered
        entries.put("conceptMedia/A--Image--002--c.jpg", new byte[]{2});
        entries.put("conceptMedia/A--Image--000--a.jpg", new byte[]{0});
        entries.put("conceptMedia/A--Image--001--b.jpg", new byte[]{1});
        entries.put("conceptMedia/B--Video--000--v.mp4", new byte[]{9});
        entries.put("someParentFolder/programs.json", new byte[1]);
        BundleZip bundleZip = new BundleZip(entries);

        List<Map.Entry<String, byte[]>> ordered = bundleZip.getConceptMediaFilesInOrder();

        assertEquals(4, ordered.size());
        assertEquals("A--Image--000--a.jpg", ordered.get(0).getKey());
        assertEquals("A--Image--001--b.jpg", ordered.get(1).getKey());
        assertEquals("A--Image--002--c.jpg", ordered.get(2).getKey());
        assertEquals("B--Video--000--v.mp4", ordered.get(3).getKey());
    }

    @Test
    public void conceptMediaAccessorReturnsEmptyWhenNoMediaFolder() {
        HashMap<String, byte[]> entries = new HashMap<>();
        entries.put("encounterTypes.json", new byte[1]);
        BundleZip bundleZip = new BundleZip(entries);
        assertTrue(bundleZip.getConceptMediaFilesInOrder().isEmpty());
    }
}
