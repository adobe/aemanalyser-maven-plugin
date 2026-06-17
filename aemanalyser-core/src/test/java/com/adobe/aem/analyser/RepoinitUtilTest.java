package com.adobe.aem.analyser;

import org.apache.sling.feature.Extension;
import org.apache.sling.feature.ExtensionType;
import org.junit.Test;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RepoinitUtilTest {

    @Test
    public void shouldRemoveConflictsInCustomerExample() {
        Extension extension = textExtension(
                "create path (sling:Folder) /apps/namics/genericmultifield/readonly\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/css\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/js\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/readonly\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/css(sling:OrderedFolder)\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/js(sling:OrderedFolder)"
        );

        RepoinitUtil.removeConflicts(extension);


        String expectedRepoinit =
                "create path (sling:Folder) /apps/namics/genericmultifield/readonly\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/readonly\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/css(sling:OrderedFolder)\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/js(sling:OrderedFolder)";
        assertEquals(expectedRepoinit, extension.getText());
    }

    @Test
    public void shouldRemoveConflictsInCustomerExampleDifferentOrder() {
        Extension extension = textExtension(
                "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/readonly\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/css(sling:OrderedFolder)\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield(sling:Folder)/clientlibs/js(sling:OrderedFolder)\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield/readonly\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/css\n" +
                        "create path (sling:Folder) /apps/namics/genericmultifield/clientlibs/js\n"
        );


        RepoinitUtil.removeConflicts(extension);


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

        RepoinitUtil.removeConflicts(extension);

        assertTrue(extension.getText().contains("# origin=test"));
    }

    @Test
    public void shouldHandleLeadingWhitespace() {
        Extension extension = textExtension(
                "   create path (sling:Folder) /apps/a/b(sling:OrderedFolder)\n" +
                        "   create path (sling:Folder) /apps/a/b"
        );

        RepoinitUtil.removeConflicts(extension);

        assertFalse(extension.getText().contains("create path (sling:Folder) /apps/a/b\n"));
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
}