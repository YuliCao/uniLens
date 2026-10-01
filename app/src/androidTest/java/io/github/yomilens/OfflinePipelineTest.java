package io.github.yomilens;

import static org.junit.Assert.*;

import android.graphics.*;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OfflinePipelineTest {
  @Test
  public void emojiReadingsStaySeparatedOnAndroid() {
    String text = "「日本語👨‍👩‍👧‍👦👍🏽🫠1️⃣東京」！";
    ReadingEngine.Reading reading = new ReadingEngine().read(text);
    assertEquals("「ni·hon·go ｜ tou·kyou」！", reading.romaji);
    assertEquals(text, reading.original);
  }

  @Test
  public void compareCaptureResolutionLatency() throws Exception {
    TextRecognizer ocr =
        TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
    try {
      for (int height : new int[] {960, 1280, 1600}) {
        Bitmap b = Bitmap.createBitmap(height * 9 / 16, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xfffcf8ef);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(height / 28f);
        p.setColor(Color.BLACK);
        String[] lines = {"日本語を勉強します。", "今日は良い天気ですね。", "東京へ行きます。", "学校で友達と会いました。"};
        for (int i = 0; i < lines.length; i++)
          c.drawText(lines[i], height / 40f, height * (.25f + i * .13f), p);
        long[] times = new long[5];
        for (int i = 0; i < times.length; i++) {
          long start = SystemClock.elapsedRealtime();
          Text result = Tasks.await(ocr.process(InputImage.fromBitmap(b, 0)), 30, TimeUnit.SECONDS);
          times[i] = SystemClock.elapsedRealtime() - start;
          assertTrue(result.getText(), result.getText().contains("日本語"));
          assertTrue(result.getText(), result.getText().contains("東京"));
        }
        Arrays.sort(times);
        Log.i(
            "YomiLensBenchmark",
            "height=" + height + " n=5 p50=" + times[2] + "ms max=" + times[4] + "ms");
        b.recycle();
      }
    } finally {
      ocr.close();
    }
  }

  @Test
  public void bundledOcrAndDictionaryReadJapaneseWithoutModelDownload() throws Exception {
    Bitmap b = Bitmap.createBitmap(1200, 500, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    c.drawColor(Color.WHITE);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(Color.BLACK);
    p.setTextSize(68);
    c.drawText("日本語を勉強します。", 50, 130, p);
    c.drawText("東京へ行きます。", 50, 260, p);
    TextRecognizer ocr =
        TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
    try {
      Text result = Tasks.await(ocr.process(InputImage.fromBitmap(b, 0)), 30, TimeUnit.SECONDS);
      assertTrue(result.getText(), result.getText().contains("日本語"));
      ReadingEngine.Reading reading = new ReadingEngine().read(result.getText());
      assertTrue(reading.romaji, reading.romaji.contains("ni·hon·go"));
      assertTrue(reading.romaji, reading.romaji.contains("tou·kyou"));
      assertFalse(result.getTextBlocks().isEmpty());
      assertNotNull(result.getTextBlocks().get(0).getBoundingBox());
    } finally {
      ocr.close();
      b.recycle();
    }
  }
}
