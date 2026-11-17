import TimeServiceClient_Library.NtpSampleFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Replay known inputs through the same Java class used by the client adapter. */
public final class FilterReplay {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: FilterReplay <input.csv> <output.csv>");
        Map<String, NtpSampleFilter> filters = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        Path destination = Paths.get(args[1]).toAbsolutePath();
        Files.createDirectories(destination.getParent());
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(destination), StandardCharsets.UTF_8))) {
            out.println("scenario,request,true_offset_ms,raw_offset_ms,delay_ms,filtered_offset_ms,raw_abs_error_ms,filtered_abs_error_ms");
            for (String line : lines.subList(1, lines.size())) {
                String[] f = line.split(",", -1);
                NtpSampleFilter filter = filters.computeIfAbsent(f[0], k -> new NtpSampleFilter());
                long raw = Long.parseLong(f[3]);
                OptionalLong result = filter.add(raw, Long.parseLong(f[4]), Long.parseLong(f[5]));
                String filtered = result.isPresent() ? Long.toString(result.getAsLong()) : "";
                String rawError = f[2].isEmpty() ? "" : Long.toString(Math.abs(raw - Long.parseLong(f[2])));
                String filteredError = f[2].isEmpty() || !result.isPresent() ? "" :
                    Long.toString(Math.abs(result.getAsLong() - Long.parseLong(f[2])));
                out.println(String.join(",", f[0], f[1], f[2], f[3], f[4], filtered, rawError, filteredError));
            }
        }
        NtpSampleFilter boundary = new NtpSampleFilter();
        if (boundary.add(100, -1, 0).isPresent()) throw new AssertionError("negative delay accepted");
        boundary.add(100, 20, 0); boundary.add(100, 20, 1);
        if (boundary.add(100, 20, 2).orElse(Long.MIN_VALUE) != 100) throw new AssertionError("warmup");
        if (boundary.add(200, 20, 60_003).isPresent()) throw new AssertionError("stale samples reused");
        boundary.reset();
        if (boundary.add(200, 20, 60_004).isPresent()) throw new AssertionError("reset");
        System.out.println("PASS Java filter replay; negative delay, expiry, and reset checks passed");
    }
}
