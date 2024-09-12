package org.avni.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class MetadataDiffService {

    private static final Logger logger = LoggerFactory.getLogger(MetadataDiffService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> compareMetadataZips(MultipartFile zipFile1, MultipartFile zipFile2) throws IOException {
        Map<String, Object> result = new HashMap<>();
        File tempDir1 = null, tempDir2 = null;

        try {
            tempDir1 = extractZip(zipFile1);
            tempDir2 = extractZip(zipFile2);

            List<File> files1 = listJsonFiles(tempDir1);
            List<File> files2 = listJsonFiles(tempDir2);

            Map<String, Map<String, Object>> jsonMap1 = parseJsonFiles(files1, tempDir1);
            Map<String, Map<String, Object>> jsonMap2 = parseJsonFiles(files2, tempDir2);

            Set<String> fileNames1 = jsonMap1.keySet();
            Set<String> fileNames2 = jsonMap2.keySet();

            Set<String> commonFileNames = new HashSet<>(fileNames1);
            commonFileNames.retainAll(fileNames2);

            for (String fileName : commonFileNames) {
                Map<String, Object> jsonMapFile1 = jsonMap1.get(fileName);
                Map<String, Object> jsonMapFile2 = jsonMap2.get(fileName);

                if (jsonMapFile1 != null && jsonMapFile2 != null) {
                    Map<String, Object> fileDifferences = findDifferences(jsonMapFile1, jsonMapFile2);
                    if (!fileDifferences.isEmpty()) {
                        result.put(fileName, fileDifferences);
                    }
                }
            }

            Set<String> missingInZip1 = findMissingFiles(fileNames1, fileNames2);
            if (!missingInZip1.isEmpty()) {
                result.putAll(missingFilesMap(missingInZip1, "Missing Files in UAT ZIP"));
            }

            Set<String> missingInZip2 = findMissingFiles(fileNames2, fileNames1);
            if (!missingInZip2.isEmpty()) {
                result.putAll(missingFilesMap(missingInZip2, "Missing Files in PROD ZIP"));
            }

        } catch (IOException e) {
            logger.error("Error comparing metadata ZIPs: " + e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Error comparing metadata ZIPs: " + e.getMessage());
            result.put("error", errorResult);
        } finally {
            if (tempDir1 != null) {
                deleteDirectory(tempDir1);
            }
            if (tempDir2 != null) {
                deleteDirectory(tempDir2);
            }
        }
        return result;
    }

    private File extractZip(MultipartFile zipFile) throws IOException {
        File tempDir = Files.createTempDirectory("metadata-zip").toFile();

        try (ZipInputStream zipInputStream = new ZipInputStream(zipFile.getInputStream())) {
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

    private List<File> listJsonFiles(File directory) {
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

    private Map<String, Map<String, Object>> parseJsonFiles(List<File> files, File rootDir) throws IOException {
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

    protected Map<String, Object> findDifferences(Map<String, Object> jsonMap1, Map<String, Object> jsonMap2) {
        Map<String, Object> differences = new HashMap<>();
        boolean hasDifferences = false;
        String uuid = "null";
        for (Map.Entry<String, Object> entry : jsonMap1.entrySet()) {
            uuid = entry.getKey();
            Object json1 = entry.getValue();
            Object json2 = jsonMap2.get(uuid);
            if (json2 != null) {
                Map<String, Object> diff = findJsonDifferences(castToStringObjectMap(json1), castToStringObjectMap(json2));
                if (!diff.isEmpty()) {
                    differences.put(uuid, diff);
                    hasDifferences = true;
                }
            } else {
                differences.put(uuid, createFieldDiff( json1, null,"removed"));
                hasDifferences = true;
            }
        }

        for (Map.Entry<String, Object> entry : jsonMap2.entrySet()) {
            String uuid2 = entry.getKey();
            if (!jsonMap1.containsKey(uuid2)) {
                differences.put(uuid2, createFieldDiff(null, entry.getValue(), "added"));
                hasDifferences = true;
            }
        }

        if (!hasDifferences) {
            differences.put(uuid, createFieldDiff(null, null, "noModification"));
        }
        return differences;
    }

    protected Map<String, Object> findJsonDifferences(Map<String, Object> json1, Map<String, Object> json2) {
        Map<String, Object> differences = new LinkedHashMap<>();
        if (json1 == null && json2 == null) {
            return differences;
        }

        if (json1 == null) {
            json2.forEach((key, value) -> differences.put(key, createFieldDiff(null, value, "added")));
            return differences;
        }

        if (json2 == null) {
            json1.forEach((key, value) -> differences.put(key, createFieldDiff(value, null, "removed")));
            return differences;
        }

        for (Map.Entry<String, Object> entry : json1.entrySet()) {
            String key = entry.getKey();
            Object value1 = entry.getValue();
            Object value2 = json2.get(key);

            if (key.equals("id")) {
                continue;
            }
            if (value2 == null) {
                differences.put(key, createFieldDiff(value1, null, "removed"));
            } else {
                if (value1 instanceof Map && value2 instanceof Map) {
                    Map<String, Object> subDiff = findJsonDifferences((Map<String, Object>) value1, (Map<String, Object>) value2);
                    if (!subDiff.isEmpty()) {
                        differences.put(key, createObjectDiff((Map<String, Object>) value1, (Map<String, Object>) value2, "modified"));
                    }
                } else if (value1 instanceof List && value2 instanceof List) {
                    List<Map<String, Object>> listDiff = findArrayDifferences((List<Object>) value1, (List<Object>) value2);
                    if (!listDiff.isEmpty()) {
                        differences.put(key, createArrayDiff((List<Object>) value1, (List<Object>) value2, "modified"));
                    }
                } else if (!value1.equals(value2)) {
                    differences.put(key, createFieldDiff(value1, value2, "modified"));
                }
            }
        }

        for (Map.Entry<String, Object> entry : json2.entrySet()) {
            String key = entry.getKey();
            if (!json1.containsKey(key)) {
                differences.put(key, createFieldDiff(null, entry.getValue(), "added"));
            }
        }

        return differences;
    }

    protected List<Map<String, Object>> findArrayDifferences(List<Object> array1, List<Object> array2) {
        List<Map<String, Object>> differences = new ArrayList<>();

        Function<Map<String, Object>, String> getUuid = obj -> (String) obj.get("uuid");

        Map<String, Map<String, Object>> map1 = array1.stream()
                .filter(obj -> obj instanceof Map)
                .map(obj -> (Map<String, Object>) obj)
                .collect(Collectors.toMap(getUuid, Function.identity(), (e1, e2) -> e1));

        Map<String, Map<String, Object>> map2 = array2.stream()
                .filter(obj -> obj instanceof Map)
                .map(obj -> (Map<String, Object>) obj)
                .collect(Collectors.toMap(getUuid, Function.identity(), (e1, e2) -> e1));

        for (String uuid : map2.keySet()) {
            if (!map1.containsKey(uuid)) {
                differences.add(createFieldDiff(null, map2.get(uuid), "added"));
            } else {
                Map<String, Object> obj1 = map1.get(uuid);
                Map<String, Object> obj2 = map2.get(uuid);

                Map<String, Object> subDiff = findJsonDifferences(obj1, obj2);
                if (!subDiff.isEmpty()) {
                    differences.add(createObjectDiff(obj1, obj2, "modified"));
                }
            }
        }

        for (String uuid : map1.keySet()) {
            if (!map2.containsKey(uuid)) {
                differences.add(createFieldDiff(map1.get(uuid), null, "removed"));
            }
        }
        return differences;
    }

    private Map<String, Object> createFieldDiff(Object oldValue, Object newValue, String changeType) {
        Map<String, Object> fieldDiff = new LinkedHashMap<>();

        if(!"noModification".equals(changeType)) {
            if (oldValue == null && newValue != null) {
                fieldDiff.put("dataType", getDataType(newValue));
            } else if (oldValue != null && newValue == null) {
                fieldDiff.put("dataType", getDataType(oldValue));
            } else if (oldValue != null && newValue != null) {
                fieldDiff.put("dataType", getDataType(newValue));
            } else {
                fieldDiff.put("dataType", "object");
            }
        }
        fieldDiff.put("changeType", changeType);
        if (oldValue != null) {
            fieldDiff.put("oldValue", oldValue);
        }
        if (newValue != null) {
            fieldDiff.put("newValue", newValue);
        }
        return fieldDiff;
    }

    private Map<String, Object> createObjectDiff(Map<String, Object> oldValue, Map<String, Object> newValue, String changeType) {
        Map<String, Object> objectDiff = new LinkedHashMap<>();
        Map<String, Object> fieldsDiff = findDifferences(oldValue, newValue);

        if (!fieldsDiff.isEmpty() && !"noModification".equals(changeType)) {
            objectDiff.put("dataType", "object");
            objectDiff.put("changeType", changeType);
            objectDiff.put("fields", fieldsDiff);
        }
        return objectDiff;
    }

    private Map<String, Object> createArrayDiff(List<Object> oldValue, List<Object> newValue, String changeType) {
        Map<String, Object> arrayDiff = new LinkedHashMap<>();

        List<Map<String, Object>> itemsDiff = findArrayDifferences(oldValue, newValue);
        if (!itemsDiff.isEmpty() && !"noModification".equals(changeType)) {
            arrayDiff.put("dataType", "array");
            arrayDiff.put("changeType", changeType);
            arrayDiff.put("items", itemsDiff);
        }
        return arrayDiff;
    }

    protected Set<String> findMissingFiles(Set<String> fileNames1, Set<String> fileNames2) {
        Set<String> missingFiles = new HashSet<>(fileNames1);
        missingFiles.removeAll(fileNames2);
        return missingFiles;
    }

    protected Map<String, Object> missingFilesMap(Set<String> missingFiles, String message) {
        Map<String, Object> missingFilesMap = new LinkedHashMap<>();
        missingFilesMap.put(message, missingFiles);
        return missingFilesMap;
    }

    protected void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    private String getDataType(Object value) {
        if (value instanceof Map) {
            return "object";
        } else if (value instanceof List) {
            return "array";
        } else {
            return "primitive";
        }
    }

    private Map<String, Object> castToStringObjectMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return new HashMap<>();
    }
}
