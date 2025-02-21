package org.avni.server.web;

import org.avni.server.domain.metadata.MetadataChangeReport;
import org.avni.server.service.MetadataDiffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/web")
public class MetadataDiffController {
    private final MetadataDiffService metadatadiffService;

    @Autowired
    public MetadataDiffController(MetadataDiffService metadatadiffService) {
        this.metadatadiffService = metadatadiffService;
    }

    @PostMapping("/bundle/findChanges")
    @PreAuthorize("hasAnyAuthority('user')")
    public ResponseEntity<?> compareMetadataZips(@RequestParam("candidateBundle") MultipartFile candidateBundle) throws IOException {
        MetadataChangeReport metadataChangeReport = metadatadiffService.findChangesInBundle(candidateBundle);
        return ResponseEntity.ok(metadataChangeReport);
    }
}
