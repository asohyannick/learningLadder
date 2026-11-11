package com.learningLadder.learningLadder.utils.image;

import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class ImageOptimizer {

    public static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final int MAX_SOURCE_DIMENSION = 8_000;

    private static final Map<String, String> SUPPORTED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    /** An uploaded image, validated and ready to be optimized and stored. */
    public record PreparedImage(String objectKey, byte[] content, String contentType, int maxDimension) {
    }

    /**
     * Validates the upload and copies its bytes. Returns null when no file was sent.
     *
     * @param folder       MinIO folder, e.g. "partners/logos"
     * @param maxDimension longest side after optimization, in pixels
     * @param label        used in error messages, e.g. "Logo"
     */
    public PreparedImage prepare(MultipartFile file, String folder, int maxDimension, String label) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BadRequestException(label + " must not exceed 5 MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = SUPPORTED_TYPES.get(contentType);
        if (extension == null) {
            throw new BadRequestException(label + " must be a JPEG, PNG or WEBP image");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Could not read the " + label.toLowerCase(Locale.ROOT));
        }

        if (!"image/webp".equals(contentType)) {
            checkRealImage(content, label);
        }

        String objectKey = folder + "/" + UUID.randomUUID() + "." + extension;
        return new PreparedImage(objectKey, content, contentType, maxDimension);
    }

    /**
     * Background step: returns smaller bytes for oversized JPEG/PNG images, or the original bytes
     * when no change is needed or anything goes wrong (an unprocessed image is better than none).
     */
    public byte[] optimize(PreparedImage image) {
        boolean png = "image/png".equals(image.contentType());
        boolean jpeg = "image/jpeg".equals(image.contentType());
        if (!png && !jpeg) {
            return image.content();
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(image.content()));
            if (source == null) {
                return image.content();
            }
            int width = source.getWidth();
            int height = source.getHeight();
            int longest = Math.max(width, height);
            if (longest <= image.maxDimension()) {
                return image.content();
            }

            double scale = image.maxDimension() / (double) longest;
            int newWidth = Math.max(1, (int) Math.round(width * scale));
            int newHeight = Math.max(1, (int) Math.round(height * scale));

            BufferedImage resized = new BufferedImage(newWidth, newHeight,
                    png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = resized.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.drawImage(source, 0, 0, newWidth, newHeight, null);
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(resized, png ? "png" : "jpg", out)) {
                return image.content();
            }
            byte[] optimized = out.toByteArray();
            log.debug("Image {} resized {}x{} -> {}x{} ({} -> {} bytes)", image.objectKey(),
                    width, height, newWidth, newHeight, image.content().length, optimized.length);
            return optimized.length < image.content().length ? optimized : image.content();

        } catch (IOException | RuntimeException ex) {
            log.warn("Could not optimize image {}; uploading the original: {}", image.objectKey(), ex.getMessage());
            return image.content();
        }
    }

    private void checkRealImage(byte[] content, String label) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw new BadRequestException(label + " is not a valid image file");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION) {
                    throw new BadRequestException(label + " is too large: maximum "
                            + MAX_SOURCE_DIMENSION + " x " + MAX_SOURCE_DIMENSION + " pixels");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw new BadRequestException(label + " is not a valid image file");
        }
    }
}