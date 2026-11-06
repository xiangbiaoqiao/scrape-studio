package com.scrapestudio.project;

import com.scrapestudio.project.dto.FieldDto;
import com.scrapestudio.project.dto.ProjectDetail;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGeneratorTest {

    private final CodeGenerator generator = new CodeGenerator();

    @Test
    void generatesCssPythonWithRequestsAndBeautifulSoup() {
        String code = generator.generate(project("CSS"), "python");

        assertThat(code)
                .contains("import requests")
                .contains("from bs4 import BeautifulSoup")
                .contains("ITEM_SELECTOR = \".product_pod\"")
                .contains("for item in soup.select(ITEM_SELECTOR)")
                .contains("item.select_one(\"h3 a\").get(\"title\")");
    }

    @Test
    void switchesToPlaywrightWhenXpathIsUsed() {
        String code = generator.generate(project("XPATH"), "python");

        assertThat(code)
                .contains("from playwright.sync_api import sync_playwright")
                .contains("xpath=//h3/a")
                .contains("page.goto(URL");
    }

    @Test
    void generatesJavaWithJsoup() {
        String code = generator.generate(project("CSS"), "java");

        assertThat(code)
                .contains("import org.jsoup.Jsoup;")
                .contains("Jsoup.connect(url)")
                .contains("doc.select(\".product_pod\")")
                .contains("rows.stream().limit(5).forEach(System.out::println)");
    }

    @Test
    void generatesNodeWithPlaywright() {
        String code = generator.generate(project("CSS"), "node");

        assertThat(code)
                .contains("import { chromium } from 'playwright';")
                .contains("const ITEM_SELECTOR = \".product_pod\";")
                .contains("await browser.close();");
    }

    @Test
    void returnsGuidanceWhenThereAreNoFields() {
        ProjectDetail empty = new ProjectDetail(
                1L, "Empty", "https://example.com", null, null, "CSS",
                false, 60, null, null, List.of(), Instant.now(), Instant.now()
        );

        assertThat(generator.generate(empty, "python")).contains("Add at least one field");
    }

    private ProjectDetail project(String selectorType) {
        String titleSelector = "XPATH".equals(selectorType) ? "//h3/a" : "h3 a";
        String priceSelector = "XPATH".equals(selectorType) ? "//p[@class='price_color']" : ".price_color";
        List<FieldDto> fields = List.of(
                new FieldDto(1L, "title", titleSelector, selectorType, "title", false, 0),
                new FieldDto(2L, "price", priceSelector, selectorType, "text", false, 1)
        );

        return new ProjectDetail(
                1L,
                "Demo",
                "https://books.toscrape.com/",
                null,
                ".product_pod",
                "CSS",
                false,
                60,
                null,
                null,
                fields,
                Instant.now(),
                Instant.now()
        );
    }
}
