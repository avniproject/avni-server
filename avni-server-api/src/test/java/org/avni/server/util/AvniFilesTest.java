package org.avni.server.util;

import org.junit.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.avni.server.util.AvniFiles.APP_ZIP;
import static org.avni.server.util.AvniFiles.validateFileName;

public class AvniFilesTest {

    @Test
    public void validateMimeTypeShouldDetectFilesCorrectly() throws IOException {
        AvniFiles.validateMimeType(readFile("sample-files/csv.csv", "text/csv"), "text/csv");

        AvniFiles.validateMimeType(readFile("sample-files/text.txt", "text/plain"), "text/plain");
        AvniFiles.validateMimeType(readFile("sample-files/javascript.js", "application/javascript"), "application/javascript");
        AvniFiles.validateMimeType(readFile("sample-files/json.json", "application/json"), "application/json");
        AvniFiles.validateMimeType(readFile("sample-files/angled.xml", "application/xml"), "application/xml");
        AvniFiles.validateMimeType(readFile("sample-files/phpfile.php", "text/html"), "text/html");
        AvniFiles.validateMimeType(readFile("sample-files/spreadsheet.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        AvniFiles.validateMimeType(readFile("sample-files/document.pdf", "application/pdf"), "application/pdf");
    }

    @Test
    public void validateMimeTypeShouldUnderstandZipFiles() throws IOException {
        AvniFiles.validateMimeType(readFile("sample-files/compressed.zip", AvniFiles.APP_ZIP), AvniFiles.APP_ZIP);
    }

    @Test (expected = BadRequestError.class)
    public void validateMimeTypeShouldFailWhenMimeTypesDontMatchFileName() throws IOException {
        AvniFiles.validateMimeType(readFile("sample-files/text.txt", "text/csv"), "text/csv");
    }

    @Test
    public void validateMimeTypeDoesNotFailWhenTextFilesMasqueradeAsCsv() throws IOException {
        AvniFiles.validateMimeType(readFile("sample-files/text.csv", "text/csv"), "text/csv");
    }

    @Test
    public void vdalidateMimeTypeDoesNotFailForDoubleExtensions() throws IOException {
        AvniFiles.validateMimeType(readFile("sample-files/text.csv.csv", "text/csv"), "text/csv");
    }

    @Test (expected = BadRequestError.class)
    public void validateMimeTypeShouldFailWhenIncorrectExtensionProvided() throws IOException {
        AvniFiles.validateMimeType(readFile("sample-files/csv.txt", "text/csv"), "text/csv");
    }

    @Test (expected = BadRequestError.class)
    public void validateMimeTypeShouldFailForNonZipFilesProvided() throws IOException {
        AvniFiles.validateMimeType(readFile("sample-files/document.pdf", "application/pdf"), APP_ZIP);
    }

    @Test
    public void validateFileNameShouldWorkForGoodFiles() {
        validateFileName("abc.zip", "zip");
    }

    @Test (expected = BadRequestError.class)
    public void validateFileNameShouldFailForDoubleDotFiles() {
        validateFileName("abc.zip.zip", "zip");
    }

    @Test (expected = BadRequestError.class)
    public void  validateFileNameShouldFailForIncorrectExtension() {
        validateFileName("abc.zip", "csv");
    }

    private MultipartFile readFile(String fileName, String expectedMimeType) throws IOException {
        return new MockMultipartFile(fileName, "/home/users/someName" + fileName, expectedMimeType, getClass().getClassLoader().getResourceAsStream(fileName));
    }
}
