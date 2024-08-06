/*
  Copyright 2024 Cognizant Netcentric. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/
package com.adobe.aem.analyser.mojos.extensions;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.artifact.handler.DefaultArtifactHandler;

import com.adobe.aem.analyser.mojos.Constants;

@Singleton
@Named(Constants.PACKAGING_AEMAPP)
public class AemAppArtifactHandler extends DefaultArtifactHandler {

    public AemAppArtifactHandler() {
        super(Constants.PACKAGING_AEMAPP);
        setIncludesDependencies(false);
        setExtension("zip");
        setLanguage("java");
        setAddedToClasspath(false);
    }
}
