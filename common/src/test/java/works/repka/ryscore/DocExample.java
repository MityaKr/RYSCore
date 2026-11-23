package works.repka.ryscore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** JSON examples from docs/for-mods.md: the block after the marker {@code <!-- doc-example:<tag> ... -->}. */
public final class DocExample {
    private DocExample() {}

    public static Path doc() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path p = dir; p != null; p = p.getParent()) {
            Path doc = p.resolve("docs").resolve("for-mods.md");
            if (Files.isRegularFile(doc)) return doc;
        }
        throw new IllegalStateException("docs/for-mods.md not found above " + dir);
    }

    public static String json(String tag) throws IOException {
        String text = Files.readString(doc(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        int marker = text.indexOf("<!-- doc-example:" + tag + " ");
        if (marker < 0) throw new IllegalStateException("no marker doc-example:" + tag);
        int open = text.indexOf("```json\n", marker);
        int close = text.indexOf("\n```", open + 8);
        if (open < 0 || close < 0) throw new IllegalStateException("no json block after doc-example:" + tag);
        return text.substring(open + 8, close);
    }
}
