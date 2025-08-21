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
package com.adobe.aem.analyser.mojos;

public abstract class Constants extends com.adobe.aem.analyser.Constants {

    /** Name of the env var to check. */
    public static final String SKIP_ENV_VAR = "CM_PROGRAM_ID";

    /** Packaging of the analyse project */
    public static final String PACKAGING_AEM_ANALYSE = "aem-analyse";
    
    /** Packaging zip (for content packages) */
    public static final String PACKAGING_ZIP = "zip";

    /** Packaging for content packages */
    public static final String PACKAGING_CONTENT_PACKAGE = "content-package";

    /** The extension for the content packages without the leading dot */
    public static final String EXTENSION_CONTENT_PACKAGE = "zip";

    /** The directory for the content package converter */
    public static final String CONVERTER_DIRECTORY = "cp-conversion";

    /** The directory for the feature model */
    public static final String FM_DIRECTORY = "fm.out";

    /** The packaging of the application */
    public static final String PACKAGING_AEMAPP = "aemapp";
}
