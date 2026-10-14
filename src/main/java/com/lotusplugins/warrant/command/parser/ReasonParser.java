package com.lotusplugins.warrant.command.parser;

import java.util.Set;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.standard.StringParser;

public final class ReasonParser<C> implements ArgumentParser<C, String> {

    public static final String LEADING_SILENT_KEY = "silent-before-reason";
    private static final Set<String> SILENT_TOKENS = Set.of("-s", "--silent");

    private final StringParser<C> reason = new StringParser<>(StringParser.StringMode.GREEDY_FLAG_YIELDING);

    public static <C> ParserDescriptor<C, String> reasonParser() {
        return ParserDescriptor.of(new ReasonParser<>(), String.class);
    }

    @Override
    public ArgumentParseResult<String> parse(CommandContext<C> context, CommandInput input) {
        while (input.hasRemainingInput() && SILENT_TOKENS.contains(input.peekString())) {
            input.readString();
            context.store(LEADING_SILENT_KEY, true);
        }
        return reason.parse(context, input);
    }
}
