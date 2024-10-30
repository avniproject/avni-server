package org.avni.server.web;

import org.avni.server.dao.NewsRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.News;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.NewsService;
import org.avni.server.service.S3Service;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.S;
import org.avni.server.web.request.NewsContract;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class NewsController extends AbstractController<News> implements RestControllerResourceProcessor<News> {

    private final NewsService newsService;
    private final NewsRepository newsRepository;
    private final S3Service s3Service;
    private final AccessControlService accessControlService;

    @Autowired
    public NewsController(NewsService newsService, NewsRepository newsRepository,
                          S3Service s3Service, AccessControlService accessControlService) {
        this.newsService = newsService;
        this.newsRepository = newsRepository;
        this.s3Service = s3Service;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/news")
    @ResponseBody
    @Transactional
    public List<NewsContract> getAll() {
        return newsRepository.findAllByIsVoidedFalse()
                .stream().map(NewsContract::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/publishedNews")
    @ResponseBody
    @Transactional
    public List<NewsContract> getAllPublishedNews() {
        return newsRepository.findByPublishedDateNotNullAndIsVoidedFalse()
                .stream().map(NewsContract::fromEntity)
                .peek(newsContract -> {
                    String signedURL = S.isEmpty(newsContract.getHeroImage()) ? null : s3Service.generateMediaDownloadUrl(newsContract.getHeroImage()).toString();
                    newsContract.setSignedHeroImage(signedURL);
                })
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/news/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<NewsContract> getById(@PathVariable Long id) {
        Optional<News> news = newsRepository.findById(id);
        return news.map(n -> ResponseEntity.ok(NewsContract.fromEntity(n)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/web/news")
    @ResponseBody
    @Transactional
    public ResponseEntity<NewsContract> newNews(@RequestBody NewsContract newsContract) {
        accessControlService.checkPrivilege(PrivilegeType.EditNews);
        News news = newsService.saveNews(newsContract);
        return ResponseEntity.ok(NewsContract.fromEntity(news));
    }

    @PutMapping(value = "/web/news/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<NewsContract> editNews(@PathVariable Long id, @RequestBody NewsContract newsContract) {
        accessControlService.checkPrivilege(PrivilegeType.EditNews);
        Optional<News> news = newsRepository.findById(id);
        if (!news.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        News newNews = newsService.editNews(newsContract, id);
        return ResponseEntity.ok(NewsContract.fromEntity(newNews));
    }

    @DeleteMapping(value = "/web/news/{id}")
    @ResponseBody
    @Transactional
    public void deleteNews(@PathVariable Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditNews);
        Optional<News> news = newsRepository.findById(id);
        news.ifPresent(newsService::deleteNews);
    }

    @RequestMapping(value = "/news/v2", method = RequestMethod.GET)
    @Transactional
    public SlicedResources<EntityModel<News>> getNewsAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        return wrap(newsRepository.findSliceByPublishedDateNotNullAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable));
    }

    @RequestMapping(value = "/news", method = RequestMethod.GET)
    @Transactional
    public CollectionModel<EntityModel<News>> getNews(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        return wrap(newsRepository.findByPublishedDateNotNullAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable));
    }

    @Override
    public EntityModel<News> process(EntityModel<News> resource) {
        News news = resource.getContent();
        if (!S.isEmpty(news.getHeroImage())) {
            resource.add(Link.of(s3Service.generateMediaDownloadUrl(news.getHeroImage()).toString(), "heroImageSignedURL"));
        }
        return resource;
    }
}
