package io.github.yomilens;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Separate test APK: validates capture and touch-through across application packages. */
public final class ExternalFixtureActivity extends Activity {
  private boolean next;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    show();
  }

  private void show() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER);
    root.setPadding(30, 80, 30, 60);
    root.setBackgroundColor(Color.WHITE);
    String[] lines =
        next
            ? new String[] {"冒険を始めましょう。", "この世界を守りたい。", "新しい仲間が加わった！", "セーブしました。"}
            : new String[] {"日本語を勉強します。", "今日は良い天気ですね。", "東京へ行きます。", "学校で友達と会いました。"};
    for (String line : lines) {
      TextView text = new TextView(this);
      text.setText(line);
      text.setTextColor(Color.BLACK);
      text.setTextSize(26);
      text.setPadding(0, 38, 0, 38);
      root.addView(text);
    }
    Button button = new Button(this);
    button.setText("下一页 · 外部应用");
    button.setOnClickListener(
        v -> {
          next = !next;
          show();
        });
    root.addView(button);
    setContentView(root);
  }
}
