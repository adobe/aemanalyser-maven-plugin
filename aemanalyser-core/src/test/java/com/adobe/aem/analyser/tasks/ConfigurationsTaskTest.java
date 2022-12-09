/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.tasks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Dictionary;
import java.util.List;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.builder.FeatureProvider;
import org.junit.Test;

import com.adobe.aem.analyser.result.AemAnalyserResult;
import com.adobe.aem.project.model.ConfigurationFile;
import com.adobe.aem.project.model.ConfigurationFileType;
import com.adobe.aem.project.model.ConfigurationFile.Location;

public class ConfigurationsTaskTest {

    @Test public void testDirectoryScanningAndValidating() throws IOException {
        final FeatureProvider provider = new FeatureProvider() {

            @Override
            public Feature provide(ArtifactId id) {
                return new Feature(id);
            }
            
        };
        final File baseDir = new File(this.getClass().getResource("/validation").getFile());
        System.out.println(baseDir);
        final TaskContext context = new TaskContext(baseDir.getParentFile(), 
            new ArtifactId("g", "p", "1.0", null, null),
            new ArtifactId("g", "a", "1.0", null, null),
            Collections.emptyList(), 
            provider);
        final ConfigurationsTask task = new ConfigurationsTask(context, new ConfigurationsTaskConfig());
        final List<ConfigurationFile> files = task.scanRepositoryDirectory(baseDir);
        assertEquals(4, files.size());
        int count = 0;
        for(final ConfigurationFile f : files) {
            assertEquals("com.adobe.aem.Component", f.getPid());
            if ( f.getType() == ConfigurationFileType.XML ) {
                count++;
                assertEquals("libs/myapp/install.dev/com.adobe.aem.Component.xml".replace('/', File.separatorChar), 
                   f.getSource().getAbsolutePath().substring(baseDir.getAbsolutePath().length() + 1));
                assertEquals(Location.LIBS, f.getLocation());
                assertEquals(1, f.getLevel());
                assertEquals("dev", f.getRunMode());
                final Dictionary<String, Object> properties = f.readConfiguration();
                assertEquals("xml", properties.get("value"));
            }
            if ( f.getType() == ConfigurationFileType.PROPERTIES ) {
                count++;
                assertEquals("apps/myapp/config.dev/subfolder/com.adobe.aem.Component.properties".replace('/', File.separatorChar), 
                    f.getSource().getAbsolutePath().substring(baseDir.getAbsolutePath().length() + 1));
                assertEquals(Location.APPS, f.getLocation());
                assertEquals(2, f.getLevel());
                assertEquals("dev", f.getRunMode());
                final Dictionary<String, Object> properties = f.readConfiguration();
                assertEquals("true", properties.get("value"));
            }
            if ( f.getType() == ConfigurationFileType.CONFIGADMIN ) {
                count++;
                assertEquals("apps/myapp/config/com.adobe.aem.Component.config".replace('/', File.separatorChar), 
                    f.getSource().getAbsolutePath().substring(baseDir.getAbsolutePath().length() + 1));
                assertEquals(Location.APPS, f.getLocation());
                assertEquals(1, f.getLevel());
                assertNull(f.getRunMode());
                final Dictionary<String, Object> properties = f.readConfiguration();
                assertEquals("true", properties.get("value"));
            }
            if ( f.getType() == ConfigurationFileType.JSON ) {
                count++;
                assertEquals("apps/myapp/config.author/com.adobe.aem.Component.cfg.json".replace('/', File.separatorChar), 
                    f.getSource().getAbsolutePath().substring(baseDir.getAbsolutePath().length() + 1));
                assertEquals(Location.APPS, f.getLocation());
                assertEquals(1, f.getLevel());
                assertEquals("author", f.getRunMode());
                final Dictionary<String, Object> properties = f.readConfiguration();
                assertEquals(true, properties.get("value"));
            }
        }
        assertEquals(4, count);

        final AemAnalyserResult result = task.analyseConfigurations(files);
        assertEquals(2, result.getErrors().size());
    }
    
}
