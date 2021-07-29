/*
  Copyright 2021 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.cli;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;

public class VersionProvider implements IVersionProvider {
    private static final String VERSION_RESOURCE = "/META-INF/maven/com.adobe.aem/aemanalyser-cli/pom.properties";

    @Override
    public String[] getVersion() throws Exception {
        URL res = getClass().getResource(VERSION_RESOURCE);
        if (res != null) {
            Properties p = new Properties();
            try (InputStream is = res.openStream()) {
                p.load(is);
            }

            return new String[] {p.getProperty("version")};
        }
        return new String[] {"Unknown version"};
    }
}
