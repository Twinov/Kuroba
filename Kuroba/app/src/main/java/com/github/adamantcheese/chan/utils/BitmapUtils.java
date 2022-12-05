package com.github.adamantcheese.chan.utils;

import static android.graphics.Bitmap.CompressFormat.JPEG;
import static android.graphics.Bitmap.CompressFormat.PNG;
import static android.graphics.Bitmap.CompressFormat.WEBP;
import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.core.repository.BitmapRepository.rs;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;

import android.content.Context;
import android.graphics.*;
import android.graphics.Bitmap.CompressFormat;
import android.media.MediaMetadataRetriever;
import android.util.Base64;

import androidx.annotation.*;
import androidx.core.util.Pair;
import androidx.exifinterface.media.ExifInterface;
import androidx.renderscript.*;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.presenter.ImageReencodingPresenter;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.google.common.io.Files;

import java.io.*;
import java.util.Arrays;

import kotlin.random.Random;

public class BitmapUtils {
    private static final String TAG = "BitmapUtils";
    private static final int PIXEL_DIFF = 5;

    private static final byte[] PNG_HEADER = new byte[]{(byte) 137, 'P', 'N', 'G', '\r', '\n', 26, '\n'};
    private static final byte[] JPEG_HEADER = new byte[]{(byte) 0xFF, (byte) 0xD8};
    private static final byte[] WEBP_HEADER1 = new byte[]{'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_HEADER2 = new byte[]{'W', 'E', 'B', 'P'};

    private static final BitmapFactory.Options options = new BitmapFactory.Options();

    static {
        options.inMutable = true;
    }

    public static File reencodeBitmapFile(
            @NonNull File inputBitmapFile,
            @NonNull ImageReencodingPresenter.ImageOptions imageOptions,
            @Nullable CompressFormat newFormat
    )
            throws IOException {
        if (imageOptions.areOptionsInvalid())
            throw new IllegalArgumentException("Image options not formatted correctly.");
        Bitmap bitmap = BitmapFactory.decodeFile(inputBitmapFile.getAbsolutePath(), options);
        Matrix matrix = new Matrix();

        //slightly change one pixel of the image to change it's checksum
        if (imageOptions.changeImageChecksum) {
            int randomX = Math.abs(Random.Default.nextInt()) % bitmap.getWidth();
            int randomY = Math.abs(Random.Default.nextInt()) % bitmap.getHeight();

            // one pixel is enough to change the checksum of an image
            int pixel = bitmap.getPixel(randomX, randomY);

            // NOTE: apparently when re-encoding jpegs, changing a pixel by 1 is sometimes not enough
            // due to the jpeg's compression algorithm (it may even out this pixel with surrounding
            // pixels like it wasn't changed at all) so we have to increase the difference a little bit
            if (pixel - PIXEL_DIFF >= 0) {
                pixel -= PIXEL_DIFF;
            } else {
                pixel += PIXEL_DIFF;
            }

            bitmap.setPixel(randomX, randomY, pixel);
        }

        //scale the image down
        if (imageOptions.reducePercent > 0) {
            float scale = (100f - (float) imageOptions.reducePercent) / 100f;
            matrix.setScale(scale, scale);
        }

        //fix exif
        if (imageOptions.fixExif) {
            try {
                ExifInterface exif = new ExifInterface(inputBitmapFile.getAbsolutePath());
                matrix.postRotate(exif.getRotationDegrees());
            } catch (Exception ignored) {}
        }

        Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle();

        if (imageOptions.blur && rs != null) {
            Allocation input = Allocation.createFromBitmap(rs, newBitmap);
            Allocation output = Allocation.createTyped(rs, input.getType());
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            script.setRadius(16f);
            script.setInput(input);
            script.forEach(output);
            output.copyTo(newBitmap);
            input.destroy();
            output.destroy();
        }

        File tempFile = null;

        try {
            deleteOldTempFiles(getCacheDir().listFiles());

            tempFile = File.createTempFile("temp_file_name", null, getCacheDir());

            try (FileOutputStream output = new FileOutputStream(tempFile)) {
                newBitmap.compress(newFormat, imageOptions.reencodeQuality, output);
            }

            return tempFile;
        } catch (Throwable error) {
            File[] list = new File[1];
            list[0] = tempFile;
            deleteOldTempFiles(list);

            throw error;
        } finally {
            if (newBitmap != null) {
                newBitmap.recycle();
            }
        }
    }

    private static void deleteOldTempFiles(File[] files) {
        if (files == null || files.length == 0) {
            return;
        }

        for (File file : files) {
            if ("tmp".equalsIgnoreCase(Files.getFileExtension(file.getAbsolutePath()))) {
                if (!file.delete()) {
                    Logger.w(TAG, "Could not delete old temp image file: " + file.getAbsolutePath());
                }
            }
        }
    }

    public static boolean isFileSupportedForReencoding(File file) {
        return getImageFormat(file) != null;
    }

    public static CompressFormat getImageFormat(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[16];
            raf.read(header);

            if (JavaUtils.arrayPrefixedWith(header, PNG_HEADER)) {
                return PNG;
            }

            if (JavaUtils.arrayPrefixedWith(header, JPEG_HEADER)) {
                return JPEG;
            }

            if (JavaUtils.arrayPrefixedWith(header, WEBP_HEADER1)) {
                if (JavaUtils.arrayPrefixedWith(Arrays.copyOfRange(header, 8, 16), WEBP_HEADER2)) {
                    return WEBP;
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the dimensions of the specified image file
     *
     * @param file image
     * @return a pair of dimensions, in WIDTH then HEIGHT order; -1, -1 if not determinable
     */
    @NonNull
    public static Pair<Integer, Integer> getImageDims(File file) {
        try (FileInputStream fileStream = new FileInputStream(file)) {
            Bitmap bitmap = BitmapFactory.decodeStream(fileStream);
            return new Pair<>(bitmap.getWidth(), bitmap.getHeight());
        } catch (Exception e) {
            return new Pair<>(-1, -1);
        }
    }

    /**
     * Decode the given byte data into a Bitmap, scaling it if necessary.
     *
     * @param data      bytes to decode
     * @param maxWidth  the max width of the image, 0 to leave as-is
     * @param maxHeight the max height of the image, 0 to leave as-is
     * @return a bitmap, scaled to the max width and height if needed
     */
    public static Bitmap decode(InputStream data, float maxWidth, float maxHeight) {
        Bitmap tempBitmap = BitmapFactory.decodeStream(data);
        if (tempBitmap == null || options.outWidth == -1 || options.outHeight == -1) return null;

        // Scale this image, if necessary
        return scaleBitmap(tempBitmap, maxWidth, maxHeight);
    }

    private static Bitmap scaleBitmap(Bitmap input, float maxWidth, float maxHeight) {
        int actualWidth = input.getWidth();
        int actualHeight = input.getHeight();

        // Then compute the dimensions we would ideally like.
        float desiredWidth = getResizedDimension(maxWidth, maxHeight, actualWidth, actualHeight);
        float desiredHeight = getResizedDimension(maxHeight, maxWidth, actualHeight, actualWidth);

        // If necessary, scale down to the maximal acceptable size.
        Bitmap bitmap;
        if (actualWidth > desiredWidth || actualHeight > desiredHeight) {
            bitmap = Bitmap.createScaledBitmap(input, (int) desiredWidth, (int) desiredHeight, true);
            input.recycle();
        } else {
            bitmap = input;
        }
        return bitmap;
    }

    private static float getResizedDimension(
            float maxPrimary, float maxSecondary, float actualPrimary, float actualSecondary
    ) {
        // If no dominant value at all, just return the actual.
        if (maxPrimary == 0 && maxSecondary == 0) {
            return actualPrimary;
        }

        // If primary is unspecified, scale primary to match secondary's scaling ratio.
        if (maxPrimary == 0) {
            return (int) (actualPrimary * ((double) maxSecondary / (double) actualSecondary));
        }

        if (maxSecondary == 0) {
            return maxPrimary;
        }

        double ratio = (double) actualSecondary / (double) actualPrimary;
        return maxPrimary * ratio > maxSecondary ? (int) (maxSecondary / ratio) : maxPrimary;
    }

    /**
     * Decode the given byte data into a Bitmap, scaling it if necessary.
     *
     * @param file      file to decode
     * @param maxWidth  the max width of the image, 0 to leave as-is
     * @param maxHeight the max height of the image, 0 to leave as-is
     * @return a bitmap, scaled to the max width and height if needed
     */
    public static Bitmap decodeFile(File file, float maxWidth, float maxHeight) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return BitmapUtils.decode(fis, maxWidth, maxHeight);
        } catch (Throwable e) {
            Logger.w(TAG, "", e);
            return null;
        }
    }

    public static Bitmap decode(Context c, @DrawableRes int resId) {
        return BitmapFactory.decodeResource(c.getResources(), resId);
    }

    public static Bitmap decodeFilePreviewImage(
            final File file, float maxWidth, float maxHeight, final ImageDecoderCallback callback, boolean addAudioIcon
    ) {
        if (callback != null) {
            BackgroundUtils.runOnBackgroundThread(() -> {
                Bitmap result = decodeFilePreviewImage(file, maxWidth, maxHeight, addAudioIcon);
                BackgroundUtils.runOnMainThread(() -> callback.onImageBitmap(result));
            });
            return null;
        } else {
            return decodeFilePreviewImage(file, maxWidth, maxHeight, addAudioIcon);
        }
    }

    private static Bitmap decodeFilePreviewImage(
            final File file, float maxWidth, float maxHeight, boolean addAudioIcon
    ) {
        Bitmap result = BitmapRepository.paddedError;
        try {
            // Decode normally, scaling if necessary
            result = decodeFile(file, maxWidth, maxHeight);
        } catch (Exception ignored) {
        }
        try {
            // Decode some sort of media file
            MediaMetadataRetriever video = new MediaMetadataRetriever();
            video.setDataSource(file.getAbsolutePath());
            Bitmap frameBitmap = video.getFrameAtTime(0);
            if (frameBitmap == null) {
                throw new Exception("No bitmap for media!");
            }
            result = scaleBitmap(frameBitmap, maxWidth, maxHeight);
            boolean hasAudio = "yes".equals(video.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
            if (hasAudio && addAudioIcon) {
                Bitmap audioIconBitmap =
                        BitmapFactory.decodeResource(getRes(), R.drawable.ic_fluent_speaker_2_24_filled);
                Bitmap audioBitmap = Bitmap.createScaledBitmap(audioIconBitmap,
                        audioIconBitmap.getWidth() * 3,
                        audioIconBitmap.getHeight() * 3,
                        true
                );
                Bitmap tempBitmap = Bitmap.createBitmap(result.getWidth(), result.getHeight(), result.getConfig());
                Canvas temp = new Canvas(tempBitmap);
                temp.drawBitmap(result, new Matrix(), null);
                temp.drawBitmap(audioBitmap,
                        frameBitmap.getWidth() - audioBitmap.getWidth(),
                        frameBitmap.getHeight() - audioBitmap.getHeight(),
                        null
                );
                result = tempBitmap;
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    public static String asBase64(Bitmap bitmap) {
        if (bitmap == null) return "";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    public interface ImageDecoderCallback {
        void onImageBitmap(Bitmap bitmap);
    }
}
