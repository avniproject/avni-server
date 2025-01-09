package org.avni.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.util.ObjectMapperSingleton;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class MetadataBundleAndFileHandler {
    private static final ObjectMapper objectMapper = ObjectMapperSingleton.getObjectMapper();

    public File extractZip(MultipartFile zipFile) throws IOException {
        return this.extractZip(zipFile.getInputStream());
    }

    public File extractZip(InputStream inputStream) throws IOException {
        File tempDir = Files.createTempDirectory("metadata-zip").toFile();

        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    File file = new File(tempDir, entry.getName());
                    File parentDir = file.getParentFile();
                    if (!parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    try (OutputStream outputStream = new FileOutputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zipInputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                    }
                }
                zipInputStream.closeEntry();
            }
        }
        return tempDir;
    }

    protected List<File> listJsonFiles(File directory) {
        List<File> jsonFiles = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    jsonFiles.addAll(listJsonFiles(file));
                } else if (file.isFile() && file.getName().toLowerCase().endsWith(".json")) {
                    jsonFiles.add(file);
                }
            }
        }
        return jsonFiles;
    }

    /**
     * Returns a map of relative path of the JSON file in the zip, to a map of UUID and JSON file content
     **/
    public Map<String, Map<String, Object>> parseJsonFiles(List<File> files, File rootDir) throws IOException {
        Map<String, Map<String, Object>> jsonMap = new HashMap<>();

        for (File file : files) {
            String relativePath = getRelativePath(file, rootDir);
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                StringBuilder jsonContent = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }

                Map<String, Object> jsonMapFile = new HashMap<>();
                if (jsonContent.toString().trim().startsWith("[")) {
                    List<Map<String, Object>> jsonArray = objectMapper.readValue(jsonContent.toString(), new TypeReference<List<Map<String, Object>>>() {});
                    for (Map<String, Object> jsonObject : jsonArray) {
                        String uuid = (String) jsonObject.get("uuid");
                        if (uuid != null) {
                            jsonObject.remove("filename");
                            jsonMapFile.put(uuid, jsonObject);
                        }
                    }
                } else {
                    Map<String, Object> jsonObject = objectMapper.readValue(jsonContent.toString(), new TypeReference<Map<String, Object>>() {});
                    String uuid = (String) jsonObject.get("uuid");
                    if (uuid != null) {
                        jsonObject.remove("filename");
                        jsonMapFile.put(uuid, jsonObject);
                    }
                }
                jsonMap.put(relativePath, jsonMapFile);
            }
        }
        return jsonMap;
    }

    private String getRelativePath(File file, File rootDir) {
        String filePath = file.getPath();
        String rootPath = rootDir.getPath();
        return filePath.substring(rootPath.length() + 1);
    }
}
