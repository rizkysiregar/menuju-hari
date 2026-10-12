package com.menujuhari.media.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import net.coobird.thumbnailator.Thumbnails;

/**
 * Pemrosesan gambar (MEDIA-002): varian web (maks 1600 px, q~0.8)
 * dan thumbnail (maks 400 px). Format keluaran mengikuti format sumber
 * (JPEG tetap JPEG; PNG tetap PNG agar transparansi tidak hilang).
 */
@Component
public class ImageProcessor {

	public record ProcessedVariant(byte[] bytes, String contentType, int width, int height) {
	}

	public record Result(ProcessedVariant web, ProcessedVariant thumb) {
	}

	public Result process(byte[] original, String sourceContentType) throws IOException {
		var image = ImageIO.read(new ByteArrayInputStream(original));
		if (image == null) {
			throw new IOException("Gambar tidak dapat dibaca");
		}
		String format = sourceContentType.contains("png") ? "png" : "jpg";
		String outContentType = "png".equals(format) ? "image/png" : "image/jpeg";

		var web = render(image, 1600, 0.8, format, outContentType);
		var thumb = render(image, 400, 0.8, format, outContentType);
		return new Result(web, thumb);
	}

	private ProcessedVariant render(java.awt.image.BufferedImage source, int maxSize, double quality,
			String format, String contentType) throws IOException {
		var buffer = new java.io.ByteArrayOutputStream();
		Thumbnails.of(source)
				.size(maxSize, maxSize)
				.outputQuality(quality)
				.outputFormat(format)
				.toOutputStream(buffer);
		// Thumbnailator menyetel dimensi sesuai rasio; baca kembali untuk metadata.
		var rendered = ImageIO.read(new ByteArrayInputStream(buffer.toByteArray()));
		return new ProcessedVariant(buffer.toByteArray(), contentType, rendered.getWidth(), rendered.getHeight());
	}

	public static boolean isJpeg(byte[] bytes) {
		return bytes.length >= 3
				&& (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
	}

	public static boolean isPng(byte[] bytes) {
		return bytes.length >= 8
				&& (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
	}

	public static Map<String, Object> variantMap(String key, String contentType, int width, int height) {
		return Map.of("key", key, "contentType", contentType, "width", width, "height", height);
	}
}
