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
package com.adobe.aem.project.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.builder.FeatureProvider;
import org.apache.sling.feature.io.artifacts.ArtifactManager;
import org.apache.sling.feature.io.artifacts.ArtifactManagerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.aem.analyser.Constants;
import com.adobe.aem.analyser.tasks.TaskResult;

public abstract class AbstractCommand {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected CliParser parser;

    private static final Map<ArtifactId, ArtifactId> VERSION_CACHE = new HashMap<>();

    public void setParser(final CliParser parser) {
        this.parser = parser;
    }

    protected boolean isDryRun() {
        return this.parser.options.contains("dryRun");
    }

    protected boolean isStrict() {
        return this.parser.options.contains("strict");
    }

    public abstract void validate();

    public abstract TaskResult doExecute() throws IOException;

    public File getBaseDirectory() {
        return new File(new File("dot").getAbsolutePath()).getParentFile();
    }

    public ArtifactId getSdkId() throws IOException {
        return getLatestVersion(new ArtifactId(Constants.SDK_GROUP_ID, Constants.SDK_ARTIFACT_ID, "1", null, null));
    }

    public List<ArtifactId> getAddons() throws IOException {
/*
        final List<ArtifactId> addons = new ArrayList<>();
        for(final ArtifactId id : Constants.DEFAULT_ADDONS) {
            addons.add(this.getLatestVersion(id));
        }
        return addons;
*/
        return Collections.emptyList();
    }

    public ArtifactId getProjectId() {
        return new ArtifactId("group", "artifact", "1.0", null, "pom");
    }

    public FeatureProvider getFeatureProvider() throws IOException {
        final ArtifactManagerConfig config = new ArtifactManagerConfig();
        final ArtifactManager artifactManager = ArtifactManager.getArtifactManager(config);
        return artifactManager.toFeatureProvider();
    }

    protected ArtifactId getLatestVersion(final ArtifactId id) throws IOException {
        ArtifactId result = VERSION_CACHE.get(id);
        if ( result == null ) {
            final String metadataUrl = "https://repo.maven.apache.org/maven2/"
                .concat(id.getGroupId().replace('.', '/'))
                .concat("/")
                .concat(id.getArtifactId())
                .concat("/maven-metadata.xml");
            final String contents = getFileContents(metadataUrl);
            final String version = ArtifactManager.getValue(contents, new String[] {"metadata", "versioning", "latest"});
            result = id.changeVersion(version);
            VERSION_CACHE.put(id, result);
        }
        return result;
    }

    protected String getFileContents(final String url) throws IOException {
        final StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(url).openStream(), "UTF-8"))) {
            for(String line = reader.readLine(); line != null; line = reader.readLine()) {
                sb.append(line).append('\n');
            }
        }

        return sb.toString();
    }
}
