package org.avni.server.web;

import com.amazonaws.HttpMethod;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FilenameUtils;
import org.avni.server.dao.GroupRepository;
import org.avni.server.dao.UserGroupRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.StorageDataClass;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.S3Service;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.media.MediaFolder;
import org.avni.server.service.storage.StorageServiceProvider;
import org.avni.server.util.AvniFiles;
import org.avni.server.util.BadRequestError;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.List;

import static java.lang.String.format;

@RestController
public class MediaController {
    private static final String SQLITE_MIGRATION_GROUP = "SQLite Migration";
    private static final Pattern MODEL_FILE_NAME = Pattern.compile("^[0-9a-f]{64}\\.bin$");
    private static final Pattern MODEL_RELATIVE_KEY = Pattern.compile("^models/[0-9a-f]{64}\\.bin$");
    private final Logger logger;
    private final S3Service s3Service;
    private final StorageServiceProvider storageServiceProvider;
    private final AccessControlService accessControlService;
    private final ErrorBodyBuilder errorBodyBuilder;
    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;

    @Autowired
    public MediaController(S3Service s3Service, StorageServiceProvider storageServiceProvider,
                           AccessControlService accessControlService, ErrorBodyBuilder errorBodyBuilder,
                           GroupRepository groupRepository, UserGroupRepository userGroupRepository) {
        this.s3Service = s3Service;
        this.storageServiceProvider = storageServiceProvider;
        this.accessControlService = accessControlService;
        this.errorBodyBuilder = errorBodyBuilder;
        this.groupRepository = groupRepository;
        this.userGroupRepository = userGroupRepository;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    private S3Service storageFor(String keyOrUrl) {
        return storageServiceProvider.forDataClass(StorageDataClass.dataClassForKey(keyOrUrl));
    }

    private String authorizedModelKey(String fileName) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        if (fileName == null || !MODEL_FILE_NAME.matcher(fileName).matches()) {
            throw new BadRequestError("Invalid model file name '%s'. Expected <sha256>.bin.", fileName);
        }
        return format("%s/%s", StorageDataClass.MODEL_NAMESPACE, fileName);
    }

    @RequestMapping(value = "/media/uploadUrl/{fileName:.+}", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> generateUploadUrl(@PathVariable String fileName) {
        logger.info("getting media upload url");
        if (StorageDataClass.dataClassForKey(fileName) == StorageDataClass.MODEL) {
            String modelKey = authorizedModelKey(FilenameUtils.getName(fileName));
            return getFileUrlResponse(modelKey, HttpMethod.PUT, storageServiceProvider.forDataClass(StorageDataClass.MODEL));
        }
        return getFileUrlResponse(fileName, HttpMethod.PUT, s3Service);
    }

    private ResponseEntity<String> getFileUrlResponse(String fileName, HttpMethod method) {
        return getFileUrlResponse(fileName, method, s3Service);
    }

    private ResponseEntity<String> getFileUrlResponse(String fileName, HttpMethod method, S3Service storageService) {
        try {
            URL url = storageService.generateMediaUploadUrl(fileName, method);
            logger.debug(format("Generating pre-signed url: %s", url.toString()));
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(url.toString());
        } catch (AccessDeniedException e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBodyBuilder.getErrorMessageBody(e));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(e));
        }
    }

    @RequestMapping(value = "/media/mobileDatabaseBackupUrl/upload", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> generateMobileDatabaseBackupUploadUrl() {
        logger.info("getting mobile database backup upload url");
        try {
            return getFileUrlResponse(mobileDatabaseBackupFile(), HttpMethod.PUT);
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
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
    @Transactional(readOnly = true)
    public ResponseEntity<String> generateMobileDatabaseBackupDownloadUrl() {
        logger.info("getting mobile database backup download url");
        try {
            return getFileUrlResponse(mobileDatabaseBackupFile(), HttpMethod.GET);
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @RequestMapping(value = "/media/mobileDatabaseBackupUrl/exists", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> mobileDatabaseBackupExists() {
        logger.info("checking whether mobile database backup url exists");
        try {
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Boolean.toString(s3Service.fileExists(mobileDatabaseBackupFile())));
        } catch (ValidationException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @RequestMapping(value = "/media/mobileDatabaseSqliteSnapshotUrl/exists", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> mobileDatabaseSqliteSnapshotExists() {
        logger.info("checking whether sqlite snapshot exists");
        try {
            boolean eligible = currentUserIsInSqliteMigrationGroup()
                && s3Service.fileExists(sqliteSnapshotRelativeKey());
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Boolean.toString(eligible));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(e));
        }
    }

    @RequestMapping(value = "/media/mobileDatabaseSqliteSnapshotUrl/download", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> generateMobileDatabaseSqliteSnapshotDownloadUrl() {
        logger.info("getting sqlite snapshot download url");
        try {
            URL url = s3Service.generateMediaUploadUrl(sqliteSnapshotRelativeKey(), HttpMethod.GET);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(url.toString());
        } catch (AccessDeniedException e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBodyBuilder.getErrorMessageBody(e));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(e));
        }
    }

    private boolean currentUserIsInSqliteMigrationGroup() {
        User user = UserContextHolder.getUserContext().getUser();
        Group group = groupRepository.findByNameAndOrganisationId(SQLITE_MIGRATION_GROUP, UserContextHolder.getUserContext().getOrganisationId());
        if (group == null) {
            return false;
        }
        return userGroupRepository.findByUserAndGroupAndIsVoidedFalse(user, group) != null;
    }

    private String sqliteSnapshotRelativeKey() {
        User user = UserContextHolder.getUserContext().getUser();
        return format("snapshots/%s/snapshot.db", user.getUsername());
    }

    @RequestMapping(value = "/media/signedUrl", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> generateDownloadUrl(@RequestParam String url) {
        try {
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(storageFor(url).generateMediaDownloadUrl(url).toString());
        } catch (AccessDeniedException e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBodyBuilder.getErrorMessageBody(e));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(e));
        }
    }

    @RequestMapping(value = "/media/signedUrls", method = RequestMethod.POST)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity generateDownloadUrls(@RequestBody List<String> urls) {
        try {
            Map<String, String> signedUrls = urls.stream()
                .collect(Collectors.toMap(
                    url -> url,
                    url -> storageFor(url).generateMediaDownloadUrl(url).toString()
                ));
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(signedUrls);
        } catch (AccessDeniedException e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBodyBuilder.getErrorMessageBody(e));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(e));
        }
    }

    // The device knows only the relative model key (it must stay backend-agnostic), so it cannot use /media/signedUrl which parses a full URL.
    @RequestMapping(value = "/media/modelBlobUrl", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> generateModelBlobUrl(@RequestParam String key) {
        if (key == null || !MODEL_RELATIVE_KEY.matcher(key).matches()) {
            throw new BadRequestError("Invalid model key '%s'. Expected models/<sha256>.bin.", key);
        }
        Organisation organisation = UserContextHolder.getOrganisation();
        S3Service modelBackend = storageServiceProvider.forDataClass(StorageDataClass.MODEL);
        URL url = modelBackend.getURLForExtensions(key, organisation);
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(url.toString());
    }

    //unprotected endpoint
    @RequestMapping(value = "/web/media", method = RequestMethod.GET)
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public void downloadFile(@RequestParam String url, HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {
        User user = UserContextHolder.getUser();
        if (user == null) {
            String originalUrl = format("/web/media?url=%s", request.getParameter("url"));
            String redirectUrl = format("/?redirect_url=%s", URLEncoder.encode(originalUrl, "UTF-8"));
            response.setHeader("Location", redirectUrl);
        } else {
            response.setHeader("Location", storageFor(url).generateMediaDownloadUrl(url).toString());
        }
        response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
    }

    @PostMapping("/web/uploadMedia")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<?> uploadMedia(@RequestParam MultipartFile file,
                                         @RequestParam(value = "parentFolder", required = false) String parentFolder) {
        User user = UserContextHolder.getUserContext().getUser();
        String targetFilePath;
        if (StorageDataClass.MODEL_NAMESPACE.equals(parentFolder)) {
            targetFilePath = authorizedModelKey(FilenameUtils.getName(file.getOriginalFilename()));
        } else if (parentFolder != null && !parentFolder.isEmpty()) {
            throw new BadRequestError("Unsupported parentFolder '%s'.", parentFolder);
        } else {
            String uuid = UUID.randomUUID().toString();
            String fileExtension = FilenameUtils.getExtension(file.getOriginalFilename());
            targetFilePath = format("%s.%s", uuid, fileExtension);
        }
        try {
            File tempSourceFile = AvniFiles.convertMultiPartToFile(file, "");
            URL s3FileUrl = storageFor(targetFilePath).uploadImageFile(tempSourceFile, targetFilePath);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(s3FileUrl.toString());
        } catch (Exception e) {
            logger.error(format("Media upload failed.  file:'%s', user:'%s'", file.getOriginalFilename(), user.getUsername()), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(format("Unable to upload media. %s", e.getMessage())));
        }
    }

    @PostMapping("/media/saveImage")
    public ResponseEntity<?> saveImage(@RequestParam MultipartFile file, @RequestParam String folderName) {
        MediaFolder folder = MediaFolder.valueOfLabel(folderName);
        if (folder == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body(String.format("Unsupported folderName %s", folderName));
        }

        File tempSourceFile;
        try {
            tempSourceFile = AvniFiles.convertMultiPartToFile(file, "");
            AvniFiles.ImageType imageType = AvniFiles.guessImageType(tempSourceFile);
            if (AvniFiles.ImageType.Unknown.equals(imageType)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN)
                        .body(String.format("Unsupported file. File type: %s. Use .bmp, .jpg, .jpeg, .png, .gif file.", imageType));
            }
            if (folder.equals(MediaFolder.ICONS) && isInvalidDimension(tempSourceFile, imageType)) {
                accessControlService.checkHasAnyOfSpecificPrivileges(Arrays.asList(
                    PrivilegeType.EditSubjectType,
                    PrivilegeType.EditOfflineDashboardAndReportCard
                ));
                Dimension imageDimension = AvniFiles.getImageDimension(tempSourceFile, imageType);
                String dimension = imageDimension.getWidth() + " X " + imageDimension.getHeight();
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(String.format("Unsupported file. Image size: %s. Use image of size 75 X 75 or smaller.", dimension));
            }
            if (folder.equals(MediaFolder.NEWS) && isInvalidImageSize(file)) {
                double sizeInKB = AvniFiles.getSizeInKB(file);
                accessControlService.checkPrivilege(PrivilegeType.EditNews);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(String.format("Unsupported file. File size: %s. Use image of size 500KB or smaller.", sizeInKB));
            }
            if (folder.equals(MediaFolder.PROFILE_PICS)) {
                // not checking privileges for specific subject type here as that will be checked while saving the entity
                accessControlService.checkHasAnyOfSpecificPrivileges(Arrays.asList(
                    PrivilegeType.RegisterSubject,
                    PrivilegeType.EditSubject
                ));
            }
            String uuid = UUID.randomUUID().toString();
            String targetFilePath = format("%s/%s%s", folderName, uuid, imageType.EXT);
            URL s3FileUrl = s3Service.uploadImageFile(tempSourceFile, targetFilePath);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(s3FileUrl.toString());
        } catch (Exception e) {
            User user = UserContextHolder.getUserContext().getUser();
            logger.error(format("Image upload failed. folderName: '%s' file:'%s', user:'%s'", folderName, file.getOriginalFilename(), user.getUsername()), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(format("Unable to save Image. %s", e.getMessage())));
        }
    }

    @PostMapping("/media/saveVideo")
    public ResponseEntity<?> saveVideo(@RequestParam MultipartFile file, @RequestParam String folderName) {
        MediaFolder folder = MediaFolder.valueOfLabel(folderName);
        if (folder == null || folder != MediaFolder.MetaData) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body(String.format("Unsupported folderName %s", folderName));
        }

        File tempSourceFile;
        try {
            String mimeType = AvniFiles.detectMimeType(file);
            if (mimeType == null || !mimeType.startsWith("video/")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN)
                        .body(String.format("Unsupported file. File type: %s. Use video file.", mimeType));
            }
            tempSourceFile = AvniFiles.convertMultiPartToFile(file, "");

            String uuid = UUID.randomUUID().toString();
            String targetFilePath = AvniFiles.buildVideoTargetFilePath(folderName, mimeType, uuid);
            URL s3FileUrl = s3Service.uploadImageFile(tempSourceFile, targetFilePath);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(s3FileUrl.toString());
        } catch (Exception e) {
            User user = UserContextHolder.getUserContext().getUser();
            logger.error(format("Video upload failed. folderName: '%s' file:'%s', user:'%s'", folderName, file.getOriginalFilename(), user.getUsername()), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(format("Unable to save Video. %s", e.getMessage())));
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
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public ResponseEntity<InputStreamResource> downloadFile(@RequestParam String s3Url, @RequestParam String fileName) throws IOException {
        InputStreamResource resource = new InputStreamResource(s3Service.getObjectContentFromUrl(s3Url));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + fileName)
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .body(resource);
    }
}
