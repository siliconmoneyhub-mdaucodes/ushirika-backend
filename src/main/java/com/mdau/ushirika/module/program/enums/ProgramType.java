package com.mdau.ushirika.module.program.enums;

/**
 * MGR and BENEVOLENCE route join/enrollment actions to their existing dedicated
 * modules (module.mgr, module.benevolence) — Program is a catalog/management layer
 * on top of them, not a replacement. CUSTOM is for future programs with no
 * dedicated backing module yet.
 */
public enum ProgramType {
    MGR,
    BENEVOLENCE,
    CUSTOM
}
