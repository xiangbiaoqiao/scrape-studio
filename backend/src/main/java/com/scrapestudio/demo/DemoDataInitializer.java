package com.scrapestudio.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapestudio.project.Field;
import com.scrapestudio.project.FieldRepository;
import com.scrapestudio.project.Project;
import com.scrapestudio.project.ProjectRepository;
import com.scrapestudio.project.SelectorType;
import com.scrapestudio.run.Run;
import com.scrapestudio.run.RunRepository;
import com.scrapestudio.user.User;
import com.scrapestudio.user.UserRepository;
import com.scrapestudio.worker.dto.DiagnosisReport;
import com.scrapestudio.worker.dto.DiagnosisSignal;
import com.scrapestudio.worker.dto.TlsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds a ready-to-explore workspace so a fresh clone can show the full product
 * without requiring the rendering worker to be online.
 */
@Component
@ConditionalOnProperty(name = "scrapestudio.demo.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private static final String DEMO_EMAIL;
    private static final String DEMO_PASSWORD;

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final FieldRepository fieldRepository;
    private final RunRepository runRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public DemoDataInitializer(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            FieldRepository fieldRepository,
            RunRepository runRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.fieldRepository = fieldRepository;
        this.runRepository = runRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmailIgnoreCase(DEMO_EMAIL).isPresent()) {
            return;
        }

        User demo = new User();
        demo.setEmail(DEMO_EMAIL);
        demo.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        demo.setDisplayName("Demo Explorer");
        demo.setRole(User.Role.USER);
        demo = userRepository.save(demo);

        Project books = createProject(
                demo,
                "Books to Scrape",
                "https://books.toscrape.com/",
                "The canonical sandbox for learning web scraping. Selectors were captured with the visual picker."
        );
        books.setItemSelector(".product_pod");
        books.setItemSelectorType(SelectorType.CSS);
        books.setScheduleEnabled(true);
        books.setIntervalMinutes(360);
        books.setLastRunAt(Instant.now().minus(2, ChronoUnit.HOURS));
        books.setNextRunAt(Instant.now().plus(4, ChronoUnit.HOURS));
        books = projectRepository.save(books);

        addField(books, "title", "h3 a", SelectorType.CSS, "title", false);
        addField(books, "price", ".price_color", SelectorType.CSS, "text", false);
        addField(books, "rating", "p.star-rating", SelectorType.CSS, "class", false);
        addField(books, "availability", ".availability", SelectorType.CSS, "text", false);
        addField(books, "link", "h3 a", SelectorType.CSS, "href", false);

        Project quotes = createProject(
                demo,
                "Quotes to Scrape",
                "https://quotes.toscrape.com/",
                "A second demo workspace that shows tag extraction with a multi-value selector."
        );
        quotes.setItemSelector(".quote");
        quotes.setItemSelectorType(SelectorType.CSS);
        quotes = projectRepository.save(quotes);

        addField(quotes, "quote", ".text", SelectorType.CSS, "text", false);
        addField(quotes, "author", ".author", SelectorType.CSS, "text", false);
        addField(quotes, "tags", ".tags a.tag", SelectorType.CSS, "text", true);

        seedRuns(books, quotes);
        log.info("Demo workspace ready - login with {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
    }

    private Project createProject(User owner, String name, String url, String description) {
        Project project = new Project();
        project.setOwner(owner);
        project.setName(name);
        project.setUrl(url);
        project.setDescription(description);
        return projectRepository.save(project);
    }

    private void addField(
            Project project,
            String name,
            String selector,
            SelectorType type,
            String attribute,
            boolean multiple
    ) {
        Field field = new Field();
        field.setProject(project);
        field.setName(name);
        field.setSelector(selector);
        field.setSelectorType(type);
        field.setAttribute(attribute);
        field.setMultiple(multiple);
        field.setOrderIndex((int) fieldRepository.countByProjectId(project.getId()));
        fieldRepository.save(field);
    }

    private void seedRuns(Project books, Project quotes) {
        List<Map<String, String>> bookRowList = List.of(
                book("A Light in the Attic", "£51.77", "star-rating Three", "In stock (22 available)",
                        "catalogue/a-light-in-the-attic_1000/index.html"),
                book("Tipping the Velvet", "£53.74", "star-rating One", "In stock (20 available)",
                        "catalogue/tipping-the-velvet_999/index.html"),
                book("Soumission", "£50.10", "star-rating One", "In stock (20 available)",
                        "catalogue/soumission_998/index.html"),
                book("Sharp Objects", "£47.82", "star-rating Four", "In stock (20 available)",
                        "catalogue/sharp-objects_997/index.html"),
                book("Sapiens: A Brief History of Humankind", "£54.23", "star-rating Five",
                        "In stock (20 available)", "catalogue/sapiens-a-brief-history-of-humankind_996/index.html")
        );

        List<Map<String, String>> quoteRowList = List.of(
                Map.of(
                        "quote", "“The world as we have created it is a process of our thinking. It cannot be changed without changing our thinking.”",
                        "author", "Albert Einstein",
                        "tags", "change, deep-thoughts, thinking, world"
                ),
                Map.of(
                        "quote", "“It is our choices, Harry, that show what we truly are, far more than our abilities.”",
                        "author", "J.K. Rowling",
                        "tags", "abilities, choices"
                ),
                Map.of(
                        "quote", "“There are only two ways to live your life. One is as though nothing is a miracle. The other is as though everything is a miracle.”",
                        "author", "Albert Einstein",
                        "tags", "inspirational, life, live, miracle, miracles"
                )
        );

        seedRun(books, Run.RunStatus.SUCCESS, Instant.now().minus(2, ChronoUnit.HOURS),
                1840, bookRowList, null, null);
        seedRun(books, Run.RunStatus.BLOCKED, Instant.now().minus(2, ChronoUnit.DAYS),
                9120, List.of(), "Cloudflare challenge detected (HTTP 403 + cf-mitigated header)",
                diagnosis());
        seedRun(books, Run.RunStatus.SUCCESS, Instant.now().minus(4, ChronoUnit.DAYS),
                2130, bookRowList, null, null);
        seedRun(books, Run.RunStatus.SUCCESS, Instant.now().minus(9, ChronoUnit.DAYS),
                1970, bookRowList, null, null);

        seedRun(quotes, Run.RunStatus.SUCCESS, Instant.now().minus(6, ChronoUnit.HOURS),
                1210, quoteRowList, null, null);
        seedRun(quotes, Run.RunStatus.SUCCESS, Instant.now().minus(3, ChronoUnit.DAYS),
                1450, quoteRowList, null, null);
    }

    private void seedRun(
            Project project,
            Run.RunStatus status,
            Instant startedAt,
            long durationMs,
            List<Map<String, String>> rowList,
            String errorMessage,
            DiagnosisReport diagnosisReport
    ) {
        Run run = new Run();
        run.setProject(project);
        run.setStatus(status);
        run.setTriggerType(project.isScheduleEnabled() ? Run.RunTrigger.SCHEDULE : Run.RunTrigger.MANUAL);
        run.setStartedAt(startedAt);
        run.setFinishedAt(startedAt.plusMillis(durationMs));
        run.setDurationMs(durationMs);
        run.setItemCount(rowList.size());
        run.setErrorMessage(errorMessage);
        try {
            run.setResultJson(objectMapper.writeValueAsString(rowList));
            if (diagnosisReport != null) {
                run.setDiagnosisJson(objectMapper.writeValueAsString(diagnosisReport));
            }
        } catch (Exception exception) {
            log.warn("Could not seed run payload for project {}", project.getId(), exception);
            run.setResultJson("[]");
        }
        runRepository.save(run);
    }

    private Map<String, String> book(String title, String price, String rating, String availability, String link) {
        Map<String, String> rowMap = new LinkedHashMap<>();
        rowMap.put("title", title);
        rowMap.put("price", price);
        rowMap.put("rating", rating);
        rowMap.put("availability", availability);
        rowMap.put("link", link);
        return rowMap;
    }

    private DiagnosisReport diagnosis() {
        return new DiagnosisReport(
                "https://books.toscrape.com/",
                "https://books.toscrape.com/",
                Instant.now().minus(2, ChronoUnit.DAYS).toString(),
                1840L,
                403,
                "low-risk",
                34,
                "TLS and HTTP/2 fingerprints look like a normal browser, but the page was served from a CDN challenge during this run.",
                "cloudflare",
                new TlsInfo(
                        "TLSv1.3",
                        "TLS_AES_128_GCM_SHA256",
                        "h2",
                        true,
                        "Google Trust Services",
                        null,
                        null
                ),
                List.of(
                        new DiagnosisSignal(
                                "TLS fingerprint",
                                "pass",
                                "JA4 matches a current Chrome build."
                        ),
                        new DiagnosisSignal(
                                "HTTP/2 settings",
                                "pass",
                                "Frame settings are consistent with Chromium."
                        ),
                        new DiagnosisSignal(
                                "CDN challenge",
                                "warn",
                                "Response included cf-mitigated: challenge. Retry with a real browser context."
                        )
                ),
                List.of(
                        "Use the Playwright rendering worker instead of plain HTTP requests.",
                        "Keep a realistic browser profile and reuse a warm session.",
                        "Slow down bursts and respect the site's crawling policy."
                )
        );
    }

    static {
        DEMO_EMAIL = "demo@scrapestudio.dev";
        DEMO_PASSWORD = "demo1234";
    }
}
