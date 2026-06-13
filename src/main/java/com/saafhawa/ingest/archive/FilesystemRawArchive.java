package com.saafhawa.ingest.archive;

import com.saafhawa.common.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Filesystem archive (default; used in tests/CI without an object store). */
@Service
@ConditionalOnProperty(name = "saafhawa.archive.type", havingValue = "filesystem", matchIfMissing = true)
public class FilesystemRawArchive implements RawArchiveService {

    private final Path base;

    public FilesystemRawArchive(AppProperties props) {
        this.base = Path.of(props.archive().basePath());
    }

    @Override
    public String archive(String source, Instant when, String suffix, byte[] data) {
        String key = ArchivePaths.key(source, when, suffix);
        Path target = base.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, GzipUtil.gzip(data));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to archive " + key, e);
        }
        return key;
    }

    @Override
    public byte[] retrieve(String ref) {
        try {
            return GzipUtil.gunzip(Files.readAllBytes(base.resolve(ref)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read archive " + ref, e);
        }
    }
}
