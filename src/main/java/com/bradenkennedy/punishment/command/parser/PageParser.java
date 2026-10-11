package com.bradenkennedy.punishment.command.parser;

import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

public final class PageParser<C> implements ArgumentParser<C, Integer>, BlockingSuggestionProvider.Strings<C> {

    private final IntegerParser<C> pages = new IntegerParser<>(1, Integer.MAX_VALUE);

    public static <C> ParserDescriptor<C, Integer> pageParser() {
        return ParserDescriptor.of(new PageParser<>(), Integer.class);
    }

    @Override
    public ArgumentParseResult<Integer> parse(CommandContext<C> context, CommandInput input) {
        if (input.hasRemainingInput() && input.peek() == '-') {
            return ArgumentParseResult.success(1);
        }
        return pages.parse(context, input);
    }

    @Override
    public Iterable<String> stringSuggestions(CommandContext<C> context, CommandInput input) {
        return pages.stringSuggestions(context, input);
    }
}
