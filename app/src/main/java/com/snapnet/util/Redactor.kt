package com.snapnet.util

/**
 * Removes credentials from text that may be written to a log, shown in a notification, or copied
 * into a shared bug report.
 *
 * yt-dlp's own output is usually free of secrets, but the *command line* SnapNet builds contains
 * paths and headers, and a user may paste a debug log into a public issue. Defence in depth: scrub
 * anything credential-shaped at the point of emission rather than trusting the source.
 */
object Redactor {

    const val MASK = "<redacted>"

    /**
     * @param keepLabel when true the first capture group (e.g. `Authorization: `) is preserved so the
     *   sanitised text stays readable.
     */
    private data class Rule(val regex: Regex, val keepLabel: Boolean = false)

    private val RULES: List<Rule> =
        listOf(
            Rule(Regex("""(?i)(bearer\s+)([A-Za-z0-9._~+/=-]+)"""), keepLabel = true),
            Rule(Regex("""(?i)(authorization\s*[:=]\s*)(\S+)"""), keepLabel = true),
            Rule(Regex("""(?i)(set-cookie\s*:\s*)(.+)"""), keepLabel = true),
            Rule(Regex("""(?i)(\bcookie\s*[:=]\s*)(.+)"""), keepLabel = true),
            Rule(Regex("""(?i)(password\s*[:=]\s*)(\S+)"""), keepLabel = true),
            Rule(
                Regex("""(?i)(\b(?:api[_-]?key|access[_-]?token|auth[_-]?token|secret)\s*[:=]\s*)(\S+)"""),
                keepLabel = true,
            ),
            Rule(Regex("""(?i)(--cookies(?:-from-browser)?\s+)(\S+)"""), keepLabel = true),
            Rule(Regex("""\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]+""")),
            Rule(Regex("""\bgithub_pat_[A-Za-z0-9_]+""")),
            Rule(Regex("""\b(?:AKIA|ASIA)[0-9A-Z]{16}\b""")),
            Rule(Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----""")),
        )

    /** @return [input] with anything credential-shaped replaced by [MASK]. */
    fun redact(input: String?): String {
        // Deliberately explicit rather than relying on a smart cast: `out` must be non-null for the
        // Regex.replace overloads to resolve.
        if (input == null || input.isEmpty()) return ""
        var out: String = input
        for (rule in RULES) {
            out =
                if (rule.keepLabel) {
                    rule.regex.replace(out) { match -> match.groupValues[1] + MASK }
                } else {
                    rule.regex.replace(out, MASK)
                }
        }
        return out
    }
}
