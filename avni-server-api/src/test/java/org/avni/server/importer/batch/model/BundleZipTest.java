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
}
