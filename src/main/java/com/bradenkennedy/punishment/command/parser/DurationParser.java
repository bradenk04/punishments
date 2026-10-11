package com.bradenkennedy.punishment.command.parser;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

public final class DurationParser<C> implements ArgumentParser<C, Duration>, BlockingSuggestionProvider.Strings<C> {

    private static final Pattern FORMAT = Pattern.compile("(\\d+[smhdw])+");
    private static final Pattern SEGMENT = Pattern.compile("(\\d+)([smhdw])");
    private static final Map<String, Long> SECONDS = Map.of("s", 1L, "m", 60L, "h", 3600L, "d", 86400L, "w", 604800L);
    private static final List<String> UNITS_DESCENDING = List.of("w", "d", "h", "m", "s");

    public static <C> ParserDescriptor<C, Duration> durationParser() {
        return ParserDescriptor.of(new DurationParser<>(), Duration.class);
    }

    public static String format(Duration duration) {
        long remaining = duration.toSeconds();
        StringBuilder out = new StringBuilder();
        for (String unit : UNITS_DESCENDING) {
            long size = SECONDS.get(unit);
            if (remaining >= size) {
                out.append(remaining / size).append(unit);
                remaining %= size;
            }
        }
        return out.isEmpty() ? "0s" : out.toString();
    }

    @Override
    public ArgumentParseResult<Duration> parse(CommandContext<C> context, CommandInput input) {
        String token = input.peekString().toLowerCase();
        if (!FORMAT.matcher(token).matches()) {
            return ArgumentParseResult.failure(new IllegalArgumentException("Invalid duration: " + token));
        }
        try {
            long seconds = 0;
            for (Matcher m = SEGMENT.matcher(token); m.find(); ) {
                seconds =
                        Math.addExact(seconds, Math.multiplyExact(Long.parseLong(m.group(1)), SECONDS.get(m.group(2))));
            }
            input.readString();
            return ArgumentParseResult.success(Duration.ofSeconds(seconds));
        } catch (ArithmeticException | NumberFormatException e) {
            return ArgumentParseResult.failure(new IllegalArgumentException("Duration too large: " + token));
        }
    }

    @Override
    public Iterable<String> stringSuggestions(CommandContext<C> context, CommandInput input) {
        return List.of("30m", "1h", "1d", "7d", "30d");
    }
}
