package com.adobe.aem.analyser;

import org.apache.sling.feature.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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
     * <p>Lines matching the internal {@link #PATTERN} are filtered out from the extension text.
     * The remaining lines are joined back and set as the new extension text.</p>
     *
     * @param repoinitExtension the repoinit {@link Extension} whose text content should be cleaned up;
     *                          must not be {@code null} and must have a non-null text value
     */
    static void removeConflicts(Extension repoinitExtension) {
        String originalText = repoinitExtension.getText();
        List<String> removedLines = new ArrayList<>();

        List<String> fixedLines = originalText.lines()
                .filter(line -> {
                    boolean isConflicting = PATTERN.matcher(line).matches();
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
}