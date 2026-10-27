# ADR 0001: Start with a single Maven module

- Status: Accepted
- Date: 2026-10-27
- Related: STEP 0.2, STEP 2

## Context

Serox has a JavaFX shell today, while browser navigation, rendering, search,
AI, storage, and MCP are planned for later steps. Splitting empty or tightly
coupled code into Maven modules now would add build and dependency management
overhead before those boundaries have been exercised.

## Decision

Start as one Maven module. Keep code grouped by responsibility and expose
interfaces at boundaries that STEP 2 identifies. Revisit a multi-module build
when at least one of these conditions is met:

- a subsystem has a stable public API and can be built and tested independently;
- a subsystem needs a distinct dependency set or release lifecycle;
- module boundaries materially improve isolation or enforce dependency direction.

Do not split modules only to mirror package names or the planned architecture
diagram. When a split is justified, record the proposed module graph and build
impact in a follow-up decision record.

## Consequences

- The initial build stays simple and JavaFX launch/package commands remain at
  the repository root.
- Package structure and dependency rules must keep future extraction possible.
- STEP 2 must validate boundaries before adding subsystems with external
  dependencies.
