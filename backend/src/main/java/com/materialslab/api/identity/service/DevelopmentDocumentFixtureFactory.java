package com.materialslab.api.identity.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

/** 生成可被常用办公软件实际打开的开发测试文档。 */
final class DevelopmentDocumentFixtureFactory {
    private DevelopmentDocumentFixtureFactory() { }

    /** 根据固定分类创建对应格式的真实测试文件。 */
    static Fixture create(String category, String projectNo, String projectName, int index, OffsetDateTime createdAt) {
        try {
            return switch (category) {
                case "project_plan" -> new Fixture("项目实施方案-" + serial(index) + ".docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        docx(projectNo, projectName, index));
                case "literature" -> new Fixture("文献调研摘要-" + serial(index) + ".pdf", "application/pdf",
                        pdf(projectNo, projectName, index));
                case "experiment_plan" -> new Fixture("实验配方记录-" + serial(index) + ".xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        xlsx(projectNo, projectName, index));
                case "stage_report" -> new Fixture("项目阶段汇报-" + serial(index) + ".pptx",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        pptx(projectNo, projectName, index));
                case "meeting_minutes" -> new Fixture("项目例会纪要-" + serial(index) + ".csv", "text/csv; charset=utf-8",
                        csv(projectNo, projectName, index, createdAt));
                default -> new Fixture("实验样品示意图-" + serial(index) + ".png", "image/png",
                        png(projectNo, index));
            };
        } catch (IOException error) {
            throw new IllegalStateException("生成开发测试文档失败：" + category, error);
        }
    }

    private static byte[] docx(String projectNo, String projectName, int index) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            paragraph(document, "材料实验助手 · 项目实施方案");
            paragraph(document, "项目编号：" + projectNo);
            paragraph(document, "项目名称：" + projectName);
            paragraph(document, "版本：V" + ((index - 1) / 3 + 1) + ".0");
            paragraph(document, "本文件为开发测试数据库中的真实 DOCX 文档，可通过接口下载和在线预览。");
            document.write(output);
            return output.toByteArray();
        }
    }

    private static void paragraph(XWPFDocument document, String value) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText(value);
    }

    private static byte[] xlsx(String projectNo, String projectName, int index) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("实验配方");
            String[][] values = {
                    {"项目编号", projectNo},
                    {"项目名称", projectName},
                    {"记录序号", serial(index)},
                    {"材料", "测试原料 A"},
                    {"用量", "10 g"},
                    {"状态", "开发测试数据"}
            };
            for (int row = 0; row < values.length; row++) {
                sheet.createRow(row).createCell(0).setCellValue(values[row][0]);
                sheet.getRow(row).createCell(1).setCellValue(values[row][1]);
            }
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] pptx(String projectNo, String projectName, int index) throws IOException {
        try (XMLSlideShow slideshow = new XMLSlideShow(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSLFSlide slide = slideshow.createSlide();
            XSLFTextBox title = slide.createTextBox();
            title.setText("材料实验助手\n项目阶段汇报");
            title.setAnchor(new java.awt.Rectangle(50, 50, 620, 110));
            XSLFTextBox body = slide.createTextBox();
            body.setText("项目编号：" + projectNo + "\n项目名称：" + projectName
                    + "\n汇报序号：" + serial(index) + "\n数据来源：开发测试数据库");
            body.setAnchor(new java.awt.Rectangle(70, 210, 580, 180));
            slideshow.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdf(String projectNo, String projectName, int index) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                stream.newLineAtOffset(72, 760);
                stream.showText("Materials Lab Assistant - Literature Review");
                stream.newLineAtOffset(0, -28);
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                stream.showText("Project: " + projectNo + "  Document: " + serial(index));
                stream.newLineAtOffset(0, -22);
                stream.showText("This is a real PDF test document generated for API preview verification.");
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] csv(String projectNo, String projectName, int index, OffsetDateTime createdAt) {
        return ("项目编号,项目名称,会议序号,会议日期,结论\n"
                + csvCell(projectNo) + "," + csvCell(projectName) + "," + serial(index) + ","
                + createdAt.toLocalDate() + ",确认下一轮实验计划\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String csvCell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static byte[] png(String projectNo, int index) throws IOException {
        BufferedImage image = new BufferedImage(800, 450, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(240, 246, 255));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(37, 99, 235));
            graphics.fillRoundRect(50, 54, 700, 342, 20, 20);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            graphics.drawString("Materials Lab Test Sample", 115, 190);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
            graphics.drawString(projectNo + " / Sample " + serial(index), 170, 245);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static String serial(int index) { return String.format("%02d", index); }

    /** 文档文件名、MIME 和正文。 */
    record Fixture(String name, String mimeType, byte[] content) { }
}
