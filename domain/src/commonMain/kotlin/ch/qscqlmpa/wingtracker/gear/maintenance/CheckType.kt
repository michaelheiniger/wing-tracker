package ch.qscqlmpa.wingtracker.gear.maintenance

/**
 * Which types a piece of gear accepts is decided by its [GearKind]. Partial is the wing trim check;
 * Other covers anything else worth recording.
 */
enum class CheckType { Partial, Full, Check, Repack, Other }
