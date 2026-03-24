package top.ligoudaner.classpoints;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView ivSplash = findViewById(R.id.iv_splash);
        int splashResId = getNextSplashResId();
        ivSplash.setImageResource(splashResId);

        // 显示 2 秒后跳转到 MainActivity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }, 2000);
    }

    private int getNextSplashResId() {
        SharedPreferences prefs = getSharedPreferences("SplashPrefs", MODE_PRIVATE);

        // 获取所有可用的资源ID
        List<Integer> allSplashResIds = Arrays.asList(
                R.drawable.splash1,
                R.drawable.splash2
        );

        // 获取已显示的资源ID集合（存储为字符串）
        Set<String> shownIdsSet = prefs.getStringSet("shown_splash_ids", new HashSet<>());
        List<Integer> shownIds = shownIdsSet.stream()
                .map(Integer::parseInt)
                .collect(Collectors.toList());

        // 计算还未显示的资源ID
        List<Integer> remainingIds = new ArrayList<>();
        for (Integer id : allSplashResIds) {
            if (!shownIds.contains(id)) {
                remainingIds.add(id);
            }
        }

        // 如果所有图片都显示过了，重置已显示列表
        if (remainingIds.isEmpty()) {
            shownIds.clear();
            remainingIds.addAll(allSplashResIds);
        }

        // 从未显示的图片中随机选一张
        Collections.shuffle(remainingIds);
        int nextId = remainingIds.get(0);

        // 更新已显示的图片列表
        shownIds.add(nextId);
        Set<String> newShownIdsSet = shownIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());
        prefs.edit().putStringSet("shown_splash_ids", newShownIdsSet).apply();

        return nextId;
    }
}
