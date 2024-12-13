package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.dao.CommentRepository;
import org.avni.server.dao.CommentThreadRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.Comment;
import org.avni.server.domain.CommentThread;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.service.CommentService;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.UserService;
import org.avni.server.web.request.CommentContract;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;

@RestController
public class CommentController extends AbstractController<Comment> implements RestControllerResourceProcessor<Comment> {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CommentController.class);
    private final CommentRepository commentRepository;
    private final IndividualRepository individualRepository;
    private final CommentService commentService;
    private final SubjectTypeRepository subjectTypeRepository;
    private final UserService userService;
    private final CommentThreadRepository commentThreadRepository;
    private final ScopeBasedSyncService<Comment> scopeBasedSyncService;

    @Autowired
    public CommentController(CommentRepository commentRepository,
                             IndividualRepository individualRepository,
                             CommentService commentService,
                             SubjectTypeRepository subjectTypeRepository,
                             UserService userService,
                             CommentThreadRepository commentThreadRepository, ScopeBasedSyncService<Comment> scopeBasedSyncService) {
        this.commentRepository = commentRepository;
        this.individualRepository = individualRepository;
        this.commentService = commentService;
        this.subjectTypeRepository = subjectTypeRepository;
        this.userService = userService;
        this.commentThreadRepository = commentThreadRepository;
        this.scopeBasedSyncService = scopeBasedSyncService;
    }

    @GetMapping(value = "/web/comment")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    @Transactional
    public List<Comment> getCommentsForSubject(@RequestParam(value = "commentThreadId") Long threadId) {
        return commentRepository.findByIsVoidedFalseAndCommentThreadIdOrderByLastModifiedDateTimeAscIdAsc(threadId);
    }

    @PostMapping(value = "/web/comment")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    @Transactional
    public ResponseEntity<Comment> createComment(@RequestBody CommentContract commentContract) {
        return ResponseEntity.ok(commentService.saveComment(commentContract));
    }

    @PutMapping(value = "/web/comment/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    @Transactional
    public ResponseEntity<Comment> editComment(@PathVariable Long id, @RequestBody CommentContract commentContract) {
        Optional<Comment> comment = commentRepository.findById(id);
        if (!comment.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(commentService.editComment(commentContract, comment.get()));
    }

    @DeleteMapping(value = "/web/comment/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    @Transactional
    public ResponseEntity<Comment> deleteComment(@PathVariable Long id) {
        Optional<Comment> comment = commentRepository.findById(id);
        if (!comment.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(commentService.deleteComment(comment.get()));
    }

    @RequestMapping(value = "/comments", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public void save(@RequestBody CommentContract commentContract) {
        logger.info(String.format("Saving comment with UUID %s", commentContract.getUuid()));
        Individual subject = individualRepository.findByUuid(commentContract.getSubjectUUID());
        CommentThread commentThread = commentThreadRepository.findByUuid(commentContract.getCommentThreadUUID());
        Comment comment = newOrExistingEntity(commentRepository, commentContract, new Comment());
        comment.setText(commentContract.getText());
        comment.setSubject(subject);
        comment.setCommentThread(commentThread);
        comment.setVoided(commentContract.isVoided());
        commentRepository.save(comment);
        logger.info(String.format("Saved comment with UUID %s", commentContract.getUuid()));
    }

    @GetMapping(value = {"/comment/v2"})
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public SlicedResources<EntityModel<Comment>> getCommentsByOperatingIndividualScopeAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid") String subjectTypeUuid,
            Pageable pageable) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) {
            return wrap(new SliceImpl<>(Collections.emptyList()));
        }
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocationAsSlice(commentRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.Comment));
    }

    @GetMapping(value = {"/comment"})
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<Comment>> getCommentsByOperatingIndividualScope(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid") String subjectTypeUuid,
            Pageable pageable) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) {
            return wrap(new PageImpl<>(Collections.emptyList()));
        }
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocation(commentRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.Comment));
    }

    @Override
    public EntityModel<Comment> process(EntityModel<Comment> resource) {
        Comment comment = resource.getContent();
        CommentThread commentThread = comment.getCommentThread();
        resource.removeLinks();
        resource.add(Link.of(comment.getSubjectUUID(), "individualUUID"));
        if (commentThread != null) {
            resource.add(Link.of(commentThread.getUuid(), "commentThreadUUID"));
        }
        addAuditFields(comment, resource);
        return resource;
    }

}
