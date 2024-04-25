package org.avni.server.service;

import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.S3File;
import org.avni.server.util.S3FileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static java.lang.String.format;

@Service
public class BulkUploadS3Service {
    private final S3Service s3Service;

    @Autowired
    public BulkUploadS3Service(@Qualifier("BatchS3Service") S3Service s3Service) {
        this.s3Service = s3Service;
    }

    public ObjectInfo uploadFile(MultipartFile source, String uuid) throws IOException {
        String targetFileName = format("%s-%s", uuid, source.getOriginalFilename());
        List<String> trimmedLines = readAndTrimCSVFile(source.getInputStream());
        String trimmedCSVContent = String.join("\n", trimmedLines);
        return s3Service.uploadString(trimmedCSVContent, targetFileName, "bulkuploads/input");
    }

    private List<String> readAndTrimCSVFile(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines()
                .map(line -> {
                    // Split the line into fields
                    String[] fields = line.split(",");
                    // Trim the username and email fields (assuming they are at specific indexes)
                    if (fields.length >= 2) {
                        fields[0] = fields[0].trim(); // Assuming username is at index 0
                        fields[1] = fields[1].trim(); // Assuming email is at index 1
                    }
                    // Join the trimmed fields back to a line
                    return String.join(",", fields);
                })
                .collect(Collectors.toList());
        }
    }

    public ObjectInfo uploadZip(MultipartFile source, String uuid) throws IOException {
        String targetFileName = format("%s-%s", uuid, source.getOriginalFilename());
        return s3Service.uploadZipFile(source, targetFileName, "bulkuploads/input");
    }

    public ObjectInfo uploadErrorFile(File tempSourceFile, String uuid) throws IOException {
        return s3Service.uploadFile(tempSourceFile, format("%s.csv", uuid), "bulkuploads/error");
    }

    public File getLocalErrorFile(String uuid) {
        File errorDir = new File(format("%s/bulkuploads/error", System.getProperty("java.io.tmpdir")));
        errorDir.mkdirs();
        return new File(errorDir, format("%s.csv", uuid));
    }

    public InputStream downloadErrorFile(String jobUuid) {
        S3File s3File = S3File.organisationFile(UserContextHolder.getOrganisation(), format("%s.csv", jobUuid), S3FileType.BulkUploadsError);
        return s3Service.getFileStream(s3File);
    }
}
