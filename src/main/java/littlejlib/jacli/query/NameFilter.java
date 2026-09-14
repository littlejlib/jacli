package littlejlib.jacli.query;

import module java.base;

/**
 * What {@code --name} accepts. It began as a case-insensitive substring, which meant
 * {@code --name 'Poster|Responder'} answered "0 declarations - nothing named 'Poster|Responder'
 * exists here": literally true, and read by a caller as the tool being less capable than the grep it
 * was trying to replace. A text search would have taken that pattern, so this one does too.
 * <p>
 * Three forms, tried in order, all case-insensitive:
 * <ul>
 *   <li>{@code a|b|c} - any of these substrings. The commonest shape by far, because the question
 *       during a refactor is about a family of related names rather than one.</li>
 *   <li>{@code get*Name}, {@code ?ostit} - a glob, where {@code *} is any run and {@code ?} one
 *       character. Anchored at both ends, the way a filename glob is.</li>
 *   <li>anything else - a plain substring, exactly as before.</li>
 * </ul>
 * A leading {@code ~} forces the rest to be read as a full regular expression, for the caller who
 * wants one and knows it. Nothing else is treated as a regex: a bare {@code .} or {@code (} in a
 * name filter is far more likely to be someone typing a qualified name than someone writing a
 * pattern, and silently reinterpreting it would be the worst of both.
 */
public sealed interface NameFilter {

    boolean matches(String name);

    /** Never null; an absent filter accepts everything, so call sites need no null checks. */
    static NameFilter of(String spec) {
        if (spec == null || spec.isEmpty()) return new Any();
        if (spec.startsWith("~")) return new Regex(Pattern.compile(spec.substring(1), Pattern.CASE_INSENSITIVE));
        /* An anchor is unambiguous intent. A headless trial wrote --name "^px$" expecting a regex,
         * got it read as the literal substring "^px$", found nothing, and had to retry with a looser
         * pattern. Nothing is named with a leading ^ or a trailing $, so reading those as anchors
         * cannot cost a real match, and refusing to costs the caller a round trip. */
        if (spec.startsWith("^") || spec.endsWith("$"))
            return new Regex(Pattern.compile(spec, Pattern.CASE_INSENSITIVE));
        if (spec.indexOf('|') >= 0) return new AnyOf(Arrays.stream(spec.split("\\|"))
                .filter(s -> !s.isEmpty()).map(NameFilter::of).toList());
        if (spec.indexOf('*') >= 0 || spec.indexOf('?') >= 0) return new Glob(Pattern.compile(glob(spec),
                Pattern.CASE_INSENSITIVE));
        return new Substring(spec.toLowerCase(Locale.ROOT));
    }

    /** Quote everything, then re-open the two wildcards, so a name containing a dot cannot become one. */
    static String glob(String spec) {
        var sb = new StringBuilder();
        for (var part : spec.split("(?=[*?])|(?<=[*?])")) {
            if ("*".equals(part)) sb.append(".*");
            else if ("?".equals(part)) sb.append('.');
            else sb.append(Pattern.quote(part));
        }
        return sb.toString();
    }

    record Any() implements NameFilter {
        @Override public boolean matches(String name) { return true; }
    }

    record Substring(String lower) implements NameFilter {
        @Override public boolean matches(String name) { return name.toLowerCase(Locale.ROOT).contains(lower); }
    }

    record Glob(Pattern pattern) implements NameFilter {
        @Override public boolean matches(String name) { return pattern.matcher(name).matches(); }
    }

    record Regex(Pattern pattern) implements NameFilter {
        @Override public boolean matches(String name) { return pattern.matcher(name).find(); }
    }

    record AnyOf(List<NameFilter> parts) implements NameFilter {
        @Override public boolean matches(String name) { return parts.stream().anyMatch(p -> p.matches(name)); }
    }
}
