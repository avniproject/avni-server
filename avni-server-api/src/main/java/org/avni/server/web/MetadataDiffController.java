package org.avni.server.web;

import org.avni.server.service.MetadataDiffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MetadataDiffController {
    private final MetadataDiffService metadatadiffService;

    @Autowired
    public MetadataDiffController(MetadataDiffService metadatadiffService) {
        this.metadatadiffService = metadatadiffService;
    }

    @PostMapping("/compare-metadata")
    @PreAuthorize("hasAnyAuthority('user')")
    public ResponseEntity<?> compareMetadataZips(@RequestParam("file1") MultipartFile file1,
                                                 @RequestParam("file2") MultipartFile file2) throws IOException {

        Map<String, Object> result = metadatadiffService.compareMetadataZips(file1, file2);

        return ResponseEntity.ok(result);
    }
}
