package org.avni.messaging.controller;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MetadataDiffController {

    public static Map<String, Object> compareZipJsonFiles(String zip1Path, String zip2Path) throws IOException {
        String extractDir1 = "zip1_contents";
        String extractDir2 = "zip2_contents";
        Map<String, Object> result = new HashMap<>();
        try {
            extractZip(zip1Path, extractDir1);
            extractZip(zip2Path, extractDir2);

            List<File> files1 = listJsonFiles(new File(extractDir1));
            List<File> files2 = listJsonFiles(new File(extractDir2));

            Set<String> fileNames1 = new HashSet<>();
            for (File file : files1) {
                fileNames1.add(file.getName());
            }

            Set<String> fileNames2 = new HashSet<>();
            for (File file : files2) {
                fileNames2.add(file.getName());
            }

            Set<String> missingInZip1 = new HashSet<>(fileNames2);
            missingInZip1.removeAll(fileNames1);

            Set<String> missingInZip2 = new HashSet<>(fileNames1);
            missingInZip2.removeAll(fileNames2);


            result.put("missingInZip1", missingInZip1);
            result.put("missingInZip2", missingInZip2);

            Set<String> commonFiles = new HashSet<>(fileNames1);
            commonFiles.retainAll(fileNames2);

            Map<String, Object> fileDiffs = new HashMap<>();
            for (String fileName : commonFiles) {
                File file1 = new File(extractDir1, fileName);
                File file2 = new File(extractDir2, fileName);

                if (file1.exists() && file2.exists()) {

                    Map<String, Object> leftMap = readJsonFile(file1);
                    Map<String, Object> rightMap = readJsonFile(file2);
                    Map<String, Object> diff = compareMaps(leftMap, rightMap);
                    if (!diff.isEmpty()) {
                        fileDiffs.put(fileName, diff);
                    }
                }
            }
        result.put("fileDiffs", fileDiffs);

        }catch(Exception e){
            System.out.println("Got error");
        }
        return result;
    }

    private static void extractZip(String zipFilePath, String destDir) throws IOException {
        File destDirectory = new File(destDir);
        if (!destDirectory.exists()) {
            destDirectory.mkdir();
        }

        try (ZipFile zipFile = new ZipFile(zipFilePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryDestination = new File(destDir, entry.getName());

                if (entry.isDirectory()) {
                    entryDestination.mkdirs();
                } else {
                    entryDestination.getParentFile().mkdirs();
                    try (InputStream in = zipFile.getInputStream(entry);
                         OutputStream out = new FileOutputStream(entryDestination)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                }
            }
        }
    }

    private static List<File> listJsonFiles(File dir) {
        List<File> jsonFiles = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    jsonFiles.addAll(listJsonFiles(file));
                } else if (file.getName().endsWith(".json")) {
                    jsonFiles.add(file);
                }
            }
        }
        return jsonFiles;
    }

    private static Map<String, Object> readJsonFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        }
        // Convert JSON string to Map (you can customize this parsing logic as per your JSON structure)
        return parseJson(content.toString());
    }

    private static Map<String, Object> parseJson(String json) {
        // Simple JSON parsing logic (you can enhance this based on your JSON structure)
        // This example assumes a flat JSON structure with only String keys and values
        Map<String, Object> map = new HashMap<>();
        String[] pairs = json.replaceAll("[{}\"]", "").split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                map.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return map;
    }

    private static Map<String, Object> compareMaps(Map<String, Object> leftMap, Map<String, Object> rightMap) {
        Map<String, Object> diff = new HashMap<>();
        Set<String> allKeys = new HashSet<>(leftMap.keySet());
        allKeys.addAll(rightMap.keySet());

        for (String key : allKeys) {
            Object leftValue = leftMap.get(key);
            Object rightValue = rightMap.get(key);
            if (leftValue == null) {
                diff.put(key, "Missing in left JSON: " + rightValue);
            } else if (rightValue == null) {
                diff.put(key, "Missing in right JSON: " + leftValue);
            } else if (!leftValue.equals(rightValue)) {
                diff.put(key, "Difference: left=" + leftValue + ", right=" + rightValue);
            }
        }
        return diff;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java org.avni.messaging.controller.MetadataDiffController <path_to_first_zip> <path_to_second_zip>");
            System.exit(1);
        }

        String zip1Path = args[0];
        String zip2Path = args[1];

        Map<String, Object> differences = compareZipJsonFiles(zip1Path, zip2Path);

        String resultJson = formatResultJson(differences);
        System.out.println(resultJson);
    }

    private static String formatResultJson(Map<String, Object> differences) {
        StringBuilder sb = new StringBuilder("{\n");
        for (Map.Entry<String, Object> entry : differences.entrySet()) {
            sb.append("  \"").append(entry.getKey()).append("\": ").append(entry.getValue()).append(",\n");
        }
        sb.deleteCharAt(sb.lastIndexOf(","));
        sb.append("\n}");
        return sb.toString();
    }
}
