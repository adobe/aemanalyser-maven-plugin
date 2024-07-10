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
package com.adobe.aem.analyser;

import org.apache.sling.feature.Feature;
import org.apache.sling.feature.io.json.FeatureJSONReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

class FeatureUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureUtil.class);

    static final String FEATURE_FILE_NAME = "unsupported_libraries_java21.json";
    static final String FEATURE_FILE_PATH = "/META-INF/" + FEATURE_FILE_NAME;

    static Feature getFeatureFromResources() {
        try (InputStream inputStream = AemAggregator.class.getResourceAsStream(FEATURE_FILE_PATH);
             Reader reader = new InputStreamReader(inputStream)) {

            return FeatureJSONReader.read(reader, FEATURE_FILE_NAME);

        } catch (Exception e) {
            LOGGER.error("Exception during reading features from resources", e);
            return null;
        }
    }
}
