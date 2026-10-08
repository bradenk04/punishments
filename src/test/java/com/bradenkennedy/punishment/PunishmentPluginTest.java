package com.bradenkennedy.punishment;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PunishmentPluginTest {

    @Test
    void getInstanceThrowsBeforeEnable() {
        assertThrows(IllegalStateException.class, PunishmentPlugin::getInstance);
    }
}
