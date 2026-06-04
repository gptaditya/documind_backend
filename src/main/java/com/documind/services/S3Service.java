package com.documind.services;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final String bucket;

    public S3Service(
            @Value("${aws.access-key}") String accessKey,
            @Value("${aws.secret-key}") String secretKey,
            @Value("${aws.region}") String region,
            @Value("${aws.s3.bucket}") String bucket) {

        this.bucket = bucket;
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    // Uploads file to S3, returns the unique S3 key
    public String upload(MultipartFile file) throws IOException {
        String key = UUID.randomUUID() + "-" + file.getOriginalFilename();

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromBytes(file.getBytes())
        );

        return key;
    }

    // Downloads file from S3 and extracts plain text (PDF or plain text)
    public String downloadAsText(String s3Key) throws IOException {
        InputStream stream = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .build()
        );

        byte[] bytes = stream.readAllBytes();

        if (s3Key.toLowerCase().endsWith(".pdf")) {
            // PDFBox 3.x: use Loader.loadPDF() — PDDocument.load() was removed in v3
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                return new PDFTextStripper().getText(doc);
            } catch (IOException e) {
                throw new IOException("Failed to extract text from PDF", e);
            }
        }

        return new String(bytes);
    }

    // Lists all files stored in the S3 bucket — returns key + fileName for each
    public List<java.util.Map<String, String>> listDocuments() {
        return s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .build()
        )
        .contents()
        .stream()
        .map((S3Object obj) -> java.util.Map.of(
            "s3Key", obj.key(),
            "fileName", obj.key().contains("-") ? obj.key().substring(obj.key().indexOf("-") + 1) : obj.key(),
            "size", String.valueOf(obj.size()),
            "lastModified", obj.lastModified().toString()
        ))
        .toList();
    }
}
