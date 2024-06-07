package org.avni.server.web;

import org.avni.server.service.MetabaseService;
import org.avni.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/metabase")
public class MetabaseController {

    private final Logger logger = LoggerFactory.getLogger(MetabaseController.class);
    private final MetabaseService metabaseService;
    private final UserService userService;

    public MetabaseController(MetabaseService metabaseService, UserService userService) {
        this.metabaseService = metabaseService;
        this.userService = userService;
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setupMetabase() {
        logger.info("Received request to setup Metabase");
        try {
            if (userService.isDefaultSuperAdmin()) {
                metabaseService.setupMetabase();
                return ResponseEntity.ok("Metabase setup completed for all organisations.");
            } else {
                return ResponseEntity.status(403).body("Access denied. Only the default super admin can access this endpoint.");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
