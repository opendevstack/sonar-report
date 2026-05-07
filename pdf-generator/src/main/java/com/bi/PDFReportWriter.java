import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;




public class PDFReportWriter {
    private final PDDocument document;
    private PDPageContentStream contentStream;
    private PDFont font;
    private float fontSize;
    private float leading;
    private float margin = 50;
    private float yPosition;
    private PDFont bodyFont;
    private PDFont tittle1Font;
    private PDFont tittle2Font;
    private PDFont tittle3Font;

    private final float bodySize = 10;
    private final float tittle1Size = 20;
    private final float tittle2Size = 18;
    private final float tittle3Size = 14;
    private PDFont previousFont = tittle1Font;

    private PDPage currentPage;
    private float currentY;

    private PDFont currentFontType;

    private final List<PDPage> indexPages;

    private final List<Bookmark> bookmarks = new ArrayList<>();
    private static class Bookmark {
        String title;
        PDPage page;
        float yPosition;
        int level; // 1 = tittle1, 2 = tittle2, 3 = tittle3

        Bookmark(String title, PDPage page, float yPosition, int level) {
            this.title = title;
            this.page = page;
            this.yPosition = yPosition;
            this.level = level;
        }
    }

    public PDFReportWriter() throws IOException {
        document = new PDDocument();
        this.indexPages = new ArrayList<>();
        InputStream imageStream = getClass().getClassLoader().getResourceAsStream("fonts/NotoSerif-Regular.ttf");
        bodyFont = PDType0Font.load(document, imageStream);
        imageStream = getClass().getClassLoader().getResourceAsStream("fonts/NotoSerif-Bold.ttf");
        tittle1Font = PDType0Font.load(document, imageStream);
        tittle2Font = tittle1Font;
        tittle3Font = tittle1Font;
        font = bodyFont;
        fontSize = bodySize;
        leading = 1.5f * fontSize;
        currentFontType = bodyFont;
        addNewPage();
    }

    public void addLine(String text) throws IOException {
        if (contentStream == null || yPosition <= margin) {
            if (contentStream != null) {
                try { contentStream.endText(); } catch (IllegalStateException ignored) {}
                contentStream.close();
            }
            addNewPage();
        } else {
            try { contentStream.endText(); } catch (IllegalStateException ignored) {}
        }

        contentStream.beginText();
        contentStream.newLineAtOffset(margin, yPosition);

        float maxWidth = PDRectangle.A4.getWidth() - 2 * margin;
        List<String> lines = divideTextInLines(text, font, fontSize, maxWidth);

        for (String line : lines) {
            contentStream.showText(line);

            if ((currentFontType == tittle1Font || currentFontType == tittle2Font || currentFontType == tittle3Font)
                    && lines.indexOf(line) == 0) {
                int level = (currentFontType == tittle1Font) ? 1 :
                            (currentFontType == tittle2Font) ? 2 : 3;

                addBookmark(line, level);
            }

            if (currentFontType == tittle1Font && lines.indexOf(line) == 0) {
                contentStream.endText();
                float textWidth = font.getStringWidth(line) / 1000 * fontSize;
                float underlineY = yPosition - 2f;
                contentStream.setLineWidth(1f);
                contentStream.moveTo(margin, underlineY);
                contentStream.lineTo(margin + textWidth, underlineY);
                contentStream.stroke();
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
            }

            contentStream.newLine();
            yPosition -= leading;
            currentY = yPosition;

            if (yPosition <= margin) {
                contentStream.endText();
                contentStream.close();
                addNewPage();
                contentStream.beginText();
                contentStream.newLineAtOffset(margin, yPosition);
            }
        }

        contentStream.endText();
    }
    
    private List<String> divideTextInLines(String text, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {

            List<String> parts = splitBySlash(word);

            for (String part : parts) {
                String candidate = currentLine.length() == 0 ? part : currentLine + " " + part;
                float width = font.getStringWidth(candidate) / 1000 * size;

                if (width <= maxWidth) {
                    currentLine = new StringBuilder(candidate);
                } else {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder(part);
                    } else {
                        lines.add(part);
                        currentLine = new StringBuilder();
                    }
                }
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    private List<String> splitBySlash(String word) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (char c : word.toCharArray()) {
            current.append(c);
            if (c == '/') {
                result.add(current.toString());
                current = new StringBuilder();
            }
        }

        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }

    private void addNewPage() throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        yPosition = PDRectangle.A4.getHeight() - margin - 20;
        contentStream = new PDPageContentStream(document, page);
        contentStream.setFont(font, fontSize);
        contentStream.setLeading(leading);
        currentPage = page;
        currentY = yPosition;
        drawHeader(page);
    }

    public static String getCurrentGMTTimeFormatted() {
        LocalDateTime nowLocal = LocalDateTime.now();

        ZoneId gmtZone = ZoneId.of("GMT");
        ZonedDateTime nowGMT = nowLocal.atZone(ZoneId.systemDefault()).withZoneSameInstant(gmtZone);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d, yyyy, hh:mm a 'GMT'", Locale.ENGLISH);

        return nowGMT.format(formatter);
    }

    private void drawHeader(PDPage page) throws IOException {
        try (PDPageContentStream headerStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            float pageWidth = PDRectangle.A4.getWidth();
            float headerHeight = 40f;
            float yTop = PDRectangle.A4.getHeight();
            float margin = 20f;

            headerStream.setNonStrokingColor(0.15f, 0.35f, 0.75f);
            headerStream.addRect(0, yTop - headerHeight, pageWidth, headerHeight);
            headerStream.fill();

            headerStream.setFont(tittle3Font, 10);
            headerStream.setNonStrokingColor(1);

            String leftText = "SonarQube Report";
            headerStream.beginText();
            headerStream.newLineAtOffset(margin, yTop - 15);
            headerStream.showText(leftText);
            headerStream.endText();

            headerStream.setFont(bodyFont, 10);
            String currentDate = getCurrentGMTTimeFormatted();
            headerStream.beginText();
            headerStream.newLineAtOffset(margin, yTop - 31);
            headerStream.showText(currentDate);
            headerStream.endText();

            String centerText = "CONFIDENTIAL";
            float centerWidth = bodyFont.getStringWidth(centerText) / 1000 * 10;
            headerStream.beginText();
            headerStream.newLineAtOffset((pageWidth - centerWidth) / 2, yTop - 20);
            headerStream.showText(centerText);
            headerStream.endText();

            try (InputStream imageStream = getClass().getClassLoader().getResourceAsStream("sonarqube-pngrepo-com.png")) {
                if (imageStream != null) {
                    PDImageXObject logo = PDImageXObject.createFromByteArray(document, imageStream.readAllBytes(), "logo");
                    float imageWidth = 80;
                    float imageHeight = 90f;
                    headerStream.drawImage(logo, pageWidth - margin - imageWidth, yTop - 63, imageWidth, imageHeight);
                }
            }
        }
    }

    public void drawTable(float tableWidth, String[] headers, List<String[]> rows) throws IOException {
        if (contentStream != null) {
            try {
                contentStream.endText();
            } catch (IllegalStateException ignored) {}
        }

        setFont(bodyFont, bodySize);

        int cols = headers.length;
        float colWidth = tableWidth / cols;
        float y = yPosition;

        int maxHeaderLines = 1;
        for (String header : headers) {
            List<String> lines = wrapText(header, colWidth - 10);
            maxHeaderLines = Math.max(maxHeaderLines, lines.size());
        }
        float headerHeight = maxHeaderLines * leading;

        drawTableSection(headers, null, y, headerHeight, colWidth, tableWidth, true);
        y -= headerHeight;

        for (String[] row : rows) {
            int maxLines = 1;
            for (int i = 0; i < row.length; i++) {
                List<String> lines = wrapText(row[i], colWidth - 10);
                maxLines = Math.max(maxLines, lines.size());
            }

            float leading = 1.5f * fontSize;
            float rowHeight = maxLines * leading;

            if (y - rowHeight < margin) {
                contentStream.close();
                addNewPage();
                y = yPosition;

                drawTableSection(headers, null, y, 20, colWidth, tableWidth, true);
                y -= 20;
            }

            drawTableSection(null, row, y, rowHeight, colWidth, tableWidth, false);
            y -= rowHeight;
        }

        yPosition = y - 10;
        try {
            contentStream.beginText();
            contentStream.newLineAtOffset(margin, yPosition);
        } catch (IllegalStateException ignored) {}
    }

    private void drawTableSection(String[] header, String[] row, float y, float rowHeight, float colWidth, float tableWidth, boolean isHeader) throws IOException {
        int cols = header != null ? header.length : row.length;
        float x = margin;

        if (isHeader) {
            contentStream.setNonStrokingColor(0.6f, 0.8f, 0.95f);
            contentStream.addRect(x, y - rowHeight, tableWidth, rowHeight);
            contentStream.fill();
            contentStream.setNonStrokingColor(0);
        }

        for (int i = 0; i <= cols; i++) {
            float xLine = x + i * colWidth;
            contentStream.moveTo(xLine, y);
            contentStream.lineTo(xLine, y - rowHeight);
        }

        contentStream.moveTo(x, y);
        contentStream.lineTo(x + tableWidth, y);
        contentStream.moveTo(x, y - rowHeight);
        contentStream.lineTo(x + tableWidth, y - rowHeight);
        contentStream.stroke();

        float maxTextWidth = colWidth - 10;
        for (int i = 0; i < cols; i++) {
            String text = isHeader ? header[i] : (i < row.length ? row[i] : "");
            List<String> lines = wrapText(text, maxTextWidth);
            float totalTextHeight = lines.size() * leading;
            float startX = x + i * colWidth;
            float startY = y - ((rowHeight - totalTextHeight) / 2) - fontSize;

            for (int j = 0; j < lines.size(); j++) {
                String line = lines.get(j);
                float textWidth = font.getStringWidth(line) / 1000 * fontSize;
                float textX = startX + (colWidth - textWidth) / 2;
                float textY = startY - j * leading;

                contentStream.beginText();
                contentStream.setFont(isHeader ? tittle3Font : font, fontSize);
                contentStream.newLineAtOffset(textX, textY);
                contentStream.showText(line);
                contentStream.endText();
            }
        }
    }

    public void insertIndexAtBeginning() throws IOException {
        PDPage indexPage = new PDPage(PDRectangle.A4);

       
        List<PDPage> existingPages = new ArrayList<>();
        for (PDPage page : document.getPages()) {
            existingPages.add(page);
        }

        
        int total = document.getNumberOfPages();
        for (int i = total - 1; i >= 0; i--) {
            document.removePage(i);
        }

        
        document.addPage(indexPage);
        indexPages.add(indexPage);
        drawHeader(indexPage);

        PDPageContentStream indexStream = new PDPageContentStream(document, indexPage,
                PDPageContentStream.AppendMode.APPEND, true, true);

        float y = PDRectangle.A4.getHeight() - margin - 60;
        float lineHeight = 2.0f * bodySize;

        
        indexStream.setFont(tittle1Font, tittle1Size);
        indexStream.beginText();
        indexStream.newLineAtOffset(margin, y);
        indexStream.showText("Index");
        indexStream.endText();
        y -= lineHeight * 2;

        for (Bookmark bm : bookmarks) {
            String title = bm.title;

            float indent = margin + (bm.level - 1) * 20;

            PDFont thisFont;
            float thisFontSize;

            switch (bm.level) {
                case 1:
                    thisFont = tittle1Font;
                    thisFontSize = 12;
                    indexStream.setNonStrokingColor(0, 0, 0);
                    break;
                case 2:
                    thisFont = bodyFont;
                    thisFontSize = 11;
                    indexStream.setNonStrokingColor(0.3f);
                    break;
                case 3:
                    thisFont = bodyFont;
                    thisFontSize = 10;
                    indexStream.setNonStrokingColor(0.5f);
                    break;
                default:
                    thisFont = bodyFont;
                    thisFontSize = 10;
                    indexStream.setNonStrokingColor(0);
            }

            indexStream.setFont(thisFont, thisFontSize);

            int pageNum = existingPages.indexOf(bm.page) + 2; 
            String pageStr = String.valueOf(pageNum);

            float pageStrWidth = thisFont.getStringWidth(pageStr) / 1000 * thisFontSize;
            float titleWidth = thisFont.getStringWidth(title) / 1000 * thisFontSize;

            
            indexStream.beginText();
            indexStream.newLineAtOffset(indent, y);
            indexStream.showText(title);
            indexStream.endText();

            
            float pageNumX = PDRectangle.A4.getWidth() - margin - pageStrWidth;
            indexStream.beginText();
            indexStream.newLineAtOffset(pageNumX, y);
            indexStream.showText(pageStr);
            indexStream.endText();

            
            PDPageXYZDestination dest = new PDPageXYZDestination();
            dest.setPage(bm.page);
            dest.setTop((int) bm.yPosition);
            dest.setZoom(1);

            PDActionGoTo action = new PDActionGoTo();
            action.setDestination(dest);

            PDAnnotationLink link = new PDAnnotationLink();
            PDRectangle rect = new PDRectangle();
            rect.setLowerLeftX(indent);
            rect.setLowerLeftY(y - 2);
            rect.setUpperRightX(indent + titleWidth);
            rect.setUpperRightY(y + thisFontSize);
            link.setRectangle(rect);

            PDBorderStyleDictionary border = new PDBorderStyleDictionary();
            border.setWidth(0);
            link.setBorderStyle(border);
            link.setAction(action);

            indexPage.getAnnotations().add(link);

            y -= lineHeight;

            if (y <= margin + lineHeight) {
                indexStream.close();
                indexPage = new PDPage(PDRectangle.A4);
                document.addPage(indexPage);
                indexPages.add(indexPage);
                drawHeader(indexPage);
                indexStream = new PDPageContentStream(document, indexPage,
                        PDPageContentStream.AppendMode.APPEND, true, true);
                y = PDRectangle.A4.getHeight() - margin - 60;
            }
        }

        indexStream.close();

        
        for (PDPage page : existingPages) {
            document.addPage(page);
        }
    }

    private List<String> wrapText(String text, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            float width = font.getStringWidth(testLine) / 1000 * fontSize;

            if (width > maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }

                
                while (font.getStringWidth(word) / 1000 * fontSize > maxWidth) {
                    int cutIndex = 1;
                    while (cutIndex < word.length() &&
                            font.getStringWidth(word.substring(0, cutIndex)) / 1000 * fontSize <= maxWidth) {
                        cutIndex++;
                    }
                    cutIndex--;
                    lines.add(word.substring(0, cutIndex));
                    word = word.substring(cutIndex);
                }

                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    private void repositionIndexPages() {
        int insertAfter = 0; 

        for (int i = indexPages.size() - 1; i >= 0; i--) {
            PDPage page = indexPages.get(i);
            document.getPages().remove(page);
            document.getPages().insertAfter(page, document.getPage(insertAfter));
        }
    }

    public void save(String fileName) throws IOException {
        if (contentStream != null) {
            try {
                contentStream.endText();
            } catch (IllegalStateException ignored) {}
            contentStream.close();
            contentStream = null;
        }

        repositionIndexPages(); 

        int totalPages = document.getNumberOfPages();
        for (int i = 0; i < totalPages; i++) {
            drawFooter(document.getPage(i), i + 1, totalPages);
        }

        document.save(fileName);
        document.close();
    }

    public void bodyFont() throws IOException {
        if (contentStream != null) {
            try {
                contentStream.endText();
            } catch (IllegalStateException ignored) {}
        }
        contentStream.beginText();
        contentStream.newLineAtOffset(margin, yPosition);
        setFont(bodyFont, bodySize);
    }

    public void tittle1Font() throws IOException {
        if (contentStream != null) {
            try {
                contentStream.endText();
            } catch (IllegalStateException ignored) {}
        }
        if (currentFontType == bodyFont) {
            yPosition -= leading;
        }
        contentStream.beginText();
        contentStream.newLineAtOffset(margin, yPosition);
        setFont(tittle1Font, tittle1Size);
    }

    public void tittle2Font() throws IOException {
        if (contentStream != null) {
            try {
                contentStream.endText();
            } catch (IllegalStateException ignored) {}
        }
        if (currentFontType == bodyFont) {
            yPosition -= leading;
        }
        contentStream.beginText();
        contentStream.newLineAtOffset(margin, yPosition);
        setFont(tittle2Font, tittle2Size);
    }

    public void tittle3Font() throws IOException {
        if (contentStream != null) {
            try {
                contentStream.endText();
            } catch (IllegalStateException ignored) {}
        }
        if (currentFontType == bodyFont) {
            yPosition -= leading;
        }
        contentStream.beginText();
        contentStream.newLineAtOffset(margin, yPosition);
        setFont(tittle3Font, tittle3Size);
    }

    private void setFont(PDFont font, float size) throws IOException {
        if (previousFont == bodyFont && font != bodyFont) yPosition -=leading;
        previousFont = font;
        this.font = font;
        this.fontSize = size;
        this.leading = 1.5f * size;
        this.currentFontType = font;
        contentStream.setFont(font, fontSize);
        contentStream.setLeading(leading);
    }

    public void startBulletEntry(String titulo) throws IOException {
        if (yPosition <= margin + 50) {
            if (contentStream != null) {
                try {
                    contentStream.endText();
                } catch (IllegalStateException ignored) {}
                contentStream.close();
            }
            addNewPage();
        }

        if (contentStream != null) {
            try {
                contentStream.endText();
            } catch (IllegalStateException ignored) {}
        }

        float lineY = yPosition - 10;
        float startX = margin;
        float endX = PDRectangle.A4.getWidth() - margin;

        contentStream.setStrokingColor(0);
        contentStream.setLineWidth(1);
        contentStream.moveTo(startX, lineY);
        contentStream.lineTo(endX, lineY);
        contentStream.stroke();

        yPosition = lineY - 20;

        contentStream.beginText();
        contentStream.setFont(tittle3Font, tittle3Size);
        contentStream.newLineAtOffset(margin, yPosition);

        float maxWidth = PDRectangle.A4.getWidth() - 2 * margin;
        List<String> lines = divideTextInLines("- " + titulo, tittle3Font, tittle3Size, maxWidth);

        for (String line : lines) {
            if (yPosition <= margin + leading) {
                contentStream.endText();
                contentStream.close();
                addNewPage();
                contentStream.beginText();
                contentStream.setFont(tittle3Font, tittle3Size);
                contentStream.newLineAtOffset(margin, yPosition);
            }

            contentStream.showText(line);
            contentStream.newLine();
            yPosition -= leading;
        }

        contentStream.endText();

        contentStream.setFont(bodyFont, bodySize);
        font = bodyFont;
        fontSize = bodySize;
        leading = 1.5f * bodySize;
        yPosition -= leading;
    }

    private void drawFooter(PDPage page, int pageNumber, int totalPages) throws IOException {
        try (PDPageContentStream footerStream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            float y = 20f; 
            float pageWidth = PDRectangle.A4.getWidth();

            String text = "Page " + pageNumber + " of " + totalPages;
            footerStream.setFont(bodyFont, 10);
            footerStream.setNonStrokingColor(0.2f);

            float textWidth = bodyFont.getStringWidth(text) / 1000 * 10;
            float x = (pageWidth - textWidth) / 2;

            footerStream.beginText();
            footerStream.newLineAtOffset(x, y);
            footerStream.showText(text);
            footerStream.endText();
        }
    }

    public void addIndentedLine(String label, String content) throws IOException {
        float originalMargin = margin;
        margin += 20;

        if (contentStream != null) {
            try { contentStream.endText(); } catch (IllegalStateException ignored) {}
        }

        float pageWidth = PDRectangle.A4.getWidth();
        String formattedLabel = "• " + label + ": ";
        float labelWidth = tittle3Font.getStringWidth(formattedLabel) / 1000 * bodySize;

        float maxFirstLineWidth = pageWidth - margin * 2 - labelWidth - 5;
        float indentX = margin + labelWidth + 5;
        float maxIndentWidth = pageWidth - indentX - margin;

        if (yPosition <= margin + leading) {
            if (contentStream != null) {
                try { contentStream.endText(); } catch (IllegalStateException ignored) {}
                contentStream.close();
            }
            addNewPage();
        }

        String[] contentLines = content.split("\n");

        List<String> firstWrapped = divideTextInLines(contentLines[0], bodyFont, bodySize, maxFirstLineWidth);
        for (int i = 0; i < firstWrapped.size(); i++) {
            if (yPosition <= margin + leading) {
                contentStream.close();
                addNewPage();
            }

            contentStream.beginText();
            if (i == 0) {
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.setFont(tittle3Font, bodySize);
                contentStream.showText(formattedLabel);
                contentStream.setFont(bodyFont, bodySize);
                contentStream.newLineAtOffset(labelWidth + 5, 0);
            } else {
                contentStream.newLineAtOffset(indentX, yPosition);
                contentStream.setFont(bodyFont, bodySize);
            }

            contentStream.showText(firstWrapped.get(i));
            contentStream.endText();
            yPosition -= leading;
        }

        for (int i = 1; i < contentLines.length; i++) {
            List<String> wrapped = divideTextInLines(contentLines[i], bodyFont, bodySize, maxIndentWidth);

            for (String line : wrapped) {
                if (yPosition <= margin + leading) {
                    contentStream.close();
                    addNewPage();
                }
                contentStream.beginText();
                contentStream.newLineAtOffset(indentX, yPosition);
                contentStream.setFont(bodyFont, bodySize);
                contentStream.showText(line);
                contentStream.endText();
                yPosition -= leading;
            }
        }

        yPosition -= leading;
        margin = originalMargin;
    }
    
    public void addIndentedHyperlink(String label, String url, String visibleText) throws IOException {
        float originalMargin = margin;
        margin += 20;

        if (contentStream != null) {
            try { contentStream.endText(); } catch (IllegalStateException ignored) {}
        }

        if (yPosition <= margin + leading) {
            contentStream.close();
            addNewPage();
        }

        float pageWidth = PDRectangle.A4.getWidth();
        float labelWidth = tittle3Font.getStringWidth("• " + label + ": ") / 1000 * bodySize;
        float linkTextWidth = bodyFont.getStringWidth(visibleText) / 1000 * bodySize;

        contentStream.beginText();
        contentStream.setFont(tittle3Font, bodySize);
        contentStream.newLineAtOffset(margin, yPosition);
        contentStream.showText("• " + label + ": ");
        contentStream.setFont(bodyFont, bodySize);
        contentStream.setNonStrokingColor(0, 0, 1); 
        contentStream.newLineAtOffset(labelWidth, 0);
        contentStream.showText(visibleText);
        contentStream.endText();
        contentStream.setNonStrokingColor(0);

        PDAnnotationLink link = new PDAnnotationLink();
        PDRectangle position = new PDRectangle();
        position.setLowerLeftX(margin + labelWidth);
        position.setLowerLeftY(yPosition - 2);
        position.setUpperRightX(margin + labelWidth + linkTextWidth);
        position.setUpperRightY(yPosition + bodySize);
        link.setRectangle(position);

        PDBorderStyleDictionary border = new PDBorderStyleDictionary();
        border.setWidth(0);
        link.setBorderStyle(border);

        PDActionURI action = new PDActionURI();
        action.setURI(url);
        link.setAction(action);

        document.getPage(document.getNumberOfPages() - 1).getAnnotations().add(link);

        yPosition -= leading;
        margin = originalMargin;
    }

    private String expandTabs(String input, int tabSize) {
        StringBuilder result = new StringBuilder();
        int position = 0;
        for (char c : input.toCharArray()) {
            if (c == '\t') {
                int spaces = tabSize - (position % tabSize);
                result.append(" ".repeat(spaces));
                position += spaces;
            } else {
                result.append(c);
                position += (c == '\n') ? 0 : 1;
            }
        }
        return result.toString();
    }
    
    public void addInlineFormattedBlock(String label, String content) throws IOException {
        float originalMargin = margin;
        margin += 20;

        if (contentStream != null) {
            try {
                contentStream.endText();
            } catch (IllegalStateException ignored) {}
        }

        PDFont codeFont = bodyFont;
        float fontSize = bodySize;
        float maxWidth = PDRectangle.A4.getWidth() - 2 * margin;

        String[] lines = content.split("\n");
        if (lines.length == 0) return;

        String labelText = "• " + label + ": ";
        float labelWidth = tittle3Font.getStringWidth(labelText) / 1000 * fontSize;

        if (yPosition <= margin + leading) {
            contentStream.close();
            addNewPage();
        }

        contentStream.beginText();
        contentStream.setFont(tittle3Font, fontSize);
        contentStream.newLineAtOffset(margin, yPosition);
        contentStream.showText(labelText);
        contentStream.setFont(codeFont, fontSize);

        String firstLine = lines[0];
        List<String> wrappedFirst = divideTextInLines(firstLine, codeFont, fontSize, maxWidth - labelWidth);

        if (!wrappedFirst.isEmpty()) {
            contentStream.newLineAtOffset(labelWidth, 0);
            contentStream.showText(wrappedFirst.get(0));
            contentStream.endText();
            yPosition -= leading;

            for (int i = 1; i < wrappedFirst.size(); i++) {
                if (yPosition <= margin + leading) {
                    contentStream.close();
                    addNewPage();
                }
                contentStream.beginText();
                contentStream.setFont(codeFont, fontSize);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(wrappedFirst.get(i));
                contentStream.endText();
                yPosition -= leading;
            }
        } else {
            contentStream.endText();
            yPosition -= leading;
        }

        for (int i = 1; i < lines.length; i++) {
            String expanded = expandTabs(lines[i], 4);
            List<String> wrapped = divideTextInLines(expanded, codeFont, fontSize, maxWidth);

            for (String w : wrapped) {
                if (yPosition <= margin + leading) {
                    contentStream.close();
                    addNewPage();
                }
                contentStream.beginText();
                contentStream.setFont(codeFont, fontSize);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(w);
                contentStream.endText();
                yPosition -= leading;
            }
        }

        yPosition -= leading;
        margin = originalMargin;
    }

    public void addCoverPage(String title, String subtitle) throws IOException {
        PDPage cover = new PDPage(PDRectangle.A4);

        try (PDPageContentStream background = new PDPageContentStream(document, cover, PDPageContentStream.AppendMode.OVERWRITE, true, true)) {
            background.setNonStrokingColor(0.9f, 0.95f, 1f);
            background.addRect(0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            background.fill();
        }

        try (PDPageContentStream stream = new PDPageContentStream(document, cover, PDPageContentStream.AppendMode.APPEND, true, true)) {
            float width = PDRectangle.A4.getWidth();
            float height = PDRectangle.A4.getHeight();

            stream.setFont(tittle1Font, 28);
            stream.beginText();
            stream.newLineAtOffset((width - tittle1Font.getStringWidth(title) / 1000 * 28) / 2, height - 200);
            stream.showText(title);
            stream.endText();

            stream.setFont(bodyFont, 16);
            stream.beginText();
            stream.newLineAtOffset((width - bodyFont.getStringWidth(subtitle) / 1000 * 16) / 2, height - 240);
            stream.showText(subtitle);
            stream.endText();
        }

        document.getPages().insertBefore(cover, document.getPage(0));
    }

    private List<NumberedBookmark> buildHierarchicalBookmarks() {
        List<NumberedBookmark> result = new ArrayList<>();
        int[] levels = new int[10]; 
        boolean hasLevel1 = bookmarks.stream().anyMatch(b -> b.level == 1);

        for (Bookmark bm : bookmarks) {
            int realLevel = bm.level;

            if (!hasLevel1 && bm.level > 1) {
                realLevel = bm.level - 1;
            }

            levels[realLevel - 1]++;

            for (int i = realLevel; i < levels.length; i++) {
                levels[i] = 0;
            }

            StringBuilder num = new StringBuilder();
            for (int i = 0; i < realLevel; i++) {
                if (levels[i] > 0) {
                    if (num.length() > 0) num.append(".");
                    num.append(levels[i]);
                }
            }

            result.add(new NumberedBookmark(num.toString(), bm));
        }

        return result;
    }

    private static class NumberedBookmark {
        String number;
        Bookmark bookmark;

        NumberedBookmark(String number, Bookmark bookmark) {
            this.number = number;
            this.bookmark = bookmark;
        }
    }

    public void addBookmark(String title, int level) {
        bookmarks.add(new Bookmark(title, currentPage, currentY, level));
    }
}