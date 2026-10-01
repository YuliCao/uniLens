package io.github.yomilens;

import android.app.Activity;
import android.graphics.Typeface;
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

  private Button button(String s, boolean main) {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextSize(14);
    b.setTextColor(main ? 0xffffffff : Ui.ACCENT);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setBackground(Ui.button(this, main ? Ui.ACCENT : Ui.SOFT, 16, 0));
    b.setStateListAnimator(null);
    return b;
  }

  private void show() {
    int d = Ui.dp(this, 1);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(Gravity.CENTER);
    root.setPadding(24 * d, 48 * d, 24 * d, 32 * d);
    root.setBackgroundColor(Ui.BACKGROUND);
    for (String line : pages[page]) {
      TextView t = new TextView(this);
      t.setText(line);
      t.setTextColor(Ui.INK);
      t.setTextSize(26);
      t.setPadding(0, 13 * d, 0, 13 * d);
      root.addView(t);
    }
    LinearLayout row = new LinearLayout(this);
    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, 48 * d);
    rowLp.topMargin = 16 * d;
    root.addView(row, rowLp);
    Button back = button("返回", false);
    back.setOnClickListener(v -> finish());
    row.addView(back, new LinearLayout.LayoutParams(0, -1, 1));
    Button next = button("下一页", true);
    next.setOnClickListener(
        v -> {
          page = (page + 1) % pages.length;
          show();
        });
    LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(0, -1, 1);
    nextLp.leftMargin = 10 * d;
    row.addView(next, nextLp);
    setContentView(root);
  }
}
