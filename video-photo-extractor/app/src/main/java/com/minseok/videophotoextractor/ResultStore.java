package com.minseok.videophotoextractor;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ResultStore {
    private static final String PREFS = "analysis_job";
    private static final String RESULT_FILE = "analysis_result.json";

    static final class State {
        final String status;
        final int progress;
        final String message;
        final int count;

        State(String status, int progress, String message, int count) {
            this.status = status;
            this.progress = progress;
            this.message = message;
            this.count = count;
        }

        boolean isRunning() { return "running".equals(status); }
        boolean isCompleted() { return "completed".equals(status); }
        boolean isError() { return "error".equals(status); }
    }

    private ResultStore() {}

    static synchronized void clear(Context context) {
        prefs(context).edit().clear().apply();
        File f = resultFile(context);
        if (f.exists()) f.delete();
    }

    static synchronized void markRunning(Context context, String message) {
        prefs(context).edit()
                .putString("status", "running")
                .putInt("progress", 0)
                .putString("message", message)
                .putInt("count", 0)
                .apply();
    }

    static synchronized void updateProgress(Context context, int progress, String message) {
        prefs(context).edit()
                .putString("status", "running")
                .putInt("progress", Math.max(0, Math.min(100, progress)))
                .putString("message", message == null ? "분석 중…" : message)
                .apply();
    }

    static synchronized void markCompleted(Context context, FrameAnalyzer.AnalyzeResult result) throws Exception {
        saveResult(context, result);
        prefs(context).edit()
                .putString("status", "completed")
                .putInt("progress", 100)
                .putString("message", "추출 완료")
                .putInt("count", result.items.size())
                .apply();
    }

    static synchronized void markError(Context context, String message) {
        prefs(context).edit()
                .putString("status", "error")
                .putInt("progress", 0)
                .putString("message", message == null ? "추출 실패" : message)
                .apply();
    }

    static State getState(Context context) {
        SharedPreferences p = prefs(context);
        return new State(
                p.getString("status", "idle"),
                p.getInt("progress", 0),
                p.getString("message", ""),
                p.getInt("count", 0));
    }

    static synchronized void saveResult(Context context, FrameAnalyzer.AnalyzeResult result) throws Exception {
        JSONObject root = new JSONObject();
        root.put("cropLeft", result.detectedCropOnScanFrame.left);
        root.put("cropTop", result.detectedCropOnScanFrame.top);
        root.put("cropRight", result.detectedCropOnScanFrame.right);
        root.put("cropBottom", result.detectedCropOnScanFrame.bottom);
        root.put("scanWidth", result.scanWidth);
        root.put("scanHeight", result.scanHeight);
        root.put("fallback", result.usedFallbackCrop);

        JSONArray items = new JSONArray();
        for (FrameAnalyzer.FrameItem item : result.items) {
            JSONObject o = new JSONObject();
            o.put("path", item.file.getAbsolutePath());
            o.put("timestamp", item.timestampMs);
            o.put("width", item.width);
            o.put("height", item.height);
            items.put(o);
        }
        root.put("items", items);

        try (FileOutputStream out = new FileOutputStream(resultFile(context))) {
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    static synchronized FrameAnalyzer.AnalyzeResult loadResult(Context context) {
        File f = resultFile(context);
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] bytes = new byte[(int) f.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int n = in.read(bytes, offset, bytes.length - offset);
                if (n < 0) break;
                offset += n;
            }
            JSONObject root = new JSONObject(new String(bytes, 0, offset, StandardCharsets.UTF_8));
            Rect crop = new Rect(
                    root.optInt("cropLeft", 0), root.optInt("cropTop", 0),
                    root.optInt("cropRight", 1), root.optInt("cropBottom", 1));
            List<FrameAnalyzer.FrameItem> list = new ArrayList<>();
            JSONArray items = root.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject o = items.getJSONObject(i);
                    File itemFile = new File(o.getString("path"));
                    if (!itemFile.exists()) continue;
                    list.add(new FrameAnalyzer.FrameItem(
                            itemFile,
                            o.optLong("timestamp", 0L),
                            o.optInt("width", 1),
                            o.optInt("height", 1)));
                }
            }
            return new FrameAnalyzer.AnalyzeResult(
                    list, crop,
                    root.optInt("scanWidth", 1),
                    root.optInt("scanHeight", 1),
                    root.optBoolean("fallback", false));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File resultFile(Context context) {
        return new File(context.getCacheDir(), RESULT_FILE);
    }
}
