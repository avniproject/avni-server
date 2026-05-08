package org.avni.server.service.media;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GoogleDriveMediaUrlResolverTest {
    private final GoogleDriveMediaUrlResolver resolver = new GoogleDriveMediaUrlResolver();

    @Test
    public void rewritesDriveFileShareLinkToDirectDownload() {
        Optional<String> resolved = resolver.resolve("https://drive.google.com/file/d/11CjmQ-ljfnY2qHy4nt9dgvxZp_s1aRnI/view?usp=drive_link");
        assertEquals(Optional.of("https://drive.google.com/uc?export=download&id=11CjmQ-ljfnY2qHy4nt9dgvxZp_s1aRnI"), resolved);
    }

    @Test
    public void rewritesDriveOpenLinkToDirectDownload() {
        Optional<String> resolved = resolver.resolve("https://drive.google.com/open?id=11CjmQ-ljfnY2qHy4nt9dgvxZp_s1aRnI");
        assertEquals(Optional.of("https://drive.google.com/uc?export=download&id=11CjmQ-ljfnY2qHy4nt9dgvxZp_s1aRnI"), resolved);
    }

    @Test
    public void rewritesGoogleSheetsLinkToXlsxExport() {
        Optional<String> resolved = resolver.resolve("https://docs.google.com/spreadsheets/d/1HvE18kBGcqc1TadeHiLoNJnCMdg6LSq8uh55DGb1RkY/edit?usp=drive_link");
        assertEquals(Optional.of("https://docs.google.com/spreadsheets/d/1HvE18kBGcqc1TadeHiLoNJnCMdg6LSq8uh55DGb1RkY/export?format=xlsx"), resolved);
    }

    @Test
    public void rewritesGoogleDocsLinkToDocxExport() {
        Optional<String> resolved = resolver.resolve("https://docs.google.com/document/d/abc123/edit");
        assertEquals(Optional.of("https://docs.google.com/document/d/abc123/export?format=docx"), resolved);
    }

    @Test
    public void rewritesGoogleSlidesLinkToPptxExport() {
        Optional<String> resolved = resolver.resolve("https://docs.google.com/presentation/d/xyz789/edit#slide=id.p1");
        assertEquals(Optional.of("https://docs.google.com/presentation/d/xyz789/export?format=pptx"), resolved);
    }

    @Test
    public void rewritesGoogleDrawingsLinkToPngExport() {
        Optional<String> resolved = resolver.resolve("https://docs.google.com/drawings/d/draw123/edit");
        assertEquals(Optional.of("https://docs.google.com/drawings/d/draw123/export?format=png"), resolved);
    }

    @Test
    public void passesThroughNonGoogleUrls() {
        assertFalse(resolver.resolve("https://example.com/file.pdf").isPresent());
        assertFalse(resolver.resolve("https://example.com/path/d/abc/edit").isPresent());
    }

    @Test
    public void passesThroughUnsupportedGoogleDocsTypes() {
        assertFalse(resolver.resolve("https://docs.google.com/forms/d/formId/edit").isPresent());
    }

    @Test
    public void handlesNullInput() {
        assertFalse(resolver.resolve(null).isPresent());
    }

    @Test
    public void handlesAccountPrefixedDriveFileLink() {
        Optional<String> resolved = resolver.resolve("https://drive.google.com/u/0/file/d/11CjmQ-ljfnY2qHy4nt9dgvxZp_s1aRnI/view?usp=drive_link");
        assertEquals(Optional.of("https://drive.google.com/uc?export=download&id=11CjmQ-ljfnY2qHy4nt9dgvxZp_s1aRnI"), resolved);
    }

    @Test
    public void handlesAccountPrefixedDriveOpenLink() {
        Optional<String> resolved = resolver.resolve("https://drive.google.com/u/2/uc?id=11CjmQ-ljfnY2qHy4nt9dgvxZp_s1aRnI&export=download");
        assertEquals(Optional.of("https://drive.google.com/uc?export=download&id=11CjmQ-ljfnY2qHy4nt9dgvxZp_s1aRnI"), resolved);
    }

    @Test
    public void handlesAccountPrefixedDocsLink() {
        Optional<String> resolved = resolver.resolve("https://docs.google.com/u/1/spreadsheets/d/1HvE18kBGcqc1TadeHiLoNJnCMdg6LSq8uh55DGb1RkY/edit?usp=drive_link");
        assertEquals(Optional.of("https://docs.google.com/spreadsheets/d/1HvE18kBGcqc1TadeHiLoNJnCMdg6LSq8uh55DGb1RkY/export?format=xlsx"), resolved);
    }
}
