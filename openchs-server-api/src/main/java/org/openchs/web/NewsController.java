package org.openchs.web;

import org.joda.time.DateTime;
import org.openchs.dao.NewsRepository;
import org.openchs.domain.News;
import org.openchs.service.NewsService;
import org.openchs.service.S3Service;
import org.openchs.util.S;
import org.openchs.web.request.NewsContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @Autowired
    public NewsController(NewsService newsService, NewsRepository newsRepository, S3Service s3Service) {
        this.newsService = newsService;
        this.newsRepository = newsRepository;
        this.s3Service = s3Service;
    }

    @GetMapping(value = "/web/news")
    @PreAuthorize(value = "hasAnyAuthority('admin','organisation_admin')")
    @ResponseBody
    @Transactional
    public List<NewsContract> getAll() {
        return newsRepository.findAllByIsVoidedFalse()
                .stream().map(NewsContract::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/news/{id}")
    @PreAuthorize(value = "hasAnyAuthority('admin','organisation_admin')")
    @ResponseBody
    @Transactional
    public ResponseEntity<NewsContract> getById(@PathVariable Long id) {
        Optional<News> news = newsRepository.findById(id);
        return news.map(n -> ResponseEntity.ok(NewsContract.fromEntity(n)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/web/news")
    @PreAuthorize(value = "hasAnyAuthority('admin', 'organisation_admin')")
    @ResponseBody
    @Transactional
    public ResponseEntity<NewsContract> newNews(@RequestBody NewsContract newsContract) {
        News news = newsService.saveNews(newsContract);
        return ResponseEntity.ok(NewsContract.fromEntity(news));
    }

    @PutMapping(value = "/web/news/{id}")
    @PreAuthorize(value = "hasAnyAuthority('admin', 'organisation_admin')")
    @ResponseBody
    @Transactional
    public ResponseEntity<NewsContract> editNews(@PathVariable Long id, @RequestBody NewsContract newsContract) {
        Optional<News> news = newsRepository.findById(id);
        if (!news.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        News newNews = newsService.editNews(newsContract, id);
        return ResponseEntity.ok(NewsContract.fromEntity(newNews));
    }

    @DeleteMapping(value = "/web/news/{id}")
    @PreAuthorize(value = "hasAnyAuthority('admin', 'organisation_admin')")
    @ResponseBody
    @Transactional
    public void deleteNews(@PathVariable Long id) {
        Optional<News> news = newsRepository.findById(id);
        news.ifPresent(newsService::deleteNews);
    }

    @RequestMapping(value = "/news", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user', 'organisation_admin')")
    @Transactional
    public PagedResources<Resource<News>> getNews(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        return wrap(newsRepository.findByPublishedDateNotNullAndAuditLastModifiedDateTimeIsBetweenOrderByAuditLastModifiedDateTimeAscIdAsc(lastModifiedDateTime, now, pageable));
    }

    @Override
    public Resource<News> process(Resource<News> resource) {
        News news = resource.getContent();
        if (!S.isEmpty(news.getHeroImage())) {
            resource.add(new Link(s3Service.generateMediaDownloadUrl(news.getHeroImage()).toString(), "heroImageSignedURL"));
        }
        return resource;
    }
}
