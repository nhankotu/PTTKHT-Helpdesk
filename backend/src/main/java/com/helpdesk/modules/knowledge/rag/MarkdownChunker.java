package com.helpdesk.modules.knowledge.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bộ tách đoạn thông minh cho Knowledge Base (Markdown-aware + Sliding-Window Overlap):
 * - Giữ nguyên ranh giới Heading (#, ##, ###)
 * - Chia đoạn dài vượt quá MAX_WORDS_PER_CHUNK (250 từ) thành nhiều chunk có gối đầu OVERLAP_WORDS (50 từ)
 *   để không bị đứt ngữ cảnh giữa 2 chunk liền kề.
 */
@Component
public class MarkdownChunker {

    private static final int MAX_WORDS_PER_CHUNK = 250;
    private static final int OVERLAP_WORDS = 50;

    public record ChunkPiece(String heading, String content, int tokenEstimate) {
    }

    public List<ChunkPiece> chunk(String articleTitle, String markdownContent) {
        String safeTitle = (articleTitle != null && !articleTitle.isBlank()) ? articleTitle.trim() : "Knowledge Base";
        if (markdownContent == null || markdownContent.isBlank()) {
            return List.of(new ChunkPiece(safeTitle, safeTitle, 1));
        }

        List<Section> sections = splitByHeadings(safeTitle, markdownContent);
        List<ChunkPiece> results = new ArrayList<>();

        for (Section section : sections) {
            String text = section.body().trim();
            if (text.isEmpty()) {
                continue;
            }
            String[] words = text.split("\\s+");
            if (words.length <= MAX_WORDS_PER_CHUNK) {
                results.add(new ChunkPiece(section.heading(), text, Math.max(1, words.length)));
            } else {
                int step = Math.max(1, MAX_WORDS_PER_CHUNK - OVERLAP_WORDS);
                for (int start = 0; start < words.length; start += step) {
                    int end = Math.min(words.length, start + MAX_WORDS_PER_CHUNK);
                    String slice = String.join(" ", Arrays.copyOfRange(words, start, end));
                    results.add(new ChunkPiece(section.heading(), slice, end - start));
                    if (end >= words.length) {
                        break;
                    }
                }
            }
        }

        if (results.isEmpty()) {
            String trimmed = markdownContent.trim();
            int count = Math.max(1, trimmed.split("\\s+").length);
            results.add(new ChunkPiece(safeTitle, trimmed, count));
        }
        return results;
    }

    public String buildEmbeddingText(String articleTitle, String heading, String content) {
        StringBuilder sb = new StringBuilder();
        if (articleTitle != null && !articleTitle.isBlank()) {
            sb.append("[Bài viết: ").append(articleTitle.trim());
            if (heading != null && !heading.isBlank() && !heading.equalsIgnoreCase(articleTitle.trim())) {
                sb.append(" | Mục: ").append(heading.trim());
            }
            sb.append("]\n");
        }
        sb.append(content != null ? content.trim() : "");
        return sb.toString();
    }

    private List<Section> splitByHeadings(String defaultHeading, String markdown) {
        String[] lines = markdown.split("\\r?\\n");
        List<Section> sections = new ArrayList<>();
        String currentHeading = defaultHeading;
        StringBuilder buffer = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                if (!buffer.isEmpty()) {
                    sections.add(new Section(currentHeading, buffer.toString()));
                    buffer.setLength(0);
                }
                currentHeading = trimmed.replaceAll("^#+\\s*", "").trim();
                if (currentHeading.isEmpty()) {
                    currentHeading = defaultHeading;
                }
                buffer.append(trimmed).append("\n");
            } else {
                buffer.append(line).append("\n");
            }
        }
        if (!buffer.isEmpty()) {
            sections.add(new Section(currentHeading, buffer.toString()));
        }
        return sections;
    }

    private record Section(String heading, String body) {
    }
}
