package com.assesshub.service;

import com.assesshub.common.exception.ApiException;
import com.assesshub.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Stores uploads outside the web root under random names. Original names are only kept as
 * metadata; files are always served back as attachments (never rendered inline).
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final Pattern STORED_NAME = Pattern.compile("^[a-f0-9-]{36}\\.[a-z0-9]{1,10}$");
    private static final Pattern AREA = Pattern.compile("^[a-z-]{1,30}$");

    public record StoredFile(String storedName, String originalName, String contentType, long size) {
    }

    private final Path root;
    private final Set<String> globalAllowed;

    public FileStorageService(AppProperties props) {
        this.root = Paths.get(props.storage().uploadDir()).toAbsolutePath().normalize();
        this.globalAllowed = props.storage().allowedExtensions().stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            log.warn("Could not create upload directory {}: {}", root, e.getMessage());
        }
    }

    public Set<String> globalAllowedExtensions() {
        return globalAllowed;
    }

    /** Normalises a user supplied comma list ("PDF, .docx") to allowed lower-case extensions. */
    public String normalizeExtensions(String csv) {
        if (!StringUtils.hasText(csv)) {
            return null;
        }
        Set<String> exts = new LinkedHashSet<>();
        for (String raw : csv.split(",")) {
            String ext = raw.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
            if (ext.isEmpty()) {
                continue;
            }
            if (!globalAllowed.contains(ext)) {
                throw ApiException.badRequest("FILE_TYPE_NOT_ALLOWED", "File type not allowed: " + ext);
            }
            exts.add(ext);
        }
        return exts.isEmpty() ? null : String.join(",", exts);
    }

    public StoredFile store(MultipartFile file, String area, Collection<String> allowedExtensions, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Please choose a non-empty file");
        }
        if (file.getSize() > maxBytes) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "File exceeds the maximum size of " + (maxBytes / (1024 * 1024)) + " MB");
        }
        String original = sanitizeName(file.getOriginalFilename());
        String ext = StringUtils.getFilenameExtension(original);
        ext = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        Set<String> allowed = (allowedExtensions == null || allowedExtensions.isEmpty()) ? globalAllowed
                : allowedExtensions.stream().map(e -> e.toLowerCase(Locale.ROOT)).filter(globalAllowed::contains)
                .collect(Collectors.toSet());
        if (ext.isEmpty() || !allowed.contains(ext)) {
            throw ApiException.badRequest("FILE_TYPE_NOT_ALLOWED", "Allowed file types: " + String.join(", ", allowed));
        }
        String storedName = UUID.randomUUID() + "." + ext;
        Path target = resolve(area, storedName);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to store upload", e);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR",
                    "Could not store the file");
        }
        String contentType = file.getContentType() == null ? "application/octet-stream"
                : file.getContentType().substring(0, Math.min(100, file.getContentType().length()));
        return new StoredFile(storedName, original, contentType, file.getSize());
    }

    public Resource load(String area, String storedName) {
        try {
            Path path = resolve(area, storedName);
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw ApiException.notFound("File");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw ApiException.notFound("File");
        }
    }

    public void deleteQuietly(String area, String storedName) {
        if (storedName == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(area, storedName));
        } catch (IOException | ApiException e) {
            log.warn("Could not delete stored file {}/{}", area, storedName);
        }
    }

    private Path resolve(String area, String storedName) {
        if (!AREA.matcher(area).matches() || !STORED_NAME.matcher(storedName).matches()) {
            throw ApiException.notFound("File");
        }
        Path path = root.resolve(area).resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            throw ApiException.notFound("File");
        }
        return path;
    }

    static String sanitizeName(String name) {
        String cleaned = StringUtils.getFilename(StringUtils.cleanPath(name == null ? "file" : name));
        if (cleaned == null || cleaned.isBlank()) {
            cleaned = "file";
        }
        cleaned = cleaned.replaceAll("[\\r\\n\"\\\\/:*?<>|]", "_");
        return cleaned.length() > 200 ? cleaned.substring(cleaned.length() - 200) : cleaned;
    }
}
