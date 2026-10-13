package com.bradenkennedy.punishment.api.model;

import java.util.List;

public record HistoryPage(List<Punishment> entries, int total) {

    public static HistoryPage empty() {
        return new HistoryPage(List.of(), 0);
    }
}
