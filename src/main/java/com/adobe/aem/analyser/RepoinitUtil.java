package com.adobe.aem.analyser;

import org.apache.sling.feature.Extension;
import org.apache.sling.feature.analyser.task.impl.repoinitconflicts.ValidationReport;
import org.apache.sling.repoinit.parser.impl.ParseException;
import org.apache.sling.repoinit.parser.impl.RepoInitParserImpl;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for handling repoinit conflicts in Sling feature extensions.
 *
 * <p>Provides methods to detect and remove conflicting {@code create path} statements
 * from repoinit extensions of Sling features.</p>
 */
class RepoinitUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepoinitUtil.class);

    private RepoinitUtil() {
    }

    /**
     * Pattern matching {@code create path} statements that are known to cause conflicts
     * for {@code clientlibs/css} and {@code clientlibs/js} paths under {@code /apps}.
     *
     * <p>Example matching line:
     * <pre>create path (sling:Folder) /apps/myapp/clientlibs/css</pre>
     * </p>
     *
     * <p>Note: This fix is targeted at a specific known issue and may not cover all conflict scenarios.</p>
     */
    private static final Pattern PATTERN = Pattern.compile(
            "create path \\(sling:Folder\\) /apps/[^\"(]+/clientlibs/(css|js)"
    );

    /**
     * Removes conflicting {@code create path} statements from the given repoinit extension.
     *
     * <p>Only lines that are present in the provided validation report conflicts and match
     * the internal {@link #PATTERN} are filtered out from the extension text.
     * The remaining lines are joined back and set as the new extension text.</p>
     *
     * @param repoInitValidationReport report from repoinit conflict validation
     * @param repoinitExtension the repoinit {@link Extension} whose text content should be cleaned up;
     *                          must not be {@code null} and must have a non-null text value
     */
    static void removeConflicts(ValidationReport repoInitValidationReport, Extension repoinitExtension) {
        if (repoInitValidationReport == null || repoinitExtension == null || repoinitExtension.getText() == null) {
            return;
        }

        Set<String> conflictingCreatePathStatements = collectConflictingCreatePathStatements(repoInitValidationReport);
        if (conflictingCreatePathStatements.isEmpty()) {
            return;
        }

        String originalText = repoinitExtension.getText();
        List<String> removedLines = new ArrayList<>();

        List<String> fixedLines = originalText.lines()
                .filter(line -> {
                    final String trimmedLine = line.trim();
                    final boolean matchesRawPattern = PATTERN.matcher(trimmedLine).matches();
                    final CreatePath lineAsCreatePath = matchesRawPattern ? parseCreatePath(trimmedLine) : null;
                    final boolean isConflicting = lineAsCreatePath != null && conflictingCreatePathStatements.contains(
                            normalizeCreatePathStatement(lineAsCreatePath.asRepoInitString()));
                    if (isConflicting) {
                        removedLines.add(line);
                    }
                    return !isConflicting;
                })
                .collect(Collectors.toList());

        if (!removedLines.isEmpty()) {
            LOGGER.debug("Removed {} repoinit conflict line(s): {}", removedLines.size(), removedLines);
        }

        String fixedText = String.join("\n", fixedLines);
        repoinitExtension.setText(fixedText);
    }

    private static Set<String> collectConflictingCreatePathStatements(final ValidationReport report) {
        return report.getConflicts().values().stream()
                .flatMap(List::stream)
                .flatMap(Arrays::stream)
                .map(CreatePath::asRepoInitString)
                .map(RepoinitUtil::normalizeCreatePathStatement)
                .collect(Collectors.toSet());
    }

    private static CreatePath parseCreatePath(final String line) {
        try {
            final List<Operation> operations = new RepoInitParserImpl(new StringReader(line)).parse();
            if (operations.size() == 1 && operations.get(0) instanceof CreatePath) {
                return (CreatePath) operations.get(0);
            }
        } catch (ParseException e) {
            LOGGER.warn("Unable to parse repoinit line as create path, skipping line: {}", line, e);
        }
        return null;
    }

    private static String normalizeCreatePathStatement(final String statement) {
        return statement == null ? "" : statement.stripTrailing();
    }
}