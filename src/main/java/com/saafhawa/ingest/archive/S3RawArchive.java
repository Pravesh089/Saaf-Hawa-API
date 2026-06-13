package com.saafhawa.ingest.archive;

import com.saafhawa.common.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.time.Instant;

/** S3 / MinIO archive (production + docker compose). Path-style addressing for MinIO. */
@Service
@ConditionalOnProperty(name = "saafhawa.archive.type", havingValue = "s3")
public class S3RawArchive implements RawArchiveService {

    private static final Logger log = LoggerFactory.getLogger(S3RawArchive.class);

    private final AppProperties.Archive cfg;
    private final S3Client s3;

    public S3RawArchive(AppProperties props) {
        this.cfg = props.archive();
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(cfg.endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cfg.accessKey(), cfg.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @PostConstruct
    void ensureBucket() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(cfg.bucket()).build());
            log.info("Created archive bucket {}", cfg.bucket());
        } catch (S3Exception e) {
            // Already exists / owned — fine.
            log.debug("Archive bucket {} already present", cfg.bucket());
        }
    }

    @Override
    public String archive(String source, Instant when, String suffix, byte[] data) {
        String key = ArchivePaths.key(source, when, suffix);
        s3.putObject(PutObjectRequest.builder().bucket(cfg.bucket()).key(key).build(),
                RequestBody.fromBytes(GzipUtil.gzip(data)));
        return key;
    }

    @Override
    public byte[] retrieve(String ref) {
        ResponseBytes<?> bytes = s3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(cfg.bucket()).key(ref).build());
        return GzipUtil.gunzip(bytes.asByteArray());
    }
}
