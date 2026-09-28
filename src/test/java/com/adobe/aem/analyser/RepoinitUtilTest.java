package com.adobe.aem.analyser;

import org.apache.sling.feature.Feature;
import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.apache.sling.feature.analyser.task.impl.repoinitconflicts.ValidationReport;
import org.apache.sling.repoinit.parser.impl.ParseException;
import org.apache.sling.repoinit.parser.impl.RepoInitParserImpl;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.junit.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RepoinitUtilTest {

    @Test
    public void shouldRemoveConflictsInCustomerExample() throws Exception {
        Extension extension = textExtension(
                "create path (sling:Folder) /apps/namics/genericmultifield/readonly\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/css\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/js\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/readonly\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/css(sling:OrderedFolder)\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/js(sling:OrderedFolder)"
        );

        ValidationReport report = reportWithConflicts(
                conflict(
                        "create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/css",
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/css(sling:OrderedFolder)"
                ),
                conflict(
                        "create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/js",
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/js(sling:OrderedFolder)"
                )
        );

        RepoinitUtil.removeConflicts(report, extension);


        String expectedRepoinit =
                "create path (sling:Folder) /apps/namics/genericmultifield/readonly\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/readonly\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/css(sling:OrderedFolder)\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/js(sling:OrderedFolder)";
        assertEquals(expectedRepoinit, extension.getText());
    }

    @Test
    public void shouldRemoveConflictsInCustomerExampleDifferentOrder() throws Exception {
        Extension extension = textExtension(
                "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/readonly\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/css(sling:OrderedFolder)\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/js(sling:OrderedFolder)\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield/readonly\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/css\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/js\n"
        );


        ValidationReport report = reportWithConflicts(
                conflict(
                        "create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/css",
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/css(sling:OrderedFolder)"
                ),
                conflict(
                        "create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/js",
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/js(sling:OrderedFolder)"
                )
        );

        RepoinitUtil.removeConflicts(report, extension);


        String expectedRepoinit =
                "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/readonly\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/css(sling:OrderedFolder)\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/js(sling:OrderedFolder)\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield/readonly";
        assertEquals(expectedRepoinit, extension.getText());
    }

    @Test
    public void shouldPreserveCommentsWhenFixEnabled() {
        String original =
                "# origin=test\n" +
                        "create path (sling:Folder) /apps/a/b(sling:OrderedFolder)\n" +
                        "create path (sling:Folder) /apps/a/b";

        Extension extension = textExtension(original);

        RepoinitUtil.removeConflicts(emptyReport(), extension);

        assertTrue(extension.getText().contains("# origin=test"));
    }

    @Test
    public void shouldNotRemoveNonRegexConflictsEvenIfReported() throws Exception {
        Extension extension = textExtension(
                "   create path (sling:Folder) /apps/a/b(sling:OrderedFolder)\n" +
                        "   create path (sling:Folder) /apps/a/b"
        );

        ValidationReport report = reportWithConflicts(
                conflict(
                        "create path (sling:Folder) /apps/a/b(sling:OrderedFolder)",
                        "create path (sling:Folder) /apps/a/b"
                )
        );

        RepoinitUtil.removeConflicts(report, extension);

        assertEquals(
                "   create path (sling:Folder) /apps/a/b(sling:OrderedFolder)\n" +
                        "   create path (sling:Folder) /apps/a/b",
                extension.getText()
        );
    }

    @Test
    public void shouldKeepRepoinitTextWhenNoConflictsExist() {
        String original =
                "create path (sling:Folder) /apps/project/clientlibs/images\n" +
                        "set ACL on /apps/project\n" +
                        "  allow jcr:read for everyone";

        Extension extension = textExtension(original);

        RepoinitUtil.removeConflicts(emptyReport(), extension);

        assertEquals(original, extension.getText());
    }

    @Test
    public void shouldRemoveBothMatchingStatementsWhenBothAreReportedAsConflict() throws Exception {
        String original =
                "create path (sling:Folder) /apps/site/clientlibs/css\n" +
                        "create path (sling:Folder) /apps/site/clientlibs/js\n" +
                        "set ACL on /apps/site\n" +
                        "  allow jcr:read for everyone";

        Extension extension = textExtension(original);

        ValidationReport report = reportWithConflicts(
                conflict(
                        "create path (sling:Folder) /apps/site/clientlibs/css",
                        "create path (sling:Folder) /apps/site/clientlibs/js"
                )
        );

        RepoinitUtil.removeConflicts(report, extension);

        assertEquals(
                "set ACL on /apps/site\n" +
                        "  allow jcr:read for everyone",
                extension.getText()
        );
    }

    private Extension textExtension(String text) {
        Extension extension = mock(Extension.class);

        when(extension.getType()).thenReturn(ExtensionType.TEXT);
        when(extension.getText()).thenReturn(text);

        doAnswer(invocation -> {
            String newText = invocation.getArgument(0);
            when(extension.getText()).thenReturn(newText);
            return null;
        }).when(extension).setText(anyString());

        return extension;
    }

    private ValidationReport emptyReport() {
        return new ValidationReport();
    }

    @SafeVarargs
    private ValidationReport reportWithConflicts(CreatePath[]... conflicts) {
        ValidationReport report = new ValidationReport();
        Feature feature = mock(Feature.class);
        report.addConflicts(feature, List.of(conflicts));
        return report;
    }

    private CreatePath[] conflict(String first, String second) throws ParseException {
        return new CreatePath[] {
                parseCreatePath(first),
                parseCreatePath(second)
        };
    }

    private CreatePath parseCreatePath(String line) throws ParseException {
        List<Operation> operations = new RepoInitParserImpl(new StringReader(line)).parse();
        assertFalse(operations.isEmpty());
        return (CreatePath) operations.get(0);
    }
}