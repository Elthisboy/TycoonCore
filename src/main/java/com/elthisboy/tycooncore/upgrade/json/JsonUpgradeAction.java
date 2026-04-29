package com.elthisboy.tycooncore.upgrade.json;

import java.util.HashMap;
import java.util.Map;

/**
 * A single action that runs when an upgrade is purchased.
 *
 * Supported types:
 *   "command"   – executes a server command (supports {player}, {level}, {uuid} placeholders)
 *   "schematic" – triggers schematic placement; use "file" field for the schematic name
 *                 and optionally a "command" param for the actual run command
 *   "effect"    – applies a built-in player effect (e.g. "speed")
 */
public class JsonUpgradeAction {
    public String type = "";
    public String value = "";  // command text or effect id
    public String file = "";   // schematic file name (schematic type)
    public Map<String, String> params = new HashMap<>();
}
