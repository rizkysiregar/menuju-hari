package com.menujuhari.media.application;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.menujuhari.media.infra.config.MinioProperties;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;

/**
 * Wrapper MinIO (S3-compatible). Portabilitas: hanya class ini yang
 * menyentuh API MinIO — migrasi ke R2/S3 berarti mengganti class ini.
 */
@Component
public class MinioStorage {

	private final MinioClient client;
	private final String bucket;

	public MinioStorage(MinioProperties props) {
		this.client = MinioClient.builder()
				.endpoint(props.endpoint())
				.credentials(props.accessKey(), props.secretKey())
				.build();
		this.bucket = props.bucket();
	}

	@PostConstruct
	void ensureBucket() {
		try {
			if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
				client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("MinIO tidak dapat dihubungi: " + e.getMessage(), e);
		}
	}

	public String presignPut(String objectKey, java.time.Duration ttl) {
		try {
			return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
					.method(Method.PUT)
					.bucket(bucket)
					.object(objectKey)
					.expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
					.build());
		}
		catch (Exception e) {
			throw new StorageException("Gagal membuat presigned upload URL", e);
		}
	}

	public String presignGet(String objectKey, java.time.Duration ttl) {
		try {
			return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
					.method(Method.GET)
					.bucket(bucket)
					.object(objectKey)
					.expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
					.build());
		}
		catch (Exception e) {
			throw new StorageException("Gagal membuat presigned download URL", e);
		}
	}

	/** Unduh objek (maks bekerja untuk objek kecil seperti foto <= 5 MB). */
	public byte[] download(String objectKey) {
		try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
			return in.readAllBytes();
		}
		catch (Exception e) {
			throw new StorageException("Gagal mengunduh objek: " + objectKey, e);
		}
	}

	public void upload(String objectKey, byte[] bytes, String contentType) {
		try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
			client.putObject(PutObjectArgs.builder()
					.bucket(bucket)
					.object(objectKey)
					.stream(in, bytes.length, -1)
					.contentType(contentType)
					.build());
		}
		catch (Exception e) {
			throw new StorageException("Gagal mengunggah objek: " + objectKey, e);
		}
	}

	public boolean exists(String objectKey) {
		try {
			client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}

	public long size(String objectKey) {
		try {
			return client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build()).size();
		}
		catch (Exception e) {
			throw new StorageException("Gagal membaca ukuran objek: " + objectKey, e);
		}
	}

	public void delete(String objectKey) {
		try {
			client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
		}
		catch (Exception e) {
			throw new StorageException("Gagal menghapus objek: " + objectKey, e);
		}
	}

	public static class StorageException extends RuntimeException {
		public StorageException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
