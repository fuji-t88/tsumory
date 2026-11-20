import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * WBS の Markdown（作業用の原本）と Excel（最終成果物）を行き来させる。
 *
 * <pre>
 * java tools/WbsSync.java export WBS_tsumory.xlsx WBS_tsumory.md        Excel の値を Markdown に書き出す
 * java tools/WbsSync.java apply  WBS_tsumory.md WBS_tsumory.xlsx [--dry-run]
 *                                                                        Markdown の値を Excel に書き込む
 * </pre>
 *
 * apply は値だけを書き換え、書式・数式・入力規則・テーブルは Excel 側のものを残す。
 * 数式のセル、テーブルの見出し、入力規則の候補にない値、Excel にない行への書き込みは、エラーにして書き込まない。
 */
public class WbsSync {

    private static final Pattern ANCHOR = Pattern.compile("^<!-- @(\\d+) cols=([A-Z]+(?:,[A-Z]+)*) -->$");
    private static final Pattern DATE_TEXT = Pattern.compile("^(\\d{4})/(\\d{1,2})/(\\d{1,2})$");
    private static final Pattern NUMBER_TEXT = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    public static void main(String[] args) throws IOException {
        if (System.console() == null) {
            // パイプや Git Bash では Windows の既定（MS932）で化けるので、UTF-8 で出す
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        }
        if (args.length >= 3 && args[0].equals("export")) {
            export(Path.of(args[1]), Path.of(args[2]));
        } else if (args.length >= 3 && args[0].equals("apply")) {
            boolean dryRun = args.length >= 4 && args[3].equals("--dry-run");
            apply(Path.of(args[1]), Path.of(args[2]), dryRun);
        } else {
            System.err.println("usage: export <xlsx> <md> | apply <md> <xlsx> [--dry-run]");
            System.exit(2);
        }
    }

    // ---------------------------------------------------------------- workbook

    record Sheet(String name, String path) {}

    record Cell(String ref, String attrs, String inner, int start, int end) {
        String type() {
            var m = Pattern.compile("\\bt=\"(\\w+)\"").matcher(attrs);
            return m.find() ? m.group(1) : "n";
        }

        int style() {
            var m = Pattern.compile("\\bs=\"(\\d+)\"").matcher(attrs);
            return m.find() ? Integer.parseInt(m.group(1)) : 0;
        }

        String formula(String p) {
            if (inner == null) return null;
            var m = Pattern.compile("<" + p + "f(?:\\s[^>]*)?>(.*?)</" + p + "f>", Pattern.DOTALL).matcher(inner);
            if (m.find()) return unescapeXml(m.group(1));
            return Pattern.compile("<" + p + "f[\\s/>]").matcher(inner).find() ? "" : null;
        }

        String rawValue(String p) {
            if (inner == null) return null;
            var m = Pattern.compile("<" + p + "v>(.*?)</" + p + "v>", Pattern.DOTALL).matcher(inner);
            if (m.find()) return unescapeXml(m.group(1));
            var is = Pattern.compile("<" + p + "is>(.*?)</" + p + "is>", Pattern.DOTALL).matcher(inner);
            return is.find() ? unescapeXml(is.group(1).replaceAll("<[^>]+>", "")) : null;
        }
    }

    static final class Workbook {
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        final List<Sheet> sheets = new ArrayList<>();
        final List<String> sharedStrings = new ArrayList<>();
        final Set<Integer> dateStyles = new HashSet<>();

        Workbook(Path xlsx) throws IOException {
            try (InputStream in = Files.newInputStream(xlsx);
                    var zip = new ZipInputStream(in)) {
                ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    if (!e.isDirectory()) entries.put(e.getName(), zip.readAllBytes());
                }
            }
            String wb = text("xl/workbook.xml");
            String rels = text("xl/_rels/workbook.xml.rels");
            var targets = new HashMap<String, String>();
            for (var tag : tags(rels, "Relationship")) {
                targets.put(attr(tag, "Id"), resolve("xl/", attr(tag, "Target")));
            }
            for (var tag : tags(wb, "sheet")) {
                sheets.add(new Sheet(unescapeXml(attr(tag, "name")), targets.get(attr(tag, "r:id"))));
            }
            if (entries.containsKey("xl/sharedStrings.xml")) {
                var m = Pattern.compile("<(?:\\w+:)?si>(.*?)</(?:\\w+:)?si>", Pattern.DOTALL)
                        .matcher(text("xl/sharedStrings.xml"));
                while (m.find()) {
                    var t = Pattern.compile("<(?:\\w+:)?t(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?t>", Pattern.DOTALL)
                            .matcher(m.group(1));
                    var sb = new StringBuilder();
                    while (t.find()) sb.append(unescapeXml(t.group(1)));
                    sharedStrings.add(sb.toString());
                }
            }
            readDateStyles(text("xl/styles.xml"));
        }

        private void readDateStyles(String styles) {
            var custom = new HashMap<Integer, String>();
            for (var tag : tags(styles, "numFmt")) {
                custom.put(Integer.parseInt(attr(tag, "numFmtId")), unescapeXml(attr(tag, "formatCode")));
            }
            var xfs = Pattern.compile("<(?:\\w+:)?cellXfs\\b.*?</(?:\\w+:)?cellXfs>", Pattern.DOTALL)
                    .matcher(styles);
            if (!xfs.find()) return;
            int i = 0;
            for (var tag : tags(xfs.group(), "xf")) {
                String id = attr(tag, "numFmtId");
                int fmt = id == null ? 0 : Integer.parseInt(id);
                String code = custom.getOrDefault(fmt, "").replaceAll("\"[^\"]*\"", "");
                if ((fmt >= 14 && fmt <= 22) || code.matches(".*[yd].*")) dateStyles.add(i);
                i++;
            }
        }

        String text(String name) {
            byte[] b = entries.get(name);
            if (b == null) throw new IllegalStateException("not found in xlsx: " + name);
            return new String(b, StandardCharsets.UTF_8);
        }

        Sheet sheet(String name) {
            return sheets.stream()
                    .filter(s -> s.name().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Excel にないシート: " + name));
        }

        /** シートに付いたテーブルの見出し行のセル。 */
        Set<String> tableHeaderRefs(Sheet sheet) {
            var refs = new HashSet<String>();
            String dir = sheet.path().substring(0, sheet.path().lastIndexOf('/') + 1);
            String relsName = dir + "_rels/" + sheet.path().substring(dir.length()) + ".rels";
            if (!entries.containsKey(relsName)) return refs;
            for (var tag : tags(text(relsName), "Relationship")) {
                if (!attr(tag, "Type").endsWith("/table")) continue;
                String table = text(resolve(dir, attr(tag, "Target")));
                var head = tags(table, "table").getFirst();
                String hc = attr(head, "headerRowCount");
                if ("0".equals(hc)) continue;
                var range = attr(head, "ref").split(":");
                int row = rowOf(range[0]);
                for (int c = colIndex(colOf(range[0])); c <= colIndex(colOf(range[1])); c++) {
                    refs.add(colName(c) + row);
                }
            }
            return refs;
        }

        String display(Cell c, String p) {
            String f = c.formula(p);
            if (f != null) return "=" + f;
            String v = c.rawValue(p);
            if (v == null) return "";
            return switch (c.type()) {
                case "s" -> sharedStrings.get(Integer.parseInt(v.trim()));
                case "n" -> dateStyles.contains(c.style()) && NUMBER_TEXT.matcher(v).matches()
                        ? EXCEL_EPOCH.plusDays((long) Double.parseDouble(v)).format(DATE_FORMAT)
                        : v;
                default -> v;
            };
        }

        void write(Path target) throws IOException {
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp);
                    var zip = new ZipOutputStream(out)) {
                for (var e : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(e.getKey()));
                    zip.write(e.getValue());
                    zip.closeEntry();
                }
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Files.deleteIfExists(tmp);
                throw new IOException("書き込めない（Excel で開いていれば閉じる）: " + target, ex);
            }
        }
    }

    static String prefixOf(String sheetXml) {
        var m = Pattern.compile("<(\\w+:)?worksheet\\b").matcher(sheetXml);
        return m.find() && m.group(1) != null ? m.group(1) : "";
    }

    static Map<Integer, List<Cell>> rows(String xml, String p) {
        var rows = new TreeMap<Integer, List<Cell>>();
        var rm = Pattern.compile("<" + p + "row\\b([^>]*?)(?:/>|>(.*?)</" + p + "row>)", Pattern.DOTALL)
                .matcher(xml);
        while (rm.find()) {
            int r = Integer.parseInt(attr(rm.group(1), "r"));
            var cells = new ArrayList<Cell>();
            if (rm.group(2) != null) {
                int base = rm.start(2);
                var cm = Pattern.compile("<" + p + "c\\b([^>]*?)(?:/>|>(.*?)</" + p + "c>)", Pattern.DOTALL)
                        .matcher(rm.group(2));
                while (cm.find()) {
                    cells.add(new Cell(
                            attr(cm.group(1), "r"),
                            cm.group(1),
                            cm.group(2),
                            base + cm.start(),
                            base + cm.end()));
                }
            }
            rows.put(r, cells);
        }
        return rows;
    }

    static Set<String> mergeContinuations(String xml) {
        var set = new HashSet<String>();
        for (var tag : tags(xml, "mergeCell")) {
            var range = attr(tag, "ref").split(":");
            if (range.length < 2) continue;
            int c1 = colIndex(colOf(range[0])), c2 = colIndex(colOf(range[1]));
            int r1 = rowOf(range[0]), r2 = rowOf(range[1]);
            for (int r = r1; r <= r2; r++) {
                for (int c = c1; c <= c2; c++) {
                    if (r != r1 || c != c1) set.add(colName(c) + r);
                }
            }
        }
        return set;
    }

    // ---------------------------------------------------------------- export

    static void export(Path xlsx, Path md) throws IOException {
        var wb = new Workbook(xlsx);
        var out = new StringBuilder();
        out.append("# WBS tsumory（作業用の原本）\n\n");
        out.append("<!--\n");
        out.append("  このファイルを正として更新し、Excel（WBS_tsumory.xlsx）へは指示したときに反映する。\n");
        out.append("    反映: java tools/WbsSync.java apply WBS_tsumory.md WBS_tsumory.xlsx\n");
        out.append("  「## シート名」の下の各ブロックは、直前の「@行 cols=列,...」のコメントの位置のセルに対応する。\n");
        out.append("    表の 1 行目（見出しの行）がその行、以降の行が 1 行ずつ下の行。列は cols の順。\n");
        out.append("    「- 」で始まる行は、cols の 1 列に上から順に入る（「-」だけの行は空のセル）。\n");
        out.append("  セルの中の | は \\| 、改行は <br> 、\\ は \\\\ と書く。\n");
        out.append("  「=」で始まる値は Excel の数式。ここで書き換えても反映されない（apply がエラーにする）。\n");
        out.append("  行を足すとき（テストケースなど）は、Excel 側の行・テーブル・数式の範囲も広げる必要がある。\n");
        out.append("-->\n");
        for (var sheet : wb.sheets) {
            String xml = wb.text(sheet.path());
            String p = prefixOf(xml);
            var merged = mergeContinuations(xml);
            var values = new TreeMap<Integer, Map<Integer, String>>();
            for (var row : rows(xml, p).entrySet()) {
                for (var c : row.getValue()) {
                    String v = wb.display(c, p);
                    if (!v.isEmpty()) {
                        values.computeIfAbsent(row.getKey(), k -> new TreeMap<>())
                                .put(colIndex(colOf(c.ref())), v);
                    }
                }
            }
            out.append("\n## ").append(sheet.name()).append("\n");
            var run = new ArrayList<Integer>();
            for (int r : values.keySet()) {
                if (!run.isEmpty() && r != run.getLast() + 1) {
                    writeBlock(out, run, values, merged);
                    run.clear();
                }
                run.add(r);
            }
            if (!run.isEmpty()) writeBlock(out, run, values, merged);
        }
        Files.writeString(md, out.toString(), StandardCharsets.UTF_8);
        System.out.println("exported " + wb.sheets.size() + " sheets -> " + md);
    }

    private static void writeBlock(
            StringBuilder out, List<Integer> run, Map<Integer, Map<Integer, String>> values, Set<String> merged) {
        int min = Integer.MAX_VALUE, max = 0;
        for (int r : run) {
            for (int c : values.get(r).keySet()) {
                min = Math.min(min, c);
                max = Math.max(max, c);
            }
        }
        var cols = new ArrayList<Integer>();
        for (int c = min; c <= max; c++) {
            final int col = c;
            boolean allMerged = run.stream().allMatch(r -> merged.contains(colName(col) + r));
            if (!allMerged) cols.add(c);
        }
        out.append("\n<!-- @")
                .append(run.getFirst())
                .append(" cols=")
                .append(String.join(",", cols.stream().map(WbsSync::colName).toList()))
                .append(" -->\n");
        if (cols.size() == 1) {
            for (int r : run) {
                String v = values.get(r).getOrDefault(cols.getFirst(), "");
                out.append(v.isEmpty() ? "-" : "- " + escapeMd(v)).append("\n");
            }
            return;
        }
        boolean first = true;
        for (int r : run) {
            out.append("|");
            for (int c : cols) out.append(" ").append(escapeMd(values.get(r).getOrDefault(c, ""))).append(" |");
            out.append("\n");
            if (first) {
                out.append("|").append("---|".repeat(cols.size())).append("\n");
                first = false;
            }
        }
    }

    // ---------------------------------------------------------------- apply

    static void apply(Path md, Path xlsx, boolean dryRun) throws IOException {
        var wb = new Workbook(xlsx);
        var assignments = parseMarkdown(Files.readAllLines(md, StandardCharsets.UTF_8));
        var errors = new ArrayList<String>();
        var changes = new ArrayList<String>();
        for (var entry : assignments.entrySet()) {
            Sheet sheet;
            try {
                sheet = wb.sheet(entry.getKey());
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
                continue;
            }
            String xml = wb.text(sheet.path());
            String updated = applySheet(wb, sheet, xml, entry.getValue(), errors, changes);
            if (!updated.equals(xml)) wb.entries.put(sheet.path(), updated.getBytes(StandardCharsets.UTF_8));
        }
        changes.forEach(c -> System.out.println("  " + c));
        if (!errors.isEmpty()) {
            errors.forEach(e -> System.err.println("ERROR " + e));
            System.err.println(errors.size() + " 件のエラー。Excel は書き換えていない");
            System.exit(1);
        }
        if (dryRun) {
            System.out.println(changes.size() + " セルが変わる（--dry-run のため書き込んでいない）");
            return;
        }
        if (!changes.isEmpty()) wb.write(xlsx);
        System.out.println(changes.size() + " セルを書き換えた -> " + xlsx);
    }

    /** シート名 → (セル参照 → 値)。 */
    static Map<String, Map<String, String>> parseMarkdown(List<String> lines) {
        var result = new LinkedHashMap<String, Map<String, String>>();
        String sheet = null;
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i).stripTrailing();
            if (line.startsWith("## ")) {
                sheet = line.substring(3).trim();
                result.putIfAbsent(sheet, new LinkedHashMap<>());
                i++;
                continue;
            }
            Matcher a = ANCHOR.matcher(line);
            if (!a.matches()) {
                i++;
                continue;
            }
            if (sheet == null) throw new IllegalArgumentException((i + 1) + " 行目: シートの見出し（## ）より前にブロックがある");
            int row = Integer.parseInt(a.group(1));
            String[] cols = a.group(2).split(",");
            var target = result.get(sheet);
            i++;
            if (i < lines.size() && lines.get(i).startsWith("|")) {
                boolean header = true;
                while (i < lines.size() && lines.get(i).startsWith("|")) {
                    String l = lines.get(i).stripTrailing();
                    i++;
                    if (!header && l.matches("^\\|(\\s*:?-+:?\\s*\\|)+$")) continue;
                    header = false;
                    var cells = splitRow(l);
                    if (cells.size() != cols.length) {
                        throw new IllegalArgumentException(
                                i + " 行目: 列の数が cols と合わない（" + cells.size() + " / " + cols.length + "）");
                    }
                    for (int c = 0; c < cols.length; c++) put(target, cols[c] + row, cells.get(c), sheet, i);
                    row++;
                }
            } else {
                if (cols.length != 1) throw new IllegalArgumentException(i + " 行目: 「- 」の行のブロックは cols を 1 列にする");
                while (i < lines.size() && (lines.get(i).equals("-") || lines.get(i).startsWith("- "))) {
                    String l = lines.get(i).stripTrailing();
                    i++;
                    put(target, cols[0] + row, l.length() <= 2 ? "" : unescapeMd(l.substring(2)), sheet, i);
                    row++;
                }
            }
        }
        return result;
    }

    private static void put(Map<String, String> target, String ref, String value, String sheet, int line) {
        if (target.put(ref, value) != null) {
            throw new IllegalArgumentException(line + " 行目: " + sheet + "!" + ref + " に 2 つのブロックが書いている");
        }
    }

    private static String applySheet(
            Workbook wb,
            Sheet sheet,
            String xml,
            Map<String, String> values,
            List<String> errors,
            List<String> changes) {
        String p = prefixOf(xml);
        var rows = rows(xml, p);
        var lists = validationLists(xml);
        var headers = wb.tableHeaderRefs(sheet);
        var edits = new TreeMap<Integer, String[]>(); // start offset -> {end, replacement}
        var heightRows = new HashMap<String, String>(); // "行:列" -> 新しい値
        for (var e : values.entrySet()) {
            String ref = e.getKey(), value = e.getValue(), where = sheet.name() + "!" + ref;
            int r = rowOf(ref);
            var row = rows.get(r);
            if (row == null) {
                if (!value.isEmpty()) errors.add(where + ": Excel にない行（行を足すときは Excel 側の範囲も広げる）");
                continue;
            }
            Cell cell = row.stream().filter(c -> c.ref().equals(ref)).findFirst().orElse(null);
            String current = cell == null ? "" : wb.display(cell, p);
            if (current.equals(value)) continue;
            if (cell != null && cell.formula(p) != null) {
                errors.add(where + ": 数式のセルは書き換えない（" + current + "）");
                continue;
            }
            if (headers.contains(ref)) {
                errors.add(where + ": テーブルの見出しは書き換えない（Excel のテーブルの列名とずれる）");
                continue;
            }
            for (var list : lists.entrySet()) {
                if (inRanges(ref, list.getKey()) && !value.isEmpty() && !list.getValue().contains(value)) {
                    errors.add(where + ": 「" + value + "」は入力規則の候補にない " + list.getValue());
                }
            }
            String newCell = cellXml(p, ref, cell, value, wb);
            if (cell != null) {
                edits.put(cell.start(), new String[] {String.valueOf(cell.end()), newCell});
            } else if (!value.isEmpty()) {
                int at = insertionPoint(xml, p, row, r, ref);
                edits.put(at, new String[] {String.valueOf(at), newCell});
            }
            changes.add(where + ": " + abbreviate(current) + " -> " + abbreviate(value));
            heightRows.merge(r + ":" + colOf(ref), value, (a, b) -> a.length() >= b.length() ? a : b);
        }
        var sb = new StringBuilder(xml);
        for (var e : edits.descendingMap().entrySet()) {
            sb.replace(e.getKey(), Integer.parseInt(e.getValue()[0]), e.getValue()[1]);
        }
        return growRowHeights(sb.toString(), p, heightRows);
    }

    private static int insertionPoint(String xml, String p, List<Cell> row, int r, String ref) {
        int col = colIndex(colOf(ref));
        for (var c : row) {
            if (colIndex(colOf(c.ref())) > col) return c.start();
        }
        if (!row.isEmpty()) return row.getLast().end();
        var m = Pattern.compile("<" + p + "row\\b[^>]*\\br=\"" + r + "\"[^>]*>").matcher(xml);
        if (m.find() && !m.group().endsWith("/>")) return m.end();
        throw new IllegalArgumentException("行 " + r + " が空要素のため、セルを足せない");
    }

    private static String cellXml(String p, String ref, Cell cell, String value, Workbook wb) {
        String attrs = cell == null ? " r=\"" + ref + "\"" : cell.attrs().replaceAll("\\s*\\bt=\"\\w+\"", "");
        if (value.isEmpty()) return "<" + p + "c" + attrs + " />";
        if (cell != null && cell.type().equals("n")) {
            var d = DATE_TEXT.matcher(value);
            if (d.matches() && wb.dateStyles.contains(cell.style())) {
                var date = LocalDate.of(
                        Integer.parseInt(d.group(1)), Integer.parseInt(d.group(2)), Integer.parseInt(d.group(3)));
                long serial = ChronoUnit.DAYS.between(EXCEL_EPOCH, date);
                return "<" + p + "c" + attrs + " t=\"n\"><" + p + "v>" + serial + "</" + p + "v></" + p + "c>";
            }
            if (NUMBER_TEXT.matcher(value).matches()) {
                return "<" + p + "c" + attrs + " t=\"n\"><" + p + "v>" + value + "</" + p + "v></" + p + "c>";
            }
        }
        return "<" + p + "c" + attrs + " t=\"str\"><" + p + "v>" + escapeXml(value) + "</" + p + "v></" + p + "c>";
    }

    /** 文字が増えたセルの行を、列の幅から見積もった高さまで広げる（狭めはしない）。 */
    private static String growRowHeights(String xml, String p, HashMap<String, String> cells) {
        var widths = new ArrayList<double[]>();
        for (var tag : tags(xml, "col")) {
            widths.add(new double[] {
                Double.parseDouble(attr(tag, "min")),
                Double.parseDouble(attr(tag, "max")),
                Double.parseDouble(attr(tag, "width"))
            });
        }
        var merges = new HashMap<String, String>();
        for (var tag : tags(xml, "mergeCell")) {
            var range = attr(tag, "ref").split(":");
            if (range.length == 2) merges.put(range[0], range[1]);
        }
        var need = new HashMap<Integer, Integer>();
        for (var e : cells.entrySet()) {
            var key = e.getKey().split(":");
            int row = Integer.parseInt(key[0]);
            String col = key[1];
            int c1 = colIndex(col), c2 = c1;
            String end = merges.get(col + row);
            if (end != null && rowOf(end) == row) c2 = colIndex(colOf(end));
            double w = 0;
            for (int c = c1; c <= c2; c++) w += width(widths, c);
            int lines = (int) Math.ceil(e.getValue().length() * 2 / Math.max(w - 1, 2));
            need.merge(row, lines * 15 + 8, Math::max);
        }
        for (var e : need.entrySet()) {
            var m = Pattern.compile("<" + p + "row\\b[^>]*\\br=\"" + e.getKey() + "\"[^>]*>").matcher(xml);
            if (!m.find()) continue;
            String tag = m.group();
            var ht = Pattern.compile("\\bht=\"([\\d.]+)\"").matcher(tag);
            if (!ht.find() || Double.parseDouble(ht.group(1)) >= e.getValue()) continue;
            String newTag = tag.substring(0, ht.start()) + "ht=\"" + e.getValue() + "\"" + tag.substring(ht.end());
            xml = xml.substring(0, m.start()) + newTag + xml.substring(m.end());
        }
        return xml;
    }

    private static double width(List<double[]> widths, int col) {
        for (var w : widths) {
            if (col >= w[0] && col <= w[1]) return w[2];
        }
        return 8.43;
    }

    private static Map<String, List<String>> validationLists(String xml) {
        var lists = new LinkedHashMap<String, List<String>>();
        var m = Pattern.compile(
                        "<(?:\\w+:)?dataValidation\\b([^>]*)>\\s*<(?:\\w+:)?formula1>\"(.*?)\"</(?:\\w+:)?formula1>",
                        Pattern.DOTALL)
                .matcher(xml);
        while (m.find()) {
            if (!"list".equals(attr(m.group(1), "type"))) continue;
            lists.put(attr(m.group(1), "sqref"), List.of(unescapeXml(m.group(2)).split(",", -1)));
        }
        return lists;
    }

    private static boolean inRanges(String ref, String sqref) {
        int c = colIndex(colOf(ref)), r = rowOf(ref);
        for (String range : sqref.split("\\s+")) {
            var parts = range.split(":");
            String a = parts[0], b = parts.length > 1 ? parts[1] : parts[0];
            if (c >= colIndex(colOf(a)) && c <= colIndex(colOf(b)) && r >= rowOf(a) && r <= rowOf(b)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- helpers

    static List<String> tags(String xml, String local) {
        var list = new ArrayList<String>();
        var m = Pattern.compile("<(?:\\w+:)?" + local + "\\b[^>]*>").matcher(xml);
        while (m.find()) list.add(m.group());
        return list;
    }

    static String attr(String tag, String name) {
        var m = Pattern.compile("(?:^|\\s)" + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    static String resolve(String base, String target) {
        if (target.startsWith("/")) return target.substring(1);
        var parts = new ArrayList<>(List.of((base + target).split("/")));
        var out = new ArrayList<String>();
        for (String s : parts) {
            if (s.equals("..")) out.removeLast();
            else if (!s.isEmpty() && !s.equals(".")) out.add(s);
        }
        return String.join("/", out);
    }

    static String colOf(String ref) {
        return ref.replaceAll("\\d", "");
    }

    static int rowOf(String ref) {
        return Integer.parseInt(ref.replaceAll("[A-Z]", ""));
    }

    static int colIndex(String col) {
        int n = 0;
        for (char ch : col.toCharArray()) n = n * 26 + (ch - 'A' + 1);
        return n;
    }

    static String colName(int n) {
        var sb = new StringBuilder();
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String unescapeXml(String s) {
        var m = Pattern.compile("&#(x?)([0-9A-Fa-f]+);").matcher(s);
        var sb = new StringBuilder();
        while (m.find()) {
            int cp = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(cp))));
        }
        m.appendTail(sb);
        return sb.toString()
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    static String escapeMd(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\|").replace("\r\n", "<br>").replace("\n", "<br>");
    }

    static String unescapeMd(String s) {
        var sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                sb.append(s.charAt(++i));
            } else if (s.startsWith("<br>", i)) {
                sb.append('\n');
                i += 3;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    static List<String> splitRow(String line) {
        var cells = new ArrayList<String>();
        var cur = new StringBuilder();
        String body = line.substring(1);
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '\\' && i + 1 < body.length()) {
                cur.append(ch).append(body.charAt(++i));
            } else if (ch == '|') {
                cells.add(unescapeMd(cur.toString().strip()));
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        return cells;
    }

    static String abbreviate(String s) {
        String t = s.isEmpty() ? "（空）" : s;
        return t.length() > 30 ? t.substring(0, 30) + "…" : t;
    }
}
