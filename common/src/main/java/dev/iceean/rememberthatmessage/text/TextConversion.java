package dev.iceean.rememberthatmessage.text;

import com.google.gson.*;
import dev.iceean.rememberthatmessage.storage.JsonSupport;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/** 只转换不可变 JSON；不读取游戏注册表，不执行点击事件。 */
public final class TextConversion {
    public static final int MAX_TEXT_BYTES = 262144;
    private static final Set<String> STYLE = Set.of("color", "bold", "italic", "underlined", "strikethrough", "obfuscated", "insertion", "font");
    private static final Set<String> CONTENT = Set.of("text", "translate", "fallback", "with", "extra", "keybind", "type", "clickEvent", "hoverEvent", "click_event", "hover_event");
    private static final Map<String, String> CLICK_FIELDS = Map.of("open_url", "url", "run_command", "command", "suggest_command", "command", "copy_to_clipboard", "value", "change_page", "page");
    private TextConversion() { }

    public record Result(String json, Set<String> losses) {
        public Result { losses = Set.copyOf(losses); }
    }
    public static Result convert(String json, ComponentFormat source, ComponentFormat target) {
        Objects.requireNonNull(source); Objects.requireNonNull(target);
        if (source == target) return new Result(json, Set.of());
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) throw new IllegalArgumentException("History component exceeds conversion limit");
        var losses = new LinkedHashSet<String>();
        JsonElement value = JsonSupport.read(json, JsonElement.class);
        String converted = component(value, source, target, losses, 0).toString();
        if (converted.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) throw new IllegalArgumentException("Converted component exceeds limit");
        return new Result(converted, losses);
    }

    /** 无标记旧文件仅接受已知结构；没有明确编码差异的普通文本视为公共子集。 */
    public static ComponentFormat legacyFormat(String json, ComponentFormat target) {
        var flags = new boolean[2];
        inspectLegacy(JsonSupport.read(json, JsonElement.class), flags, 0);
        if (flags[0] && flags[1]) throw new IllegalArgumentException("Mixed legacy component encodings");
        return flags[0] ? ComponentFormat.MC_1_21_1 : flags[1] ? ComponentFormat.MC_26_3 : target;
    }
    private static void inspectLegacy(JsonElement value, boolean[] flags, int depth) {
        depth(depth);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("Null legacy component");
        if (value.isJsonPrimitive()) return;
        if (value.isJsonArray()) { for (var child : value.getAsJsonArray()) inspectLegacy(child, flags, depth + 1); return; }
        var object = value.getAsJsonObject();
        for (var key : object.keySet()) {
            if (!CONTENT.contains(key) && !STYLE.contains(key)) throw new IllegalArgumentException("Unknown unmarked component field: " + key);
        }
        flags[0] |= object.has("clickEvent") || object.has("hoverEvent");
        flags[1] |= object.has("click_event") || object.has("hover_event");
        for (String key : List.of("with", "extra")) if (object.has(key)) inspectLegacy(object.get(key), flags, depth + 1);
        for (String key : List.of("hoverEvent", "hover_event")) if (object.has(key)) {
            var hover = object.getAsJsonObject(key);
            if (!"show_text".equals(string(hover, "action", "")))
                throw new IllegalArgumentException("Unmarked non-text hover needs an explicit source encoding");
            var text = hover.get(key.equals("hoverEvent") ? "contents" : "value");
            if (text == null) throw new IllegalArgumentException("Unknown legacy hover structure");
            inspectLegacy(text, flags, depth + 1);
        }
    }
    private static void depth(int depth) {
        if (depth > 64) throw new IllegalArgumentException("History component nesting exceeds 64");
    }
    private static JsonElement component(JsonElement value, ComponentFormat source, ComponentFormat target, Set<String> losses, int depth) {
        depth(depth);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("Null component");
        if (value.isJsonPrimitive()) return value.deepCopy();
        if (value.isJsonArray()) {
            var result = new JsonArray();
            for (var child : value.getAsJsonArray()) result.add(component(child, source, target, losses, depth + 1));
            if (result.isEmpty()) throw new IllegalArgumentException("Empty component array");
            return result;
        }
        var input = value.getAsJsonObject(); var result = new JsonObject();
        if (input.has("text")) result.add("text", input.get("text").deepCopy());
        else if (input.has("translate")) {
            result.add("translate", input.get("translate").deepCopy());
            if (input.has("fallback")) result.add("fallback", input.get("fallback").deepCopy());
            if (input.has("with")) {
                var args = new JsonArray();
                for (var arg : input.getAsJsonArray("with")) args.add(component(arg, source, target, losses, depth + 1));
                result.add("with", args);
            }
        } else if (input.has("keybind")) result.add("keybind", input.get("keybind").deepCopy());
        else {
            result.addProperty("text", string(input, "fallback", "[历史内容无法转换]"));
            losses.add("unsupported_content");
        }
        if (input.has("type") && !Set.of("text", "translatable", "keybind").contains(string(input, "type", ""))) losses.add("content_type");
        for (var key : input.keySet()) if (!CONTENT.contains(key) && !STYLE.contains(key)) losses.add("field:" + key);
        for (var key : STYLE) if (input.has(key)) {
            var field = input.get(key);
            if (key.equals("font") && !field.isJsonPrimitive()) losses.add("font");
            else result.add(key, field.deepCopy());
        }
        if (input.has("extra")) {
            var children = new JsonArray();
            for (var child : input.getAsJsonArray("extra")) children.add(component(child, source, target, losses, depth + 1));
            if (!children.isEmpty()) result.add("extra", children);
        }
        String clickKey = source.modern() ? "click_event" : "clickEvent";
        String hoverKey = source.modern() ? "hover_event" : "hoverEvent";
        if (input.has(clickKey)) {
            var click = click(input.getAsJsonObject(clickKey), source, target, losses);
            if (click != null) result.add(target.modern() ? "click_event" : "clickEvent", click);
        }
        if (input.has(hoverKey)) result.add(target.modern() ? "hover_event" : "hoverEvent",
                hover(input.getAsJsonObject(hoverKey), source, target, losses, depth + 1));
        // 编码标记与实际字段冲突时必须拒绝，不能静默丢失互动。
        if (input.has(source.modern() ? "clickEvent" : "click_event") || input.has(source.modern() ? "hoverEvent" : "hover_event"))
            throw new IllegalArgumentException("Component fields disagree with encoding marker");
        return result;
    }
    private static JsonObject click(JsonObject value, ComponentFormat source, ComponentFormat target, Set<String> losses) {
        String action = string(value, "action", "");
        String modernField = CLICK_FIELDS.get(action);
        if (modernField == null) { losses.add("click:" + action); return null; }
        String field = source.modern() ? modernField : "value";
        var data = value.get(field);
        if (data == null || !data.isJsonPrimitive()) {
            losses.add("click_value:" + action);
            return null;
        }
        String text = data.getAsString();
        // 旧版允许任意字符串；仅在目标要求更严格时移除无法表示的交互。
        if (target.modern() && !validModernClick(action, text)) {
            losses.add("click_value:" + action);
            return null;
        }
        var result = new JsonObject(); result.addProperty("action", action);
        if (action.equals("change_page")) {
            if (target.modern()) result.addProperty("page", Integer.parseInt(text));
            else result.addProperty("value", text);
        } else result.addProperty(target.modern() ? modernField : "value", text);
        for (String key : value.keySet()) if (!key.equals("action") && !key.equals(field)) losses.add("click_field:" + key);
        return result;
    }
    private static boolean validModernClick(String action, String value) {
        // 与 26.3 的 UNTRUSTED_URI、POSITIVE_INT、CHAT_STRING 约束保持一致。
        if (action.equals("open_url")) {
            try {
                String scheme = new URI(value).getScheme();
                return scheme != null && Set.of("http", "https").contains(scheme.toLowerCase(Locale.ROOT));
            } catch (URISyntaxException failure) { return false; }
        }
        if (action.equals("change_page")) {
            try { return Integer.parseInt(value) > 0; }
            catch (NumberFormatException failure) { return false; }
        }
        if (action.equals("run_command") || action.equals("suggest_command")) {
            for (int i = 0; i < value.length(); i++) {
                char character = value.charAt(i);
                if (character == '\u00a7' || character < ' ' || character == '\u007f') return false;
            }
        }
        return true;
    }
    private static JsonObject hover(JsonObject value, ComponentFormat source, ComponentFormat target, Set<String> losses, int depth) {
        String action = string(value, "action", "");
        var result = new JsonObject(); result.addProperty("action", "show_text");
        JsonElement text;
        if (action.equals("show_text")) {
            var content = value.get(source.modern() ? "value" : "contents");
            if (content == null) throw new IllegalArgumentException("Missing hover text");
            text = component(content, source, target, losses, depth);
            for (var key : value.keySet()) if (!key.equals("action") && !key.equals(source.modern() ? "value" : "contents")) losses.add("hover_field:" + key);
        } else {
            losses.add("hover:" + action);
            var details = source.modern() ? value : value.has("contents") && value.get("contents").isJsonObject() ? value.getAsJsonObject("contents") : value;
            String readable = string(details, "id", string(details, "type", "[悬浮内容无法完整转换]"));
            if (details.has("count")) readable += " × " + details.get("count").getAsString();
            text = new JsonPrimitive(readable);
        }
        result.add(target.modern() ? "value" : "contents", text);
        return result;
    }
    private static String string(JsonObject object, String key, String fallback) {
        var value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }
}
