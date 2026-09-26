package io.github.yomilens;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

/** A reproducible reading fixture available without a network or another app. */
public final class SampleActivity extends Activity {
  private int page = 0;
  private final String[][] pages = {
    {"日本語を勉強します。", "今日は良い天気ですね。", "東京へ行きます。", "学校で友達と会いました。"},
    {"冒険を始めましょう。", "この世界を守りたい。", "新しい仲間が加わった！", "セーブしました。"},
    {"コーヒーを飲みます。", "チケットを買いました。", "新しいゲームを始める。", "HP 100 / MP 50"}
  };

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
    root.setBackgroundColor(0xfffcf8ef);
    TextView title = new TextView(this);
    title.setText("YomiLens 识别测试 · " + (page + 1));
    title.setTextColor(0xff167d8d);
    title.setTextSize(16);
    root.addView(title);
    for (String line : pages[page]) {
      TextView t = new TextView(this);
      t.setText(line);
      t.setTextColor(Color.BLACK);
      t.setTextSize(26);
      t.setPadding(0, 38, 0, 38);
      root.addView(t);
    }
    Button next = new Button(this);
    next.setText("下一页 · 验证点击穿透");
    next.setOnClickListener(
        v -> {
          page = (page + 1) % pages.length;
          show();
        });
    root.addView(next);
    Button back = new Button(this);
    back.setText("返回设置");
    back.setOnClickListener(v -> finish());
    root.addView(back);
    setContentView(root);
  }
}
