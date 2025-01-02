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
@RequestMapping("/web")
public class MetadataDiffController {
    private final MetadataDiffService metadatadiffService;

    @Autowired
    public MetadataDiffController(MetadataDiffService metadatadiffService) {
        this.metadatadiffService = metadatadiffService;
    }

    @PostMapping("/bundle/findChanges")
    @PreAuthorize("hasAnyAuthority('user')")
    public ResponseEntity<?> compareMetadataZips(@RequestParam("incumbentBundle") MultipartFile incumbentBundle) throws IOException {
        Map<String, Object> result = metadatadiffService.findChangesInBundle(incumbentBundle);
        return ResponseEntity.ok(result);
    }
}
