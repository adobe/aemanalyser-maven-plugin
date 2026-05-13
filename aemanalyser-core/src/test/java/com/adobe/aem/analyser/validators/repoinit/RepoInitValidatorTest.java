package com.adobe.aem.analyser.validators.repoinit;

import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RepoInitValidatorTest {

    @Test
    public void shouldReturnNoIssuesWhenFeatureHasNoRepoinit() {
        Feature feature = mock(Feature.class);
        when(feature.getExtensions()).thenReturn(mock(org.apache.sling.feature.Extensions.class));
        when(feature.getExtensions().getByName("repoinit")).thenReturn(null);

        RepoInitValidationReport report = RepoInitValidator.validateRepoinit(feature);

        assertFalse(report.hasConflicts());
        assertTrue(report.generate().contains("No issues found"));
    }

    @Test
    public void shouldReturnNoIssuesForCorrectRepoinit() {
        Extension extension = textExtension(
                "create path (sling:Folder) /apps/a/b\n" +
                        "create path (sling:Folder) /apps/a/c\n" +
                        "create path (sling:Folder) /apps/a/c/d(cq:ClientLibraryFolder)"
        );

        Feature feature = featureWithExtension(extension);

        RepoInitValidationReport report = RepoInitValidator.validateRepoinit(feature);

        assertFalse(report.hasConflicts());
        assertTrue(report.generate().contains("No issues found"));
    }

    @Test
    public void shouldReportConflictForSamePathDifferentType() {
        Extension extension = textExtension(
                "create path (sling:Folder) /apps/a/b(cq:ClientLibraryFolder)\n" +
                        "create path (sling:Folder) /apps/a/b"
        );

        Feature feature = featureWithExtension(extension);

        RepoInitValidationReport report = RepoInitValidator.validateRepoinit(feature);
        assertTrue(report.hasConflicts());

        List<String> result = report.generate();
        assertTrue(result.get(1).contains("Incorrect repoinit for feature"));
        assertTrue(result.get(2).contains("Found 1 sets of conflicting repoinit statements"));
        assertTrue(result.get(3).contains("/apps/a/b"));
    }

    @Test
    public void shouldReportMultipleConflicts() {
        Extension extension = textExtension(
                "create path (sling:Folder) /apps/a/b(cq:ClientLibraryFolder)\n" +
                        "create path (sling:Folder) /apps/a/b\n" +
                        "create path (sling:Folder) /apps/x/y(cq:ClientLibraryFolder)\n" +
                        "create path (sling:Folder) /apps/x/y"
        );

        Feature feature = featureWithExtension(extension);

        RepoInitValidationReport report = RepoInitValidator.validateRepoinit(feature);
        assertTrue(report.hasConflicts());

        List<String> result = report.generate();
        assertTrue(result.get(2).contains("Found 2 sets of conflicting repoinit statements"));
    }

    @Test
    public void shouldIgnoreInvalidRepoinitSyntax() {
        Extension extension = textExtension("invalid $$$");

        Feature feature = featureWithExtension(extension);

        RepoInitValidationReport report = RepoInitValidator.validateRepoinit(feature);

        assertFalse(report.hasConflicts());
        assertTrue(report.generate().contains("No issues found"));
    }

    private Extension textExtension(String text) {
        Extension extension = mock(Extension.class);
        when(extension.getType()).thenReturn(ExtensionType.TEXT);
        when(extension.getText()).thenReturn(text);
        return extension;
    }

    private Feature featureWithExtension(Extension extension) {
        Feature feature = mock(Feature.class);
        org.apache.sling.feature.Extensions extensions = mock(org.apache.sling.feature.Extensions.class);

        when(feature.getExtensions()).thenReturn(extensions);
        when(extensions.getByName("repoinit")).thenReturn(extension);

        return feature;
    }
}