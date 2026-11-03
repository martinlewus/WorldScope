package dev.worldscope;

import java.util.List;

public record WorldScopeHit(
        WorldScopeResult result,
        List<WorldScopeBox> structureBoxes,
        List<WorldScopeBiomeCell> biomeCells
) {
    public WorldScopeHit {
        structureBoxes = List.copyOf(structureBoxes);
        biomeCells = List.copyOf(biomeCells);
    }
}
