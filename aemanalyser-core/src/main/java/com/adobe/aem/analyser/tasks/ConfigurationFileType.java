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

/**
 * The source type of an OSGi configuration file
 */
public enum ConfigurationFileType {
    JSON,
    XML,
    PROPERTIES,
    CONFIGADMIN;

    /**
     * Detect the type from the file name (ending)
     * @param name The file name
     * @return The detected type or {@code null}
     */
    public static ConfigurationFileType fromFileName(final String name) {
        if ( name.endsWith(".cfg.json") ) {
            return JSON;
        } else if ( name.endsWith(".xml") ) {
            return XML;
        } else if ( name.endsWith(".properties") || name.endsWith(".cfg") ) {
            return PROPERTIES;
        } else if ( name.endsWith(".config") ) {
            return CONFIGADMIN;
        }
        return null;
    }
}