package com.bradenkennedy.punishment.command.parser;

import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationParserTest {

    private final DurationParser<Object> parser = new DurationParser<>();

    private ArgumentParseResult<Duration> parse(CommandInput input) {
        return parser.parse(null, input);
    }

    @ParameterizedTest
    @CsvSource({"30s, 30", "5m, 300", "2h, 7200", "1d, 86400", "1w, 604800", "1d12h30m, 131400", "1H, 3600"})
    void parsesValidDurations(String token, long seconds) {
        assertEquals(Duration.ofSeconds(seconds), parse(CommandInput.of(token)).parsedValue().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1x", "d1", "1d-2h", "99999999999999999999s", "9999999999999999w"})
    void rejectsInvalidDurations(String token) {
        CommandInput input = CommandInput.of(token);
        assertTrue(parse(input).failure().isPresent());
        assertEquals(token, input.remainingInput());
    }

    @Test
    void consumesOnlyTheDurationToken() {
        CommandInput input = CommandInput.of("1h griefing");
        parse(input);
        assertEquals("griefing", input.readString());
    }

    @Test
    void suggestsCommonDurations() {
        parser.stringSuggestions(null, CommandInput.empty()).forEach(s -> assertTrue(parse(CommandInput.of(s)).parsedValue().isPresent()));
    }

    @ParameterizedTest
    @CsvSource({"0, 0s", "59, 59s", "3600, 1h", "131400, 1d12h30m", "694861, 1w1d1h1m1s"})
    void formatsDurations(long seconds, String expected) {
        assertEquals(expected, DurationParser.format(Duration.ofSeconds(seconds)));
    }
}
