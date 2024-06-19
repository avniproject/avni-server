package org.avni.server.web;

import com.amazonaws.HttpMethod;
import org.apache.commons.io.FilenameUtils;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.S3Service;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.AvniFiles;
import org.avni.server.web.util.ErrorBodyBuilder;
import org.avni.server.web.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.UUID;

import static java.lang.String.format;

@RestController
public class MediaController {
    private final Logger logger;
    private final S3Service s3Service;
    private final AccessControlService accessControlService;
    private final ErrorBodyBuilder errorBodyBuilder;

    @Autowired
    public MediaController(S3Service s3Service, AccessControlService accessControlService, ErrorBodyBuilder errorBodyBuilder) {
        this.s3Service = s3Service;
        this.accessControlService = accessControlService;
        this.errorBodyBuilder = errorBodyBuilder;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @RequestMapping(value = "/media/uploadUrl/{fileName:.+}", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<String> generateUploadUrl(@PathVariable String fileName) {
        logger.info("getting media upload url");
        return getFileUrlResponse(fileName, HttpMethod.PUT);
    }

    private ResponseEntity<String> getFileUrlResponse(String fileName, HttpMethod method) {
        try {
            URL url = s3Service.generateMediaUploadUrl(fileName, method);
            logger.debug(format("Generating pre-signed url: %s", url.toString()));
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(url.toString());
        } catch (AccessDeniedException e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBodyBuilder.getErrorMessageBody(e));
        } catch (Exception e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(e));
        }
    }

    @RequestMapping(value = "/media/mobileDatabaseBackupUrl/upload", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<String> generateMobileDatabaseBackupUploadUrl() {
        logger.info("getting mobile database backup upload url");
        return getFileUrlResponse(mobileDatabaseBackupFile(), HttpMethod.PUT);
    }

    private String mobileDatabaseBackupFile() {
        User user = UserContextHolder.getUserContext().getUser();
        if (user.getCatchment() == null) {
            throw new ValidationException("NoCatchmentFound");
        }
        String catchmentUuid = user.getCatchment().getUuid();
        return format("MobileDbBackup-%s", catchmentUuid);
    }

    @RequestMapping(value = "/media/mobileDatabaseBackupUrl/download", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<String> generateMobileDatabaseBackupDownloadUrl() {
        logger.info("getting mobile database backup download url");
        return getFileUrlResponse(mobileDatabaseBackupFile(), HttpMethod.GET);
    }

    @RequestMapping(value = "/media/mobileDatabaseBackupUrl/exists", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<String> mobileDatabaseBackupExists() {
        logger.info("checking whether mobile database backup url exists");
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Boolean.toString(s3Service.fileExists(mobileDatabaseBackupFile())));
    }

    @RequestMapping(value = "/media/signedUrl", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<String> generateDownloadUrl(@RequestParam String url) {
        try {
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(s3Service.generateMediaDownloadUrl(url).toString());
        } catch (AccessDeniedException e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBodyBuilder.getErrorMessageBody(e));
        } catch (Exception e) {
            logger.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(e));
        }
    }

    //unprotected endpoint
    @RequestMapping(value = "/web/media", method = RequestMethod.GET)
    public void downloadFile(@RequestParam String url, HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {
        User user = UserContextHolder.getUser();
        if (user == null) {
            String originalUrl = format("/web/media?url=%s", request.getParameter("url"));
            String redirectUrl = format("/?redirect_url=%s", URLEncoder.encode(originalUrl, "UTF-8"));
            response.setHeader("Location", redirectUrl);
        } else {
            response.setHeader("Location", s3Service.generateMediaDownloadUrl(url).toString());
        }
        response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
    }

    @PostMapping("/web/uploadMedia")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<?> uploadMedia(@RequestParam MultipartFile file) {
        String uuid = UUID.randomUUID().toString();
        User user = UserContextHolder.getUserContext().getUser();
        String fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());
        String targetFilePath = format("%s.%s", uuid, fileExtension);
        try {
            File tempSourceFile = AvniFiles.convertMultiPartToFile(file, "");
            URL s3FileUrl = s3Service.uploadImageFile(tempSourceFile, targetFilePath);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(s3FileUrl.toString());
        } catch (Exception e) {
            logger.error(format("Media upload failed.  file:'%s', user:'%s'", file.getOriginalFilename(), user.getUsername()), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(format("Unable to upload media. %s", e.getMessage())));
        }
    }

    @PostMapping("/media/saveImage")
    public ResponseEntity<?> saveImage(@RequestParam MultipartFile file, @RequestParam String bucketName) {
        String uuid = UUID.randomUUID().toString();
        User user = UserContextHolder.getUserContext().getUser();
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        File tempSourceFile;
        try {
            tempSourceFile = AvniFiles.convertMultiPartToFile(file, "");
            AvniFiles.ImageType imageType = AvniFiles.guessImageTypeFromStream(tempSourceFile);
            if (AvniFiles.ImageType.Unknown.equals(imageType)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body("Unsupported file. Use .bmp, .jpg, .jpeg, .png, .gif file.");
            }
            if (bucketName.equals("icons") && isInvalidDimension(tempSourceFile, imageType)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body("Unsupported file. Use image of size 75 X 75 or smaller.");
            }
            if (bucketName.equals("news") && isInvalidImageSize(file)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body("Unsupported file. Use image of size 500KB or smaller.");
            }
            String targetFilePath = format("%s/%s%s", bucketName, uuid, imageType.EXT);
            URL s3FileUrl = s3Service.uploadImageFile(tempSourceFile, targetFilePath);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(s3FileUrl.toString());
        } catch (Exception e) {
            logger.error(format("Image upload failed. bucketName: '%s' file:'%s', user:'%s'", bucketName, file.getOriginalFilename(), user.getUsername()), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(format("Unable to save Image. %s", e.getMessage())));
        }
    }

    private boolean isInvalidImageSize(MultipartFile file) {
        return AvniFiles.getSizeInKB(file) > 500;
    }

    private boolean isInvalidDimension(File tempSourceFile, AvniFiles.ImageType imageType) throws IOException {
        Dimension dimension = AvniFiles.getImageDimension(tempSourceFile, imageType);
        return dimension.getHeight() > 75 || dimension.getWidth() > 75;
    }

    @GetMapping("/web/media/downloadStream")
    public ResponseEntity<InputStreamResource> downloadFile(@RequestParam String s3Url, @RequestParam String fileName) throws IOException {
        InputStreamResource resource = new InputStreamResource(s3Service.getObjectContentFromUrl(s3Url));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + fileName)
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .body(resource);
    }
}
