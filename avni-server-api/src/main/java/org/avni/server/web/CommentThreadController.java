package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.dao.CommentThreadRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.CommentThread;
import org.avni.server.domain.Individual;
import org.avni.server.domain.SubjectType;
import org.avni.server.service.CommentThreadService;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.UserService;
import org.avni.server.web.request.CommentThreadContract;
import org.avni.server.web.response.CommentThreadResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class CommentThreadController extends AbstractController<CommentThread> implements RestControllerResourceProcessor<CommentThread> {

    private static org.slf4j.Logger logger = LoggerFactory.getLogger(CommentThreadController.class);
    private final CommentThreadRepository commentThreadRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final UserService userService;
    private final IndividualRepository individualRepository;
    private final CommentThreadService commentThreadService;
    private final ScopeBasedSyncService<CommentThread> scopeBasedSyncService;
    private final TxDataControllerHelper txDataControllerHelper;

    @Autowired
    public CommentThreadController(CommentThreadRepository commentThreadRepository,
                                   SubjectTypeRepository subjectTypeRepository,
                                   UserService userService,
                                   IndividualRepository individualRepository,
                                   CommentThreadService commentThreadService,
                                   ScopeBasedSyncService<CommentThread> scopeBasedSyncService,
                                   TxDataControllerHelper txDataControllerHelper) {
        this.commentThreadRepository = commentThreadRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.userService = userService;
        this.individualRepository = individualRepository;
        this.commentThreadService = commentThreadService;
        this.scopeBasedSyncService = scopeBasedSyncService;
        this.txDataControllerHelper = txDataControllerHelper;
    }

    @GetMapping(value = {"/commentThread/v2"})
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public SlicedResources<EntityModel<CommentThread>> getCommentThreadsByOperatingIndividualScopeAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid") String subjectTypeUuid,
            Pageable pageable) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) {
            return wrap(new SliceImpl<>(Collections.emptyList()));
        }
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocationAsSlice(commentThreadRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.CommentThread));
    }

    @GetMapping(value = {"/commentThread"})
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<CommentThread>> getCommentThreadsByOperatingIndividualScope(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid") String subjectTypeUuid,
            Pageable pageable) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) {
            return wrap(new PageImpl<>(Collections.emptyList()));
        }
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocation(commentThreadRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.CommentThread));
    }

    @RequestMapping(value = "/commentThreads", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public void save(@RequestBody CommentThreadContract commentThreadContract) {
        logger.info(String.format("Saving comment thread with UUID %s", commentThreadContract.getUuid()));
        CommentThread commentThread = newOrExistingEntity(commentThreadRepository, commentThreadContract, new CommentThread());
        commentThread.setOpenDateTime(commentThreadContract.getOpenDateTime());
        commentThread.setResolvedDateTime(commentThreadContract.getResolvedDateTime());
        commentThread.setStatus(CommentThread.CommentThreadStatus.valueOf(commentThreadContract.getStatus()));
        commentThread.setVoided(commentThreadContract.isVoided());
        commentThreadRepository.save(commentThread);
        logger.info(String.format("Saved comment thread with UUID %s", commentThreadContract.getUuid()));
    }

    @RequestMapping(value = "/web/commentThreads", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public List<CommentThreadResponse> getAllThreads(@RequestParam(value = "subjectUUID") String subjectUUID) {
        Individual subject = individualRepository.findByUuid(subjectUUID);
        return commentThreadRepository.findDistinctByIsVoidedFalseAndCommentsIsVoidedFalseAndComments_SubjectOrderByOpenDateTimeDescIdDesc(subject)
                .stream()
                .map(CommentThreadResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @RequestMapping(value = "/web/commentThread", method = RequestMethod.POST)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<CommentThreadResponse> saveThread(@RequestBody CommentThreadContract threadContract) {
        try {
            if (threadContract.getComments().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            String subjectUUID = threadContract.getComments().iterator().next().getSubjectUUID();
            Individual individual = individualRepository.findByUuid(subjectUUID);
            if (individual == null) {
                return ResponseEntity.badRequest().build();
            }
            txDataControllerHelper.checkSubjectAccess(individual);
            CommentThread savedThread = commentThreadService.createNewThread(threadContract);
            return ResponseEntity.ok(CommentThreadResponse.fromEntity(savedThread));
        } catch (TxDataControllerHelper.TxDataPartitionAccessDeniedException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResponseEntity.status(403).build();
        }
    }

    @RequestMapping(value = "/web/commentThread/{id}/resolve", method = RequestMethod.PUT)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<CommentThreadResponse> editThread(@PathVariable Long id) {
        try {
            Optional<CommentThread> commentThread = commentThreadRepository.findById(id);
            if (!commentThread.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            CommentThread thread = commentThread.get();
            if (!thread.getComments().isEmpty()) {
                txDataControllerHelper.checkSubjectAccess(thread.getComments().iterator().next().getSubject());
            }
            CommentThread resolvedCommentThread = commentThreadService.resolveThread(thread);
            return ResponseEntity.ok(CommentThreadResponse.fromEntity(resolvedCommentThread));
        } catch (TxDataControllerHelper.TxDataPartitionAccessDeniedException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResponseEntity.status(403).build();
        }
    }

}
