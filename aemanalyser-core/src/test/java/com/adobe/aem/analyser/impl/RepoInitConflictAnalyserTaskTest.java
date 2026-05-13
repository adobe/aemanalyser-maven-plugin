package com.adobe.aem.analyser.impl;

import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.analyser.task.AnalyserTask;
import org.apache.sling.feature.analyser.task.AnalyserTaskContext;
import org.junit.Test;
import org.mockito.Mockito;

import static junit.framework.TestCase.assertEquals;

public class RepoInitConflictAnalyserTaskTest {

    @Test
    public void testConstants() {
        final AnalyserTask task = new RepoInitConflictAnalyserTask();

        assertEquals("repoinit-conflict-validation", task.getId());
        assertEquals("Repoinit Conflict Validation", task.getName());
    }

    @Test
    public void shouldNotReportWarningWhenNoRepoinit() {
        final AnalyserTaskContext ctx = Mockito.mock(AnalyserTaskContext.class);

        Feature feature = Mockito.mock(Feature.class);
        org.apache.sling.feature.Extensions extensions = Mockito.mock(org.apache.sling.feature.Extensions.class);

        Mockito.when(ctx.getFeature()).thenReturn(feature);
        Mockito.when(feature.getExtensions()).thenReturn(extensions);
        Mockito.when(extensions.getByName("repoinit")).thenReturn(null);

        RepoInitConflictAnalyserTask task = new RepoInitConflictAnalyserTask();
        task.execute(ctx);

        Mockito.verify(ctx).getFeature();
        Mockito.verifyNoMoreInteractions(ctx);
    }

    @Test
    public void shouldNotReportWarningWhenNoConflicts() {
        final AnalyserTaskContext ctx = Mockito.mock(AnalyserTaskContext.class);

        Feature feature = featureWithExtension(textExtension(
                "create path (sling:Folder) /apps/a/b\n" +
                        "create path (sling:Folder) /apps/a/c"
        ));

        Mockito.when(ctx.getFeature()).thenReturn(feature);

        RepoInitConflictAnalyserTask task = new RepoInitConflictAnalyserTask();
        task.execute(ctx);

        Mockito.verify(ctx).getFeature();
        Mockito.verifyNoMoreInteractions(ctx);
    }

    @Test
    public void shouldReportWarningWhenConflictExists() {
        final AnalyserTaskContext ctx = Mockito.mock(AnalyserTaskContext.class);

        Feature feature = featureWithExtension(textExtension(
                "create path (sling:Folder) /apps/a/b(cq:ClientLibraryFolder)\n" +
                        "create path (sling:Folder) /apps/a/b"
        ));

        Mockito.when(ctx.getFeature()).thenReturn(feature);

        RepoInitConflictAnalyserTask task = new RepoInitConflictAnalyserTask();
        task.execute(ctx);

        Mockito.verify(ctx).getFeature();
        Mockito.verify(ctx).reportWarning(Mockito.contains("conflicting repoinit"));
        Mockito.verify(ctx).reportWarning(Mockito.contains("Conflicting statement"));
    }

    @Test
    public void shouldIgnoreInvalidRepoinitSyntax() {
        final AnalyserTaskContext ctx = Mockito.mock(AnalyserTaskContext.class);

        Feature feature = featureWithExtension(textExtension("invalid $$$"));

        Mockito.when(ctx.getFeature()).thenReturn(feature);

        RepoInitConflictAnalyserTask task = new RepoInitConflictAnalyserTask();
        task.execute(ctx);

        Mockito.verify(ctx).getFeature();
        Mockito.verifyNoMoreInteractions(ctx);
    }

    private Extension textExtension(String text) {
        Extension extension = Mockito.mock(Extension.class);
        Mockito.when(extension.getType()).thenReturn(ExtensionType.TEXT);
        Mockito.when(extension.getText()).thenReturn(text);
        return extension;
    }

    private Feature featureWithExtension(Extension extension) {
        Feature feature = Mockito.mock(Feature.class);
        org.apache.sling.feature.Extensions extensions = Mockito.mock(org.apache.sling.feature.Extensions.class);

        Mockito.when(feature.getExtensions()).thenReturn(extensions);
        Mockito.when(extensions.getByName("repoinit")).thenReturn(extension);

        return feature;
    }
}