package com.bradenkennedy.punishment.command.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PageParserTest {

    private final PageParser<Object> parser = new PageParser<>();

    private ArgumentParseResult<Integer> parse(CommandInput input) {
        return parser.parse(null, input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "7", "2147483647"})
    void parsesPositivePages(String token) {
        assertEquals(
                Integer.parseInt(token),
                parse(CommandInput.of(token)).parsedValue().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "abc", "1.5", "2147483648"})
    void rejectsInvalidPages(String token) {
        assertTrue(parse(CommandInput.of(token)).failure().isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"--type ban", "-t ban"})
    void yieldsToFlagWithoutConsumingInput(String remaining) {
        CommandInput input = CommandInput.of(remaining);

        assertEquals(1, parse(input).parsedValue().orElseThrow());
        assertEquals(remaining, input.remainingInput());
    }

    @Test
    void consumesOnlyThePageToken() {
        CommandInput input = CommandInput.of("3 --type ban");

        assertEquals(3, parse(input).parsedValue().orElseThrow());
        assertEquals("--type ban", input.remainingInput().stripLeading());
    }
}
