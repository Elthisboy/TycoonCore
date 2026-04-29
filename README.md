# TycoonCore

## Project Identity
- **Name:** TycoonCore
- **Mod ID:** `tycooncore`
- **Version:** `1.0.6`

## Technical Summary
The **TycoonCore** mod functions as the comprehensive, centralized engine for a gym/business tycoon-style custom map. It orchestrates a massive suite of interdependent systems via deep integration with Fabric lifecycle events (`ServerTickEvents`). Its core loop manages live gym sessions that generate passive scoreboard income, tracks player fitness progression, and handles custom interactive equipment blocks (e.g., locking player movement during treadmill/weightlifting animations). Furthermore, it features a complex JSON-driven upgrade registry and networking layer that instantly synchronizes bossbars, active sabotage events, and persistent player data to the client upon connection.

## Feature Breakdown
- **Gym Session Engine:** Tracks active business hours, automatically generating scalable passive income at configurable tick intervals while the gym is flagged as "open." Includes auto-closing mechanics triggered at nightfall.
- **Dynamic Upgrade System:** Implements a fully JSON-driven upgrade tree (`UpgradeRegistry`), allowing map developers to infinitely scale categories, tiers, and purchasable items without altering Java code.
- **Fitness & Equipment Logic:** Introduces functional gym equipment blocks that intercept player inputs (blocking attacks/movement) to play custom exercise animations while updating backend fitness stats.
- **Sabotage & Supervisor Events:** Features a robust timed-event system managing equipment sabotages (requiring technician recovery) and mandatory, countdown-based supervisor approvals before a player can advance to a new business tier.
- **Persistent Data Management:** Utilizes a custom `PlayerDataManager` to save, load, and re-apply complex NBT states across sessions, including purchased upgrades, active bossbars, and persistent attribute modifiers.

## Command Registry
*Note: All commands require OP Permission Level 2.*

| Command | Description | Permission Level |
| :--- | :--- | :--- |
| `/pc event <sabotage\|recovery\|mission>` | Dev command to force-trigger specific computer management events or technical issues. | OP (2) |
| `/tycoon gym <start\|end\|status>` | Forces a gym to open, close, or outputs the current operational status. | OP (2) |

## Configuration Schema
The mod expects/generates the primary engine configuration at `config/gymcore/config.json`:

```json
{
  "scoreboard_name": "money",
  "currency_symbol": "$",
  "enable_passive_income": true,
  "passive_income_interval_ticks": 200,
  "tier_locations": {
    "2": { "x": 226.5, "y": -34.0, "z": 118.5, "yaw": 0.0 },
    "3": { "x": 421.5, "y": -40.0, "z": 122.5, "yaw": 0.0 }
  },
  "tier_commands": {
    "1": { 
      "on_open": ["title %player% title \"§aGym Opened\""], 
      "on_close": ["title %player% title \"§cGym Closed\""] 
    }
  },
  "supervisor_countdown_seconds": 10,
  "supervisor_approval_commands": {
    "2": [
      "tp %player% 100 64 200", 
      "tp %supervisor% 100 64 200"
    ]
  }
}
```

## Developer Info
- **Author:** El_this_boy
- **Platform:** Fabric 1.21.1
