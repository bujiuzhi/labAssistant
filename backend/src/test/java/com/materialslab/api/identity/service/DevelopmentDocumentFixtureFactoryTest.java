package com.materialslab.api.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

/** 验证开发测试文档是可被对应格式库读取的真实二进制文件。 */
class DevelopmentDocumentFixtureFactoryTest {
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(2026, 8, 5, 9, 30, 0, 0, ZoneOffset.ofHours(8));

    @Test
    void shouldCreateReadableCommonDocumentFormats() throws Exception {
        var docx = fixture("project_plan");
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx.content()))) {
            assertFalse(document.getParagraphs().isEmpty());
        }

        var pdf = fixture("literature");
        try (var document = Loader.loadPDF(pdf.content())) {
            assertEquals(1, document.getNumberOfPages());
        }

        var xlsx = fixture("experiment_plan");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx.content()))) {
            assertEquals("实验配方", workbook.getSheetAt(0).getSheetName());
        }

        var pptx = fixture("stage_report");
        try (XMLSlideShow slideshow = new XMLSlideShow(new ByteArrayInputStream(pptx.content()))) {
            assertEquals(1, slideshow.getSlides().size());
        }

        var csv = fixture("meeting_minutes");
        assertTrue(new String(csv.content(), java.nio.charset.StandardCharsets.UTF_8).contains("会议日期"));

        var png = fixture("other");
        assertEquals(800, ImageIO.read(new ByteArrayInputStream(png.content())).getWidth());
    }

    private DevelopmentDocumentFixtureFactory.Fixture fixture(String category) {
        return DevelopmentDocumentFixtureFactory.create(category, "PRJ-TEST-001", "高耐热聚酰亚胺薄膜配方验证", 1, CREATED_AT);
    }
}
