package org.avni.server.web;

import org.avni.server.builder.VideoBuilder;
import org.avni.server.dao.VideoRepository;
import org.avni.server.domain.Video;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.util.EntityUtil;
import org.avni.server.service.VideoService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.VideoContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.List;

@RestController
public class VideoController {

    private final VideoRepository videoRepository;
    private final VideoService videoService;
    private final AccessControlService accessControlService;

    @Autowired
    public VideoController(VideoRepository videoRepository, VideoService videoService, AccessControlService accessControlService) {
        this.videoRepository = videoRepository;
        this.videoService = videoService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/videos", method = RequestMethod.POST)
    @Transactional
    public void save(@RequestBody VideoContract[] videoContracts) {
        accessControlService.checkPrivilege(PrivilegeType.EditVideo);
        Arrays.stream(videoContracts).map(this::createVideo).forEach(videoRepository::save);
    }

    @RequestMapping(value = "/web/video", method = RequestMethod.GET)
    public List<VideoContract> getAll() {
        return videoService.getAllVideos();
    }

    @RequestMapping(value = "/web/video/{id}", method = RequestMethod.GET)
    public ResponseEntity<VideoContract> getById(@PathVariable Long id) {
        return videoRepository.findById(id)
                .map(video -> ResponseEntity.ok(VideoContract.fromEntity(video)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @RequestMapping(value = "/web/video", method = RequestMethod.POST)
    public ResponseEntity<?> saveVideo(@RequestBody VideoContract videoContract) {
        accessControlService.checkPrivilege(PrivilegeType.EditVideo);
        Video video = videoRepository.findByTitle(videoContract.getTitle());
        if (video != null) {
            return ResponseEntity.badRequest().body(String.format("Record with the name %s already exists", videoContract.getTitle()));
        }
        Video savedVideo = videoService.saveVideo(videoContract);
        return ResponseEntity.ok(VideoContract.fromEntity(savedVideo));
    }

    @RequestMapping(value = "/web/video/{id}", method = RequestMethod.POST)
    public ResponseEntity<?> editVideo(@RequestBody VideoContract videoContract, @PathVariable Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditVideo);
        Video video = videoRepository.getOne(id);
        if (video == null) {
            return ResponseEntity.notFound().build();
        }
        Video savedVideo = videoService.editVideo(videoContract, video);
        return ResponseEntity.ok(VideoContract.fromEntity(savedVideo));
    }

    @RequestMapping(value = "/web/video/{id}", method = RequestMethod.DELETE)
    public ResponseEntity voidVideo(@PathVariable Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditVideo);
        Video video = videoRepository.getOne(id);
        if (video == null) {
            return ResponseEntity.notFound().build();
        }
        video.setVoided(true);
        video.setTitle(EntityUtil.getVoidedName(video.getTitle(), video.getId()));
        videoRepository.save(video);
        return ResponseEntity.ok().build();
    }

    private Video createVideo(VideoContract contract) {
        Video existing = videoRepository.findByUuid(contract.getUuid());
        VideoBuilder builder = new VideoBuilder(existing);
        return builder
                .withUUID(contract.getUuid())
                .withTitle(contract.getTitle())
                .withFilePath(contract.getFilePath())
                .withDescription(contract.getDescription())
                .withDuration(contract.getDuration())
                .build();
    }
}
