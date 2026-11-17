package com.xauusdpro99.data.candle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class BiquoteOhlcClient {
    public static final String BASE_URL = "https://biquote.io/api/XAUUSD/ohlc";

    public List<Candle> fetch(CandleTimeframe timeframe) throws Exception {
        if (timeframe == null || !timeframe.nativeSupported || timeframe.biquoteInterval == null) {
            throw new IllegalArgumentException("BiQuote tidak mendokumentasikan interval native ini.");
        }
        String address = BASE_URL + "?interval=" + timeframe.biquoteInterval + "&limit=1000";
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new IllegalStateException("BiQuote OHLC HTTP " + code + " tanpa body");
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            if (code < 200 || code >= 300) throw new IllegalStateException("BiQuote OHLC HTTP " + code + ": " + body);
            JsonObject root = JsonParser.parseString(body.toString()).getAsJsonObject();
            if (!"XAUUSD".equalsIgnoreCase(string(root, "symbol"))) {
                throw new IllegalStateException("Simbol pada envelope bukan XAUUSD");
            }
            JsonArray bars = root.getAsJsonArray("bars");
            if (bars == null) throw new IllegalStateException("Respons OHLC tidak memiliki array bars");
            long receivedAt = System.currentTimeMillis();
            List<Candle> result = new ArrayList<>();
            for (JsonElement element : bars) {
                if (!element.isJsonObject()) continue;
                JsonObject b = element.getAsJsonObject();
                long openTime = Instant.parse(string(b, "openTime")).toEpochMilli();
                result.add(new Candle("XAUUSD", timeframe, openTime,
                        number(b, "open"), number(b, "high"), number(b, "low"), number(b, "close"),
                        optionalNumber(b, "volume"), optionalLong(b, "tickVolume"),
                        bool(b, "isOpen"), "BiQuote OHLC REST", receivedAt));
            }
            return result;
        } finally {
            connection.disconnect();
        }
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) throw new IllegalStateException("Field " + key + " tidak ada");
        return e.getAsString();
    }
    private static double number(JsonObject o, String key) { return o.get(key).getAsDouble(); }
    private static double optionalNumber(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : 0d;
    }
    private static long optionalLong(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : 0L;
    }
    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
    }
}
